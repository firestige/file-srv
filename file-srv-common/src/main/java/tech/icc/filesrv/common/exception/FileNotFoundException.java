package tech.icc.filesrv.common.exception;

import tech.icc.filesrv.common.constants.ResultCode;

public class FileNotFoundException extends FileServiceException {
    public static FileNotFoundException withoutStack(String message) {
        return new FileNotFoundException(message, null, false, true);
    }

    public static FileNotFoundException withStack(String message, Throwable cause) {
        return new FileNotFoundException(message, cause, false, false);
    }

    private FileNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(ResultCode.FILE_NOT_FOUND, message, cause, enableSuppression, writableStackTrace);
    }
}
