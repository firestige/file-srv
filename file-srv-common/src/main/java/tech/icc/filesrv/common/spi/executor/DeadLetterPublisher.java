package tech.icc.filesrv.common.spi.executor;

import tech.icc.filesrv.common.executor.message.DeadLetterMessage;

/**
 * 死信发布器
 * <p>
 * 负责将最终失败的任务发送到死信队列。
 */
public interface DeadLetterPublisher {

    /**
     * 发布死信消息
     *
     * @param message 死信消息
     */
    void publish(DeadLetterMessage message);
}