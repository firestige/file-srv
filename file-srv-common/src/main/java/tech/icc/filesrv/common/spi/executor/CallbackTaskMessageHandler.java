package tech.icc.filesrv.common.spi.executor;

import tech.icc.filesrv.common.executor.message.CallbackTaskMessage;

/**
 * Callback 任务消息处理器（协议层）
 * <p>
 * 负责处理消息并给出是否可确认的结果。
 */
public interface CallbackTaskMessageHandler {

    /**
     * 处理消息
     *
     * @param message 回调任务消息
     * @return 处理结果
     */
    HandleResult handle(CallbackTaskMessage message);

    enum HandleResult {
        ACK,
        RETRY
    }
}
