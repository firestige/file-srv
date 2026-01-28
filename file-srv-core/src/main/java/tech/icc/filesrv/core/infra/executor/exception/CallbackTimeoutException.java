package tech.icc.filesrv.core.infra.executor.exception;

/**
 * Callback 执行超时异常
 * <p>
 * 当单个 callback 执行超过配置的超时时间（包括本地重试）时抛出。
 */
public class CallbackTimeoutException extends RuntimeException {

    private final String taskId;
    private final String callbackName;
    private final int callbackIndex;

    public CallbackTimeoutException(String taskId, String callbackName, int callbackIndex) {
        super(String.format("Callback timeout: task=%s, callback=%s, index=%d",
                taskId, callbackName, callbackIndex));
        this.taskId = taskId;
        this.callbackName = callbackName;
        this.callbackIndex = callbackIndex;
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
}
