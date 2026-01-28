package tech.icc.filesrv.core.infra.executor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tech.icc.filesrv.core.infra.executor.CallbackTaskPublisher;
import tech.icc.filesrv.core.infra.executor.ExecutorProperties;
import tech.icc.filesrv.core.infra.executor.message.CallbackTaskMessage;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka 实现的 Callback 任务发布器
 */
public class KafkaCallbackTaskPublisher implements CallbackTaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaCallbackTaskPublisher.class);

    private final KafkaTemplate<String, CallbackTaskMessage> kafkaTemplate;
    private final ExecutorProperties properties;

    public KafkaCallbackTaskPublisher(KafkaTemplate<String, CallbackTaskMessage> kafkaTemplate,
                                       ExecutorProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(String taskId) {
        // 计算消息过期时间
        Instant deadline = Instant.now().plus(properties.timeout().taskDeadline());

        // 创建消息
        CallbackTaskMessage message = CallbackTaskMessage.create(taskId, deadline);

        String topic = properties.kafka().topic();

        log.info("Publishing callback task: taskId={}, messageId={}, topic={}",
                taskId, message.messageId(), topic);

        // 异步发送，使用 taskId 作为 key 保证同一任务的消息顺序
        CompletableFuture<SendResult<String, CallbackTaskMessage>> future =
                kafkaTemplate.send(topic, taskId, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish callback task: taskId={}, messageId={}",
                        taskId, message.messageId(), ex);
            } else {
                log.debug("Callback task published: taskId={}, messageId={}, partition={}, offset={}",
                        taskId, message.messageId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
