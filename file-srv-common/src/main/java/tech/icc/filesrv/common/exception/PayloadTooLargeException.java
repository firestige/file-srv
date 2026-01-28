package tech.icc.filesrv.common.exception;

import tech.icc.filesrv.common.constants.ResultCode;

/**
 * 请求体过大异常
 * <p>
 * 当上传文件超过允许的最大大小时抛出。
 */
public class PayloadTooLargeException extends FileServiceException {

    public static PayloadTooLargeException withoutStack(String message) {
        return new PayloadTooLargeException(message, null, false, true);
    }

    public static PayloadTooLargeException withStack(String message, Throwable cause) {
        return new PayloadTooLargeException(message, cause, false, false);
    }

    private PayloadTooLargeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(ResultCode.PAYLOAD_TOO_LARGE, message, cause, enableSuppression, writableStackTrace);
    }
}
