package tech.icc.filesrv.adapter.hcs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OBS 相关配置属性
 */
@Data
@ConfigurationProperties(prefix = "storage.obs")
public class ObsProperties {
    /** 是否启用 OBS 适配器，默认为 false，开启需显式配置 */
    private boolean enabled = false;

    /** OBS 访问地址，如 https://obs.xxx.com */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 密钥 */
    private String secretKey;

    /** 桶名称 */
    private String bucketName;

    /** 预签名 URL 过期时间，默认 1 小时 */
    private Duration presignedUrlExpiration = Duration.ofHours(1);
}
