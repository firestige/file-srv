package tech.icc.filesrv.common.exception.validation;

import tech.icc.filesrv.common.exception.FileServiceException;

/**
 * 验证异常,通常由输入参数验证失败引发
 * 这类异常都是可预期的，所以都是无栈异常
 */
abstract class ValidationException extends FileServiceException {
    protected Object source;
    protected ValidationException(Object source, int code, String message) {
        super(code, message, null, false, true);
        this.source = source;
    }

    abstract Object getSource();

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
