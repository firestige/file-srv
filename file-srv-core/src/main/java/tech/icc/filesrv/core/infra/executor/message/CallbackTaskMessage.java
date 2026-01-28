package tech.icc.filesrv.core.infra.executor.message;

import java.time.Instant;
import java.util.UUID;

/**
 * Callback 任务消息
 * <p>
 * 发布到 Kafka 的消息格式。
 * 设计要点：
 * <ul>
 *   <li>Task 级别调度：一个 Task 只发布一条消息</li>
 *   <li>断点恢复：通过 DB 中的 currentCallbackIndex 实现，不在消息中携带</li>
 *   <li>幂等检查：通过 messageId 实现</li>
 * </ul>
 *
 * @param messageId 消息唯一标识（用于幂等）
 * @param taskId    任务 ID
 * @param createdAt 消息创建时间
 * @param deadline  消息过期截止时间
 */
public record CallbackTaskMessage(
        String messageId,
        String taskId,
        Instant createdAt,
        Instant deadline
) {

    /**
     * 创建新消息
     *
     * @param taskId   任务 ID
     * @param deadline 过期截止时间
     * @return 新消息
     */
    public static CallbackTaskMessage create(String taskId, Instant deadline) {
        return new CallbackTaskMessage(
                UUID.randomUUID().toString(),
                taskId,
                Instant.now(),
                deadline
        );
    }

    /**
     * 检查消息是否已过期
     */
    public boolean isExpired() {
        return deadline != null && Instant.now().isAfter(deadline);
    }
}
