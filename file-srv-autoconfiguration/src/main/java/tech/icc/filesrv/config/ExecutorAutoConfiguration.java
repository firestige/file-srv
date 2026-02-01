package tech.icc.filesrv.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tech.icc.filesrv.core.domain.tasks.TaskRepository;
import tech.icc.filesrv.core.infra.event.TaskEventPublisher;
import tech.icc.filesrv.core.infra.executor.*;
import tech.icc.filesrv.core.infra.executor.impl.*;
import tech.icc.filesrv.core.infra.executor.message.CallbackTaskMessage;
import tech.icc.filesrv.core.infra.executor.message.DeadLetterMessage;
import tech.icc.filesrv.core.infra.file.LocalFileManager;
import tech.icc.filesrv.core.infra.plugin.PluginRegistry;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Callback 执行器自动配置
 * <p>
 * 当配置 {@code file-service.executor.enabled=true} 时启用。
 * 需要 Kafka 和 Redis 依赖。
 */
@AutoConfiguration(after = FileServiceAutoConfiguration.class)
@EnableConfigurationProperties(ExecutorProperties.class)
@ConditionalOnProperty(prefix = "file-service.executor", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ExecutorAutoConfiguration {

    /**
     * Callback 任务发布器
     */
    @Bean
    @ConditionalOnMissingBean(CallbackTaskPublisher.class)
    @ConditionalOnBean(KafkaTemplate.class)
    public CallbackTaskPublisher kafkaCallbackTaskPublisher(
            KafkaTemplate<String, CallbackTaskMessage> kafkaTemplate,
            ExecutorProperties properties) {
        return new KafkaCallbackTaskPublisher(kafkaTemplate, properties);
    }

    /**
     * 死信发布器
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterPublisher.class)
    @ConditionalOnBean(KafkaTemplate.class)
    public DeadLetterPublisher kafkaDeadLetterPublisher(
            KafkaTemplate<String, DeadLetterMessage> kafkaTemplate,
            ExecutorProperties properties) {
        return new KafkaDeadLetterPublisher(kafkaTemplate, properties);
    }

    /**
     * 幂等检查器
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyChecker.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public IdempotencyChecker redisIdempotencyChecker(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyChecker(redisTemplate);
    }

    /**
     * 超时执行器线程池
     */
    @Bean(name = "callbackTimeoutExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "callbackTimeoutExecutor")
    public ExecutorService callbackTimeoutExecutor(ExecutorProperties properties) {
        int concurrency = properties.kafka().concurrency();
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
            tech.icc.filesrv.core.callback.PluginStorageService pluginStorageService) {
        return new DefaultCallbackChainRunner(
                taskRepository,
                pluginRegistry,
                localFileManager,
                eventPublisher,
                callbackTimeoutExecutor,
                properties,
                pluginStorageService
        );
    }

    /**
     * Kafka Callback 任务消费者
     */
    @Bean
    @ConditionalOnMissingBean(KafkaCallbackTaskConsumer.class)
    @ConditionalOnBean({CallbackChainRunner.class, IdempotencyChecker.class, DeadLetterPublisher.class})
    public KafkaCallbackTaskConsumer kafkaCallbackTaskConsumer(
            TaskRepository taskRepository,
            CallbackChainRunner chainRunner,
            IdempotencyChecker idempotencyChecker,
            DeadLetterPublisher dltPublisher,
            ExecutorProperties properties) {
        return new KafkaCallbackTaskConsumer(
                taskRepository,
                chainRunner,
                idempotencyChecker,
                dltPublisher,
                properties
        );
    }
}
