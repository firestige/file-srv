package tech.icc.filesrv.common.context;

import tech.icc.filesrv.common.constants.ReasonPhase;
import tech.icc.filesrv.common.constants.ResultCode;
import tech.icc.filesrv.common.exception.FileServiceException;

public record Result<E>(int code, String message, E data) {
    public static Result<Void> success() {
        return success(null);
    }

    public static <E> Result<E> success(E data) {
        return new Result<>(ResultCode.SUCCESS, ReasonPhase.SUCCESS, data);
    }

    public static Result<Void> failure(FileServiceException e) {
        return Result.failure(e.getCode(), e.getMessage());
    }

    public static Result<Void> failure(int code, String message) {
        return new Result<>(code, message, null);
    }
}
