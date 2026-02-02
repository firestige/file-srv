package tech.icc.filesrv.core.infra.executor.impl;

import tech.icc.filesrv.common.spi.executor.DeadLetterPublisher;
import tech.icc.filesrv.common.executor.message.DeadLetterMessage;

/**
 * @deprecated Moved to tech.icc.filesrv.spi.kafka.executor.KafkaDeadLetterPublisher.
 */
@Deprecated
public class KafkaDeadLetterPublisher implements DeadLetterPublisher {

    @Override
    public void publish(DeadLetterMessage message) {
        throw new UnsupportedOperationException(
                "Moved to tech.icc.filesrv.spi.kafka.executor.KafkaDeadLetterPublisher"
        );
    }
}
