package tech.icc.filesrv.common.exception.validation;

import tech.icc.filesrv.common.exception.FileServiceException;
import tech.icc.filesrv.common.exception.WithoutStack;

/**
 * 验证异常,通常由输入参数验证失败引发
 * 这类异常都是可预期的，所以都是无栈异常
 */
public abstract class ValidationException extends FileServiceException implements WithoutStack {
    protected Object source;
    protected ValidationException(Object source, int code, String message) {
        super(code, message, null);
        this.source = source;
    }

    abstract Object getSource();

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
