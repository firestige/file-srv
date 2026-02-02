package tech.icc.filesrv.core.infra.executor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.icc.filesrv.common.vo.task.TaskStatus;
import tech.icc.filesrv.core.domain.events.CallbackTaskEvent;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.domain.tasks.TaskRepository;
import tech.icc.filesrv.core.infra.executor.CallbackChainRunner;

import java.util.Optional;

/**
 * Spring Event listener for callback task events.
 * <p>
 * This listener is active in test environments only, processing events published by
 * {@link SpringEventCallbackPublisher}. Events are handled asynchronously in a
 * separate thread pool configured in application-test.yml.
 * </p>
 * <p>
 * Production environments use {@link KafkaCallbackTaskConsumer} instead.
 * </p>
 */
@Component
@Profile("test")
public class CallbackTaskEventListener {

    private static final Logger log = LoggerFactory.getLogger(CallbackTaskEventListener.class);

    private final TaskRepository taskRepository;
    private final CallbackChainRunner chainRunner;

    public CallbackTaskEventListener(TaskRepository taskRepository,
                                     CallbackChainRunner chainRunner) {
        this.taskRepository = taskRepository;
        this.chainRunner = chainRunner;
    }

    /**
     * Handle callback task events asynchronously after transaction commit.
     * <p>
     * This method is invoked AFTER the transaction that published the event commits.
     * This ensures the task status (PROCESSING) is visible in the database.
     * </p>
     *
     * @param event the callback task event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onCallbackTask(CallbackTaskEvent event) {
        String taskId = event.taskId();
        String messageId = event.messageId();

        log.info("Received callback task event: taskId={}, messageId={}", taskId, messageId);

        // Check if the task has expired
        if (event.isExpired()) {
            log.warn("Task expired: taskId={}, deadline={}", taskId, event.deadline());
            return;
        }

        // Load the task from the repository
        Optional<TaskAggregate> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            log.warn("Task not found: taskId={}", taskId);
            return;
        }

        TaskAggregate task = taskOpt.get();
        if (task.getStatus() != TaskStatus.PROCESSING) {
            log.warn("Task not in PROCESSING status: taskId={}, status={}",
                    taskId, task.getStatus());
            return;
        }

        try {
            // Execute the entire callback chain
            chainRunner.run(task);
            log.info("Callback chain completed successfully: taskId={}", taskId);
        } catch (Exception ex) {
            log.error("Failed to execute callback chain: taskId={}", taskId, ex);
            // Note: In test environment, we don't have dead letter queue or retry logic
            // The task status will remain in PROCESSING or be updated by chainRunner
        }
    }
}
