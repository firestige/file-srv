package tech.icc.filesrv.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import tech.icc.filesrv.common.executor.message.CallbackTaskMessage;
import tech.icc.filesrv.common.executor.message.DeadLetterMessage;
import tech.icc.filesrv.common.config.ExecutorProperties;
import tech.icc.filesrv.common.spi.executor.CallbackTaskMessageHandler;
import tech.icc.filesrv.common.spi.executor.CallbackTaskPublisher;
import tech.icc.filesrv.common.spi.executor.DeadLetterPublisher;
import tech.icc.filesrv.spi.kafka.executor.KafkaCallbackTaskConsumer;
import tech.icc.filesrv.spi.kafka.executor.KafkaCallbackTaskPublisher;
import tech.icc.filesrv.spi.kafka.executor.KafkaDeadLetterPublisher;

/**
 * Kafka 执行器自动配置
 */
@AutoConfiguration(after = {FileServiceAutoConfiguration.class, ExecutorAutoConfiguration.class})
@EnableConfigurationProperties(ExecutorProperties.class)
@ConditionalOnProperty(prefix = "file-service.executor", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KafkaExecutorAutoConfiguration {

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
     * Kafka Callback 任务消费者
     */
    @Bean
    @ConditionalOnMissingBean(KafkaCallbackTaskConsumer.class)
    @ConditionalOnBean(CallbackTaskMessageHandler.class)
    public KafkaCallbackTaskConsumer kafkaCallbackTaskConsumer(CallbackTaskMessageHandler handler) {
        return new KafkaCallbackTaskConsumer(handler);
    }
}
