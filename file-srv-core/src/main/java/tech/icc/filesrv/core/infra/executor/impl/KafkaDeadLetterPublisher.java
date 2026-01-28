package tech.icc.filesrv.core.infra.executor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tech.icc.filesrv.core.infra.executor.DeadLetterPublisher;
import tech.icc.filesrv.core.infra.executor.ExecutorProperties;
import tech.icc.filesrv.core.infra.executor.message.DeadLetterMessage;

/**
 * Kafka 实现的死信发布器
 */
public class KafkaDeadLetterPublisher implements DeadLetterPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDeadLetterPublisher.class);

    private final KafkaTemplate<String, DeadLetterMessage> kafkaTemplate;
    private final ExecutorProperties properties;

    public KafkaDeadLetterPublisher(KafkaTemplate<String, DeadLetterMessage> kafkaTemplate,
                                    ExecutorProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(DeadLetterMessage message) {
        String topic = properties.kafka().dltTopic();

        log.warn("Publishing dead letter: taskId={}, reason={}, topic={}",
                message.taskId(), message.failureReason(), topic);

        kafkaTemplate.send(topic, message.taskId(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish dead letter: taskId={}", message.taskId(), ex);
                    } else {
                        log.info("Dead letter published: taskId={}, partition={}, offset={}",
                                message.taskId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
