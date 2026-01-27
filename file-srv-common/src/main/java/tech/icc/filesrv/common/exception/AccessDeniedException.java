package tech.icc.filesrv.common.exception;

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
public class AccessDeniedException extends FileServiceException {

    public static AccessDeniedException withoutStack(String message) {
        return new AccessDeniedException(message, null, false, true);
    }

    public static AccessDeniedException withStack(String message, Throwable cause) {
        return new AccessDeniedException(message, cause, false, false);
    }

    private AccessDeniedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(ResultCode.ACCESS_DENIED, message, cause, enableSuppression, writableStackTrace);
    }
}
