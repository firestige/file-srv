package tech.icc.filesrv.core.application.entrypoint.advice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tech.icc.filesrv.common.constants.ResultCode;
import tech.icc.filesrv.common.context.Result;
import tech.icc.filesrv.common.exception.NotFoundException;
import tech.icc.filesrv.common.exception.validation.AccessDeniedException;
import tech.icc.filesrv.common.exception.FileServiceException;
import tech.icc.filesrv.common.exception.validation.ValidationException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一处理控制器层抛出的异常，将其转换为标准的 {@link Result} 响应格式。
 * 主要处理以下几类异常：
 * <ul>
 *   <li>业务异常 - {@link FileServiceException} 及其子类</li>
 *   <li>校验异常 - Bean Validation 相关异常</li>
 *   <li>参数异常 - 类型转换、缺失参数等</li>
 *   <li>系统异常 - 未预期的运行时异常</li>
 * </ul>
 *
 * @see FileServiceException
 * @see Result
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    /**
     * 处理访问被拒绝异常
     * <p>
     * 返回 HTTP 403 状态码，适用于无权限访问资源的场景。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Result.failure(e));
    }

    /**
     * 处理检查异常
     * <p>
     * 返回400状态码，适用于校验失败的场景。
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<Void>> handleValidationException(ValidationException e) {
        log.warn("Validation exception: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(e));
}

    /**
     * 处理检查异常
     * <p>
     * 返回 HTTP 404 状态码，适用于资源不存在的场景。
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFoundException(NotFoundException e) {
        log.warn("not found exception: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Result.failure(e));
    }

    /**
     * 处理通用业务异常
     * <p>
     * 返回 HTTP 400 状态码，包含具体的错误码和消息。
     */
    @ExceptionHandler(FileServiceException.class)
    public ResponseEntity<Result<Void>> handleFileServiceException(FileServiceException e) {
        log.warn("Business exception occurred: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(e));
    }

    // ==================== 校验异常 ====================

    /**
     * 处理 @RequestBody 参数校验异常
     * <p>
     * 当请求体中的对象校验失败时触发，收集所有字段错误信息。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));

        log.warn("Request body validation failed: {}", errorMessage);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ResultCode.BAD_REQUEST, errorMessage));
    }

    /**
     * 处理 @PathVariable 和 @RequestParam 参数校验异常
     * <p>
     * 当路径变量或查询参数校验失败时触发。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(this::formatConstraintViolation)
                .collect(Collectors.joining("; "));

        log.warn("Parameter validation failed: {}", errorMessage);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ResultCode.BAD_REQUEST, errorMessage));
    }

    /**
     * 处理参数类型转换异常
     * <p>
     * 当路径变量或查询参数无法转换为目标类型时触发。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String errorMessage = String.format("Parameter '%s' should be of type %s",
                e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown");

        log.warn("Parameter type mismatch: {}", errorMessage);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ResultCode.BAD_REQUEST, errorMessage));
    }

    // ==================== 上传异常 ====================

    /**
     * 处理文件大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("Upload size exceeded: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.failure(ResultCode.PAYLOAD_TOO_LARGE, "File size exceeds the maximum allowed limit"));
    }

    // ==================== 系统异常 ====================

    /**
     * 处理所有未捕获的异常
     * <p>
     * 作为兜底处理器，返回 HTTP 500 状态码。
     * 为安全考虑，不向客户端暴露具体的错误堆栈。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGenericException(Exception e) {
        log.error("Unexpected error occurred", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(ResultCode.INTERNAL_ERROR, "Internal server error"));
    }

    // ==================== 辅助方法 ====================

    private String formatFieldError(FieldError error) {
        return String.format("%s: %s", error.getField(), error.getDefaultMessage());
    }

    private String formatConstraintViolation(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        // 移除方法名前缀，只保留参数名
        int lastDot = propertyPath.lastIndexOf('.');
        String paramName = lastDot > 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
        return String.format("%s: %s", paramName, violation.getMessage());
    }
}
