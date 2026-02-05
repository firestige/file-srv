package tech.icc.filesrv.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
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
 * <p>
 * 使用静态内部类分离 Publisher 和 Consumer 的配置，
 * 以解决 Bean 创建顺序依赖问题：
 * <ul>
 *   <li>PublisherConfiguration: 在 ExecutorAutoConfiguration 之前执行，提供 DeadLetterPublisher</li>
 *   <li>ConsumerConfiguration: 在 ExecutorAutoConfiguration 之后执行，依赖 CallbackTaskMessageHandler</li>
 * </ul>
 */
@EnableKafka
@AutoConfiguration(after = FileServiceAutoConfiguration.class)
@EnableConfigurationProperties(ExecutorProperties.class)
@ConditionalOnProperty(prefix = "file-service.executor", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KafkaExecutorAutoConfiguration {

    /**
     * Publisher 配置（在 ExecutorAutoConfiguration 之前）
     * <p>
     * 提供 DeadLetterPublisher 和 CallbackTaskPublisher 实现，
     * 供 ExecutorAutoConfiguration 创建 CallbackTaskMessageHandler 使用。
     */
    @Configuration(proxyBeanMethods = false)
    @AutoConfiguration(before = ExecutorAutoConfiguration.class)
    static class PublisherConfiguration {

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
    }

    /**
     * Consumer 配置（在 ExecutorAutoConfiguration 之后）
     * <p>
     * 创建 Kafka Listener 容器工厂和 Consumer Bean，
     * 依赖 ExecutorAutoConfiguration 提供的 CallbackTaskMessageHandler。
     */
    @Configuration(proxyBeanMethods = false)
    @AutoConfiguration(after = ExecutorAutoConfiguration.class)
    static class ConsumerConfiguration {

        /**
         * Kafka Listener 容器工厂（支持手动 ACK）
         */
        @Bean
        @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
        @ConditionalOnBean(ConsumerFactory.class)
        public ConcurrentKafkaListenerContainerFactory<String, CallbackTaskMessage> kafkaListenerContainerFactory(
                ConsumerFactory<Object, Object> consumerFactory) {
            ConcurrentKafkaListenerContainerFactory<String, CallbackTaskMessage> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            // Spring Kafka 的 ConsumerFactory 是泛型类型，可以强制转换
            factory.setConsumerFactory((ConsumerFactory<String, CallbackTaskMessage>) (Object) consumerFactory);
            factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
            return factory;
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
}
