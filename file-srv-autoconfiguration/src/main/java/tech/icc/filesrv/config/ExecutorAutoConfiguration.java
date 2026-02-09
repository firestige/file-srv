package tech.icc.filesrv.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tech.icc.filesrv.common.spi.plugin.PluginStorageService;
import tech.icc.filesrv.core.application.service.FileService;
import tech.icc.filesrv.core.domain.files.FileReferenceRepository;
import tech.icc.filesrv.core.domain.tasks.TaskRepository;
import tech.icc.filesrv.common.config.ExecutorProperties;
import tech.icc.filesrv.common.spi.event.TaskEventPublisher;
import tech.icc.filesrv.common.spi.executor.CallbackTaskMessageHandler;
import tech.icc.filesrv.common.spi.executor.DeadLetterPublisher;
import tech.icc.filesrv.common.spi.executor.IdempotencyChecker;
import tech.icc.filesrv.core.infra.executor.CallbackChainRunner;
import tech.icc.filesrv.core.infra.executor.impl.DefaultCallbackChainRunner;
import tech.icc.filesrv.core.infra.executor.impl.DefaultCallbackTaskMessageHandler;
import tech.icc.filesrv.core.infra.file.LocalFileManager;
import tech.icc.filesrv.core.infra.plugin.PluginRegistry;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Callback 执行器自动配置
 * <p>
 * 当配置 {@code file-service.executor.enabled=true} 时启用。
 * Kafka/Redis 相关实现由 spi-xx 模块提供。
 */
@AutoConfiguration(after = FileServiceAutoConfiguration.class)
@EnableConfigurationProperties(ExecutorProperties.class)
@ConditionalOnProperty(prefix = "file-service.executor", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ExecutorAutoConfiguration {

    /**
     * 超时执行器线程池
     */
    @Bean(name = "callbackTimeoutExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "callbackTimeoutExecutor")
    public ExecutorService callbackTimeoutExecutor(ExecutorProperties properties) {
        int concurrency = properties.messageQueue().concurrency();
        return Executors.newFixedThreadPool(concurrency * 2,
                r -> {
                    Thread t = new Thread(r, "callback-executor");
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * Callback 链执行器
     */
    @Bean
    @ConditionalOnMissingBean(CallbackChainRunner.class)
    public CallbackChainRunner defaultCallbackChainRunner(
            TaskRepository taskRepository,
            PluginRegistry pluginRegistry,
            LocalFileManager localFileManager,
            TaskEventPublisher eventPublisher,
            ExecutorService callbackTimeoutExecutor,
            ExecutorProperties properties,
            PluginStorageService pluginStorageService,
            FileService fileService,
            FileReferenceRepository fileReferenceRepository) {
        return new DefaultCallbackChainRunner(
                taskRepository,
                pluginRegistry,
                localFileManager,
                eventPublisher,
                callbackTimeoutExecutor,
                properties,
                pluginStorageService,
                fileService,
                fileReferenceRepository
        );
    }

    /**
     * Callback 任务消息处理器
     */
    @Bean
    @ConditionalOnMissingBean(CallbackTaskMessageHandler.class)
    @ConditionalOnBean({IdempotencyChecker.class, DeadLetterPublisher.class})
    public CallbackTaskMessageHandler callbackTaskMessageHandler(
            TaskRepository taskRepository,
            CallbackChainRunner chainRunner,
            IdempotencyChecker idempotencyChecker,
            DeadLetterPublisher dltPublisher,
            ExecutorProperties properties) {
        return new DefaultCallbackTaskMessageHandler(
                taskRepository,
                chainRunner,
                idempotencyChecker,
                dltPublisher,
                properties
        );
    }

}
