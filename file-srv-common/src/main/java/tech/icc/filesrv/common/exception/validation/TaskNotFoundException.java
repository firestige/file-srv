package tech.icc.filesrv.common.exception.validation;

import lombok.Getter;
import tech.icc.filesrv.common.constants.ResultCode;

/**
 * 任务未找到异常
 * <p>
 * 当指定的任务ID不存在时抛出
 */
@Getter
public class TaskNotFoundException extends ValidationException {
    
    /** 异常消息中显示的任务ID最大长度 */
    private static final int MAX_DISPLAY_LENGTH = 64;
    
    public TaskNotFoundException(String taskId) {
        super(taskId, ResultCode.TASK_NOT_FOUND, formatMessage(taskId));
    }
    
    @Override
    public String getSource() {
        return (String) super.source;
    }
    
    /**
     * 格式化异常消息，对超长任务ID进行截断
     */
    private static String formatMessage(String taskId) {
        if (taskId == null) {
            return "任务不存在: null";
        }
        
        String displayId = taskId.length() <= MAX_DISPLAY_LENGTH
            ? taskId
            : taskId.substring(0, MAX_DISPLAY_LENGTH) + "...(实际长度: " + taskId.length() + ")";
        
        return String.format("任务不存在: '%s'", displayId);
    }
}
