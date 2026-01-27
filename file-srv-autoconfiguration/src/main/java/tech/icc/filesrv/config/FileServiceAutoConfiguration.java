package tech.icc.filesrv.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import tech.icc.filesrv.core.infra.event.TaskEventPublisher;
import tech.icc.filesrv.core.infra.event.impl.LoggingTaskEventPublisher;
import tech.icc.filesrv.core.infra.file.LocalFileManager;
import tech.icc.filesrv.core.infra.file.impl.DefaultLocalFileManager;
import tech.icc.filesrv.core.infra.storage.StorageAdapter;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件服务自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties(FileServiceProperties.class)
@ComponentScan(basePackages = {
        "tech.icc.filesrv.core.application",
        "tech.icc.filesrv.core.domain",
        "tech.icc.filesrv.core.infra.plugin.impl"
})
public class FileServiceAutoConfiguration {

    /**
     * 默认事件发布器（日志实现）
     * <p>
     * 当 Kafka 不可用时使用此实现。
     * 可通过提供自定义 TaskEventPublisher Bean 覆盖。
     */
    @Bean
    @ConditionalOnMissingBean(TaskEventPublisher.class)
    public TaskEventPublisher loggingTaskEventPublisher() {
        return new LoggingTaskEventPublisher();
    }

    /**
     * 本地文件管理器
     */
    @Bean
    @ConditionalOnMissingBean(LocalFileManager.class)
    public LocalFileManager defaultLocalFileManager(
            FileServiceProperties properties,
            StorageAdapter storageAdapter) {
        Path tempDir = Paths.get(properties.getTask().getTempDir());
        return new DefaultLocalFileManager(tempDir, storageAdapter);
    }
}

