package tech.icc.filesrv.common.exception.validation;

import lombok.Getter;
import tech.icc.filesrv.common.constants.ResultCode;

/**
 * 访问被拒绝异常
 * <p>
 * 当用户尝试访问无权限的资源时抛出，例如：
 * <ul>
 *   <li>访问非公开文件的静态资源端点</li>
 *   <li>访问他人的私有文件</li>
 * </ul>
 */
@Getter
public class AccessDeniedException extends ValidationException {
    
    /** 异常消息中显示的资源标识最大长度 */
    private static final int MAX_DISPLAY_LENGTH = 64;
    
    public AccessDeniedException(String resourceId) {
        super(resourceId, ResultCode.ACCESS_DENIED, formatMessage(resourceId));
    }
    
    @Override
    public String getSource() {
        return (String) super.source;
    }
    
    /**
     * 格式化异常消息，对超长资源标识进行截断
     */
    private static String formatMessage(String resourceId) {
        if (resourceId == null) {
            return "无权访问该资源";
        }
        
        String displayId = resourceId.length() <= MAX_DISPLAY_LENGTH
            ? resourceId
            : resourceId.substring(0, MAX_DISPLAY_LENGTH) + "...(实际长度: " + resourceId.length() + ")";
        
        return String.format("无权访问资源: '%s'", displayId);
    }
}
