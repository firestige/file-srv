package tech.icc.filesrv.common.spi.executor;

/**
 * Callback task publisher - publishes callback tasks for asynchronous execution.
 * <p>
 * This interface abstracts the message publishing mechanism, supporting multiple
 * implementations based on the deployment environment.
 * </p>
 */
public interface CallbackTaskPublisher {

    /**
     * Publish a callback task for asynchronous execution.
     *
     * @param taskId the unique task identifier
     */
    void publish(String taskId);
}