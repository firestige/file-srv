package tech.icc.filesrv.common.exception.validation;

import lombok.Getter;
import tech.icc.filesrv.common.constants.ResultCode;

/**
 * 无效任务ID异常
 * <p>
 * 当任务ID格式不正确时抛出（如：不是有效格式、包含非法字符等）
 */
@Getter
public class InvalidTaskIdException extends ValidationException {
    
    /** 异常消息中显示的任务ID最大长度 */
    private static final int MAX_DISPLAY_LENGTH = 64;
    
    public InvalidTaskIdException(String taskId) {
        super(taskId, ResultCode.INVALID_PARAMETER, formatMessage(taskId));
    }
    
    @Override
    public String getSource() {
        return (String) super.source;
    }
    
    /**
     * 格式化异常消息，对超长任务ID进行截断
     * <p>
     * 示例输出：
     * - 短字符串：无效的任务ID格式: 'abc123'
     * - 长字符串：无效的任务ID格式: 'abcdefghij...'(实际长度: 200)
     */
    private static String formatMessage(String taskId) {
        if (taskId == null) {
            return "任务ID不能为空";
        }
        
        String displayId = taskId.length() <= MAX_DISPLAY_LENGTH
            ? taskId
            : taskId.substring(0, MAX_DISPLAY_LENGTH) + "...(实际长度: " + taskId.length() + ")";
        
        return String.format("无效的任务ID格式: '%s'", displayId);
    }
}
