package tech.icc.filesrv.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import tech.icc.filesrv.core.infra.cache.TaskCacheService;
import tech.icc.filesrv.core.infra.cache.impl.CaffeineTaskCacheService;
import tech.icc.filesrv.common.spi.event.TaskEventPublisher;
import tech.icc.filesrv.core.infra.event.impl.LoggingTaskEventPublisher;
import tech.icc.filesrv.common.spi.executor.CallbackTaskPublisher;
import tech.icc.filesrv.core.infra.executor.impl.NoOpCallbackTaskPublisher;
import tech.icc.filesrv.core.infra.file.LocalFileManager;
import tech.icc.filesrv.core.infra.file.impl.DefaultLocalFileManager;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;

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
     * 默认 Callback 任务发布器（NoOp 实现）
     * <p>
     * 当 Kafka 不可用时使用此实现，仅记录日志。
     * 启用 Kafka 执行器后会被 ExecutorAutoConfiguration 中的实现覆盖。
     */
    @Bean
    @ConditionalOnMissingBean(CallbackTaskPublisher.class)
    public CallbackTaskPublisher noOpCallbackTaskPublisher() {
        return new NoOpCallbackTaskPublisher();
    }

    /**
     * 文件控制器配置
     * <p>
     * 从 FileServiceProperties 中提取文件控制器相关配置，注入到 FileController。
     * 提供开箱即用的默认值，用户可通过 application.yml 自定义。
     * <p>
     * 配置项：
     * <ul>
     *   <li>file-service.file-controller.max-file-key-length: 文件标识最大长度，默认 128</li>
     *   <li>file-service.file-controller.presign.default-expiry-seconds: 预签名URL默认有效期，默认 3600</li>
     *   <li>file-service.file-controller.presign.min-expiry-seconds: 预签名URL最小有效期，默认 60</li>
     *   <li>file-service.file-controller.presign.max-expiry-seconds: 预签名URL最大有效期，默认 604800</li>
     *   <li>file-service.file-controller.pagination.default-size: 分页默认大小，默认 20</li>
     *   <li>file-service.file-controller.pagination.max-size: 分页最大大小，默认 100</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(FileControllerConfig.class)
    public FileControllerConfig fileControllerConfig(FileServiceProperties properties) {
        FileServiceProperties.FileControllerProperties controllerProps = properties.getFileController();
        FileServiceProperties.PresignProperties presignProps = controllerProps.getPresign();
        FileServiceProperties.PaginationProperties paginationProps = controllerProps.getPagination();
        
        return new FileControllerConfig(
                controllerProps.getMaxFileKeyLength(),
                presignProps.getDefaultExpirySeconds(),
                presignProps.getMinExpirySeconds(),
                presignProps.getMaxExpirySeconds(),
                paginationProps.getDefaultSize(),
                paginationProps.getMaxSize()
        );
    }
}
