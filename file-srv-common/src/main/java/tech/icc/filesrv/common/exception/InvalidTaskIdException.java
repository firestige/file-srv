package tech.icc.filesrv.common.exception;

/**
 * 无效任务 ID 异常
 */
public class InvalidTaskIdException extends RuntimeException {

    private final String taskId;

    public InvalidTaskIdException(String taskId) {
        super("Invalid task ID format: " + taskId);
        this.taskId = taskId;
    }

    public InvalidTaskIdException(String taskId, String message) {
        super(message);
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }
}
