package tech.icc.filesrv.spi.kafka.executor;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import tech.icc.filesrv.common.executor.message.CallbackTaskMessage;
import tech.icc.filesrv.common.spi.executor.CallbackTaskMessageHandler;

/**
 * Kafka callback task consumer.
 * <p>
 * This consumer is active in production environments only.
 * Test environments use Spring events instead.
 * </p>
 */
@Profile("!test")
public class KafkaCallbackTaskConsumer {

    private final CallbackTaskMessageHandler handler;

    public KafkaCallbackTaskConsumer(CallbackTaskMessageHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(
            topics = "${file-service.executor.kafka.topic:file-callback-tasks}",
            groupId = "${file-service.executor.kafka.consumer-group:file-callback-executor}",
            concurrency = "${file-service.executor.kafka.concurrency:4}"
    )
    public void consume(CallbackTaskMessage msg, Acknowledgment ack) {
        CallbackTaskMessageHandler.HandleResult result = handler.handle(msg);
        if (result == CallbackTaskMessageHandler.HandleResult.ACK) {
            ack.acknowledge();
            return;
        }
        throw new RuntimeException("Retry requested for messageId=" + msg.messageId());
    }
}
