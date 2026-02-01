package tech.icc.filesrv.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务自动配置
 * <p>
 * 启用 Spring 定时任务和重试机制支持，用于孤儿文件清理等定时任务。
 * 通过 file-srv.orphan.enabled 配置控制是否启用。
 * </p>
 */
@AutoConfiguration
@EnableScheduling
@EnableRetry
@ConditionalOnProperty(prefix = "file-srv.orphan", name = "enabled", havingValue = "true")
@ComponentScan(basePackages = {
        "tech.icc.filesrv.core.infra.task",
        "tech.icc.filesrv.core.infra.config"
})
public class SchedulingAutoConfiguration {
    // Spring will automatically detect @Scheduled methods in scanned components
}
