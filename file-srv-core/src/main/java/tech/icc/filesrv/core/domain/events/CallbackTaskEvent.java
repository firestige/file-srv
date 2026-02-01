package tech.icc.filesrv.core.domain.events;

import java.time.Instant;

/**
 * Callback task event - triggers asynchronous callback chain execution.
 * <p>
 * This event is published when an upload task completes and is ready for
 * callback processing. Different implementations handle this event based
 * on the environment profile:
 * <ul>
 *   <li><strong>Production</strong>: Published to Kafka via {@code KafkaCallbackTaskPublisher}</li>
 *   <li><strong>Test</strong>: Published via Spring's {@code ApplicationEventPublisher}</li>
 * </ul>
 * </p>
 *
 * @param taskId     the unique task identifier
 * @param messageId  the unique message identifier for idempotency and tracking
 * @param deadline   the task execution deadline (for timeout control)
 */
public record CallbackTaskEvent(
        String taskId,
        String messageId,
        Instant deadline
) {

    /**
     * Create a callback task event with the given task ID.
     * <p>
     * The message ID is automatically generated, and the deadline is set
     * to 1 hour from the current time.
     * </p>
     *
     * @param taskId the task ID to execute callbacks for
     * @return a new CallbackTaskEvent instance
     */
    public static CallbackTaskEvent of(String taskId) {
        String messageId = taskId + "-" + System.currentTimeMillis();
        Instant deadline = Instant.now().plusSeconds(3600); // 1 hour default
        return new CallbackTaskEvent(taskId, messageId, deadline);
    }

    /**
     * Create a callback task event with custom deadline.
     *
     * @param taskId   the task ID to execute callbacks for
     * @param deadline the task execution deadline
     * @return a new CallbackTaskEvent instance
     */
    public static CallbackTaskEvent of(String taskId, Instant deadline) {
        String messageId = taskId + "-" + System.currentTimeMillis();
        return new CallbackTaskEvent(taskId, messageId, deadline);
    }

    /**
     * Check if this task has exceeded its deadline.
     *
     * @return true if the current time is after the deadline
     */
    public boolean isExpired() {
        return Instant.now().isAfter(deadline);
    }

    /**
     * Get the remaining time until deadline in seconds.
     *
     * @return remaining seconds, or 0 if expired
     */
    public long getRemainingSeconds() {
        long remaining = deadline.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
}
