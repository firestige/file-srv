package tech.icc.filesrv.common.exception;

import lombok.Getter;

/**
 * 文件服务通用异常基类
 */
@Getter
public class FileServiceException extends RuntimeException {
    private final int code;

    public FileServiceException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
