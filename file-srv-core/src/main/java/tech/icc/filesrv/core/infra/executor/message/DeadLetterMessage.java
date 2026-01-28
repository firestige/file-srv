package tech.icc.filesrv.core.infra.executor.message;

import java.time.Instant;

/**
 * 死信消息
 * <p>
 * 当任务最终失败（本地重试耗尽）时，发送到死信队列。
 *
 * @param taskId            任务 ID
 * @param originalMessageId 原始消息 ID
 * @param failureReason     失败原因
 * @param failedAt          失败时间
 * @param failedNodeId      失败节点 ID
 */
public record DeadLetterMessage(
        String taskId,
        String originalMessageId,
        String failureReason,
        Instant failedAt,
        String failedNodeId
) {

    /**
     * 从原始消息创建死信
     *
     * @param original      原始消息
     * @param failureReason 失败原因
     * @param nodeId        当前节点 ID
     * @return 死信消息
     */
    public static DeadLetterMessage from(CallbackTaskMessage original,
                                         String failureReason,
                                         String nodeId) {
        return new DeadLetterMessage(
                original.taskId(),
                original.messageId(),
                failureReason,
                Instant.now(),
                nodeId
        );
    }
}
