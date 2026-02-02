package tech.icc.filesrv.core.infra.executor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import tech.icc.filesrv.common.config.ExecutorProperties;
import tech.icc.filesrv.common.executor.message.CallbackTaskMessage;
import tech.icc.filesrv.common.executor.message.DeadLetterMessage;
import tech.icc.filesrv.common.spi.executor.CallbackTaskMessageHandler;
import tech.icc.filesrv.common.spi.executor.DeadLetterPublisher;
import tech.icc.filesrv.common.spi.executor.IdempotencyChecker;
import tech.icc.filesrv.common.vo.task.TaskStatus;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.domain.tasks.TaskRepository;
import tech.icc.filesrv.core.infra.executor.CallbackChainRunner;
import tech.icc.filesrv.core.infra.executor.exception.CallbackExecutionException;
import tech.icc.filesrv.core.infra.executor.exception.CallbackTimeoutException;

import java.util.Optional;

/**
 * 默认 Callback 任务消息处理器
 */
public class DefaultCallbackTaskMessageHandler implements CallbackTaskMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultCallbackTaskMessageHandler.class);

    private final TaskRepository taskRepository;
    private final CallbackChainRunner chainRunner;
    private final IdempotencyChecker idempotencyChecker;
    private final DeadLetterPublisher dltPublisher;
    private final ExecutorProperties properties;

    @Value("${file-service.node-id:unknown}")
    private String nodeId;

    public DefaultCallbackTaskMessageHandler(TaskRepository taskRepository,
                                             CallbackChainRunner chainRunner,
                                             IdempotencyChecker idempotencyChecker,
                                             DeadLetterPublisher dltPublisher,
                                             ExecutorProperties properties) {
        this.taskRepository = taskRepository;
        this.chainRunner = chainRunner;
        this.idempotencyChecker = idempotencyChecker;
        this.dltPublisher = dltPublisher;
        this.properties = properties;
    }

    @Override
    public HandleResult handle(CallbackTaskMessage msg) {
        String taskId = msg.taskId();
        String messageId = msg.messageId();

        log.info("Handling callback task message: taskId={}, messageId={}", taskId, messageId);

        // 1. 幂等检查
        if (idempotencyChecker.isDuplicate(messageId)) {
            log.debug("Duplicate message, skipping: messageId={}", messageId);
            return HandleResult.ACK;
        }

        // 2. 过期检查
        if (msg.isExpired()) {
            log.warn("Message expired: taskId={}, deadline={}", taskId, msg.deadline());
            handleExpired(msg);
            return HandleResult.ACK;
        }

        // 3. 加载任务
        Optional<TaskAggregate> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            log.warn("Task not found: taskId={}", taskId);
            return HandleResult.ACK;
        }

        TaskAggregate task = taskOpt.get();
        if (task.getStatus() != TaskStatus.PROCESSING) {
            log.warn("Task not in PROCESSING status: taskId={}, status={}",
                    taskId, task.getStatus());
            return HandleResult.ACK;
        }

        try {
            // 4. 执行整个 callback 链（从 currentCallbackIndex 开始）
            chainRunner.run(task);

            // 5. 标记幂等
            idempotencyChecker.markProcessed(messageId, properties.idempotency().ttl());

            log.info("Callback task completed: taskId={}", taskId);
            return HandleResult.ACK;

        } catch (CallbackTimeoutException e) {
            // 重试耗尽，标记失败，发送 DLT
            handleFinalTimeout(e, msg);
            return HandleResult.ACK;

        } catch (CallbackExecutionException e) {
            // 不可重试异常，标记失败，发送 DLT
            handleFinalError(e, msg);
            return HandleResult.ACK;

        } catch (Exception e) {
            // 未预期异常：返回 RETRY 交给消息中间件重投递
            log.error("Unexpected error: taskId={}", taskId, e);
            return HandleResult.RETRY;
        }
    }

    private void handleExpired(CallbackTaskMessage msg) {
        Optional<TaskAggregate> taskOpt = taskRepository.findByTaskId(msg.taskId());
        if (taskOpt.isPresent()) {
            TaskAggregate task = taskOpt.get();
            if (!task.getStatus().isTerminal()) {
                task.markExpired();
                taskRepository.save(task);
                log.info("Task marked as expired: taskId={}", msg.taskId());
            }
        }

        DeadLetterMessage dlt = DeadLetterMessage.from(msg, "Message expired", nodeId);
        dltPublisher.publish(dlt);
    }

    private void handleFinalTimeout(CallbackTimeoutException e, CallbackTaskMessage msg) {
        log.error("Task failed due to timeout: taskId={}, callback={}",
                msg.taskId(), e.getCallbackName());

        DeadLetterMessage dlt = DeadLetterMessage.from(msg,
                "Timeout: " + e.getCallbackName(), nodeId);
        dltPublisher.publish(dlt);
    }

    private void handleFinalError(CallbackExecutionException e, CallbackTaskMessage msg) {
        log.error("Task failed due to execution error: taskId={}, callback={}, error={}",
                msg.taskId(), e.getCallbackName(), e.getMessage());

        DeadLetterMessage dlt = DeadLetterMessage.from(msg, e.getMessage(), nodeId);
        dltPublisher.publish(dlt);
    }
}
