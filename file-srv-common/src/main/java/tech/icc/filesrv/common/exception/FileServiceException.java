package tech.icc.filesrv.common.exception;

import lombok.Getter;

@Getter
public class FileServiceException extends RuntimeException {
    private final int code;

    public FileServiceException(int code, String message, Throwable cause) {
      this(code, message, cause, false, false);
    }

    protected FileServiceException(int code, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
      this.code = code;
    }
}
