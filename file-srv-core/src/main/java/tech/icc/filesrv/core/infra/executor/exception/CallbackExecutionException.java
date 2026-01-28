package tech.icc.filesrv.core.infra.executor.exception;

/**
 * Callback 执行异常
 * <p>
 * 当 callback 执行过程中发生非超时异常时抛出。
 * 可通过 {@link #isRetryable()} 判断是否可重试。
 */
public class CallbackExecutionException extends RuntimeException {

    private final String taskId;
    private final String callbackName;
    private final int callbackIndex;
    private final boolean retryable;

    public CallbackExecutionException(String taskId, String callbackName,
                                      int callbackIndex, String message, boolean retryable) {
        super(message);
        this.taskId = taskId;
        this.callbackName = callbackName;
        this.callbackIndex = callbackIndex;
        this.retryable = retryable;
    }

    public CallbackExecutionException(String taskId, String callbackName,
                                      int callbackIndex, String message, boolean retryable,
                                      Throwable cause) {
        super(message, cause);
        this.taskId = taskId;
        this.callbackName = callbackName;
        this.callbackIndex = callbackIndex;
        this.retryable = retryable;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getCallbackName() {
        return callbackName;
    }

    public int getCallbackIndex() {
        return callbackIndex;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
