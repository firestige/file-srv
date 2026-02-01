package tech.icc.filesrv.core.infra.executor;

/**
 * Callback task publisher - publishes callback tasks for asynchronous execution.
 * <p>
 * This interface abstracts the message publishing mechanism, supporting multiple
 * implementations based on the deployment environment:
 * <ul>
 *   <li><strong>Production</strong>: {@code KafkaCallbackTaskPublisher} - publishes to Apache Kafka</li>
 *   <li><strong>Test</strong>: {@code SpringEventCallbackPublisher} - publishes via Spring's {@code ApplicationEventPublisher}</li>
 *   <li><strong>Alternative MQ</strong>: Can be implemented for RabbitMQ, RocketMQ, etc.</li>
 * </ul>
 * </p>
 * 
 * <p><strong>Design Principles:</strong></p>
 * <ul>
 *   <li><strong>Task-level scheduling</strong>: One task publishes one message</li>
 *   <li><strong>Checkpoint recovery</strong>: Consumer retrieves {@code currentCallbackIndex} from DB to resume execution</li>
 *   <li><strong>Environment isolation</strong>: Use {@code @Profile} to activate the appropriate implementation</li>
 * </ul>
 */
public interface CallbackTaskPublisher {

    /**
     * Publish a callback task for asynchronous execution.
     *
     * @param taskId the unique task identifier
     */
    void publish(String taskId);
}
