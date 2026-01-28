package tech.icc.filesrv.common.exception;

import tech.icc.filesrv.common.constants.ResultCode;

/**
 * 文件未就绪异常
 * <p>
 * 当文件引用存在但尚未绑定到物理文件时抛出（PENDING 状态）。
 */
public class FileNotReadyException extends FileServiceException {

    public static FileNotReadyException withoutStack(String message) {
        return new FileNotReadyException(message, null, false, true);
    }

    public static FileNotReadyException withStack(String message, Throwable cause) {
        return new FileNotReadyException(message, cause, false, false);
    }

    private FileNotReadyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(ResultCode.FILE_NOT_READY, message, cause, enableSuppression, writableStackTrace);
    }
}
