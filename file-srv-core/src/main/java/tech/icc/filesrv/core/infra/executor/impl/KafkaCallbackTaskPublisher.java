package tech.icc.filesrv.core.infra.executor.impl;

import tech.icc.filesrv.common.spi.executor.CallbackTaskPublisher;

/**
 * @deprecated Moved to tech.icc.filesrv.spi.kafka.executor.KafkaCallbackTaskPublisher.
 */
@Deprecated
public class KafkaCallbackTaskPublisher implements CallbackTaskPublisher {

    @Override
    public void publish(String taskId) {
        throw new UnsupportedOperationException(
                "Moved to tech.icc.filesrv.spi.kafka.executor.KafkaCallbackTaskPublisher"
        );
    }
}
