package tech.icc.filesrv.common.exception.validation;

import lombok.Getter;
import tech.icc.filesrv.common.constants.ResultCode;

/**
 * 请求体过大异常
 * <p>
 * 当上传文件超过允许的最大大小时抛出
 */
@Getter
public class PayloadTooLargeException extends ValidationException {
    
    private final long actualSize;
    private final long maxSize;
    
    public PayloadTooLargeException(long actualSize, long maxSize) {
        super(actualSize, ResultCode.PAYLOAD_TOO_LARGE, formatMessage(actualSize, maxSize));
        this.actualSize = actualSize;
        this.maxSize = maxSize;
    }
    
    @Override
    public Long getSource() {
        return (Long) super.source;
    }
    
    /**
     * 格式化异常消息
     * <p>
     * 示例输出：上传文件大小 10.5 MB 超过限制 5 MB
     */
    private static String formatMessage(long actualSize, long maxSize) {
        return String.format("上传文件大小 %s 超过限制 %s",
            formatSize(actualSize), formatSize(maxSize));
    }
    
    /**
     * 格式化文件大小为人类可读格式
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
