package tech.icc.filesrv.core.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 孤儿文件清理配置属性
 * <p>
 * 从 application.yml 中读取 file-srv.orphan.* 配置项
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "file-srv.orphan")
public class OrphanCleanupProperties {

    /**
     * 是否启用孤儿文件清理
     */
    private boolean enabled = false;

    /**
     * 孤儿文件宽限期（天数）
     * <p>
     * 超过此时间且关联文件不存在的文件将被标记为孤儿
     * </p>
     */
    private int retentionDays = 7;

    /**
     * 清理任务执行的 cron 表达式
     * <p>
     * 默认每天凌晨3点执行
     * </p>
     */
    private String cleanupCron = "0 0 3 * * ?";

    /**
     * 每批处理的文件数量
     */
    private int batchSize = 100;

    /**
     * 是否仅模拟运行（不实际删除文件）
     */
    private boolean dryRun = false;

    /**
     * 获取宽限期的 Duration 对象
     */
    public Duration getRetentionDuration() {
        return Duration.ofDays(retentionDays);
    }
}
