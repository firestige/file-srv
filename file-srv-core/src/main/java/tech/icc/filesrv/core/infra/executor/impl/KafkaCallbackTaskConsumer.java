package tech.icc.filesrv.core.infra.executor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.domain.tasks.TaskRepository;
import tech.icc.filesrv.common.vo.task.TaskStatus;
import tech.icc.filesrv.core.infra.executor.CallbackChainRunner;
import tech.icc.filesrv.core.infra.executor.DeadLetterPublisher;
import tech.icc.filesrv.core.infra.executor.ExecutorProperties;
import tech.icc.filesrv.core.infra.executor.IdempotencyChecker;
import tech.icc.filesrv.core.infra.executor.exception.CallbackExecutionException;
import tech.icc.filesrv.core.infra.executor.exception.CallbackTimeoutException;
import tech.icc.filesrv.core.infra.executor.message.CallbackTaskMessage;
import tech.icc.filesrv.core.infra.executor.message.DeadLetterMessage;

import java.util.Optional;

/**
 * Kafka callback task consumer.
 * <p>
 * This consumer is active in production environments only.
 * Test environments use {@link CallbackTaskEventListener} instead.
 * </p>
 */
@Profile("!test")
public class KafkaCallbackTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaCallbackTaskConsumer.class);

    private final TaskRepository taskRepository;
    private final CallbackChainRunner chainRunner;
    private final IdempotencyChecker idempotencyChecker;
    private final DeadLetterPublisher dltPublisher;
    private final ExecutorProperties properties;

    @Value("${file-service.node-id:unknown}")
    private String nodeId;

    public KafkaCallbackTaskConsumer(TaskRepository taskRepository,
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

    @KafkaListener(
            topics = "${file-service.executor.kafka.topic:file-callback-tasks}",
            groupId = "${file-service.executor.kafka.consumer-group:file-callback-executor}",
            concurrency = "${file-service.executor.kafka.concurrency:4}"
    )
    public void consume(CallbackTaskMessage msg, Acknowledgment ack) {
        String taskId = msg.taskId();
        String messageId = msg.messageId();

        log.info("Received callback task: taskId={}, messageId={}", taskId, messageId);

        // 1. 幂等检查
        if (idempotencyChecker.isDuplicate(messageId)) {
            log.debug("Duplicate message, skipping: messageId={}", messageId);
            ack.acknowledge();
            return;
        }

        // 2. 过期检查
        if (msg.isExpired()) {
            log.warn("Message expired: taskId={}, deadline={}", taskId, msg.deadline());
            handleExpired(msg);
            ack.acknowledge();
            return;
        }

        // 3. 加载任务
        Optional<TaskAggregate> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            log.warn("Task not found: taskId={}", taskId);
            ack.acknowledge();
            return;
        }

        TaskAggregate task = taskOpt.get();
        if (task.getStatus() != TaskStatus.PROCESSING) {
            log.warn("Task not in PROCESSING status: taskId={}, status={}",
                    taskId, task.getStatus());
            ack.acknowledge();
            return;
        }

        try {
            // 4. 执行整个 callback 链（从 currentCallbackIndex 开始）
            //    所有 callback 在当前节点完成，本地重试
            chainRunner.run(task);

            // 5. 标记幂等
            idempotencyChecker.markProcessed(messageId, properties.idempotency().ttl());

            // 6. 确认消息
            ack.acknowledge();

            log.info("Callback task completed: taskId={}", taskId);

        } catch (CallbackTimeoutException e) {
            // 重试耗尽，标记失败，发送 DLT
            handleFinalTimeout(e, msg, task, ack);

        } catch (CallbackExecutionException e) {
            // 不可重试异常，标记失败，发送 DLT
            handleFinalError(e, msg, task, ack);

        } catch (Exception e) {
            // 未预期异常：不 ack，让 Kafka 重投递
            // 注意：重投递可能到其他节点，需要重新下载文件
            log.error("Unexpected error: taskId={}", taskId, e);
            throw e;
        }
    }

    /**
     * 处理过期消息
     */
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

        // 发送死信
        DeadLetterMessage dlt = DeadLetterMessage.from(msg, "Message expired", nodeId);
        dltPublisher.publish(dlt);
    }

    /**
     * 处理超时异常（本地重试已耗尽）
     */
    private void handleFinalTimeout(CallbackTimeoutException e, CallbackTaskMessage msg,
                                     TaskAggregate task, Acknowledgment ack) {
        log.error("Task failed due to timeout: taskId={}, callback={}",
                msg.taskId(), e.getCallbackName());

        // 任务状态应该已在 ChainRunner 中更新，这里只发送死信
        DeadLetterMessage dlt = DeadLetterMessage.from(msg,
                "Timeout: " + e.getCallbackName(), nodeId);
        dltPublisher.publish(dlt);

        ack.acknowledge();
    }

    /**
     * 处理执行异常（不可重试或重试已耗尽）
     */
    private void handleFinalError(CallbackExecutionException e, CallbackTaskMessage msg,
                                   TaskAggregate task, Acknowledgment ack) {
        log.error("Task failed due to execution error: taskId={}, callback={}, error={}",
                msg.taskId(), e.getCallbackName(), e.getMessage());

        // 任务状态应该已在 ChainRunner 中更新，这里只发送死信
        DeadLetterMessage dlt = DeadLetterMessage.from(msg, e.getMessage(), nodeId);
        dltPublisher.publish(dlt);

        ack.acknowledge();
    }
}
