package tech.icc.filesrv.core.infra.executor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.icc.filesrv.common.spi.executor.CallbackTaskPublisher;

/**
 * No-op Callback 任务发布器
 * <p>
 * 当 Kafka 不可用时使用，仅记录日志，不实际发布消息。
 * 适用于开发环境或单机部署场景。
 */
public class NoOpCallbackTaskPublisher implements CallbackTaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpCallbackTaskPublisher.class);

    @Override
    public void publish(String taskId) {
        log.warn("NoOp publisher: callback task not published (Kafka disabled): taskId={}", taskId);
    }
}
