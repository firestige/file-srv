package tech.icc.filesrv.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import tech.icc.filesrv.core.infra.cache.TaskCacheService;
import tech.icc.filesrv.core.infra.cache.TaskIdValidator;
import tech.icc.filesrv.core.infra.cache.impl.BloomFilterTaskIdValidator;
import tech.icc.filesrv.core.infra.cache.impl.CaffeineTaskCacheService;
import tech.icc.filesrv.core.infra.event.TaskEventPublisher;
import tech.icc.filesrv.core.infra.event.impl.LoggingTaskEventPublisher;
import tech.icc.filesrv.core.infra.file.LocalFileManager;
import tech.icc.filesrv.core.infra.file.impl.DefaultLocalFileManager;
import tech.icc.filesrv.core.infra.storage.StorageAdapter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

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

    /**
     * 任务缓存服务（基于 Caffeine 的本地缓存）
     * <p>
     * 配置项：
     * - file-srv.cache.max-size: 最大缓存条目数，默认 10000
     * - file-srv.cache.expire-seconds: 过期时间（秒），默认 30
     */
    @Bean
    @ConditionalOnMissingBean(TaskCacheService.class)
    public TaskCacheService caffeineTaskCacheService(FileServiceProperties properties) {
        FileServiceProperties.CacheProperties cacheProps = properties.getCache();
        return new CaffeineTaskCacheService(
                cacheProps.getMaxSize(),
                Duration.ofSeconds(cacheProps.getExpireSeconds())
        );
    }

    /**
     * 任务ID校验器（基于布隆过滤器）
     * <p>
     * 配置项：
     * - file-srv.bloom-filter.expected-insertions: 预期插入数量，默认 1000000
     * - file-srv.bloom-filter.fpp: 误判率，默认 0.01
     */
    @Bean
    @ConditionalOnMissingBean(TaskIdValidator.class)
    public TaskIdValidator bloomFilterTaskIdValidator(FileServiceProperties properties) {
        FileServiceProperties.BloomFilterProperties bloomProps = properties.getBloomFilter();
        return new BloomFilterTaskIdValidator(
                bloomProps.getExpectedInsertions(),
                bloomProps.getFpp()
        );
    }
}

