package tech.icc.filesrv.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件控制器配置
 * <p>
 * 封装文件控制器的所有可配置参数，用于解耦配置访问。
 */
@Getter
@AllArgsConstructor
public class FileControllerConfig {

    /** 文件标识最大长度 */
    private final int maxFileKeyLength;

    /** 预签名URL默认有效期（秒） */
    private final long defaultPresignExpirySeconds;

    /** 预签名URL最小有效期（秒） */
    private final long minPresignExpirySeconds;

    /** 预签名URL最大有效期（秒） */
    private final long maxPresignExpirySeconds;

    /** 分页默认大小 */
    private final int defaultPageSize;

    /** 分页最大大小 */
    private final int maxPageSize;
}
