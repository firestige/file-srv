package tech.icc.filesrv.core.infra.executor;

import java.time.Duration;

/**
 * 幂等检查器
 * <p>
 * 防止重复消费同一消息。
 */
public interface IdempotencyChecker {

    /**
     * 检查消息是否已处理
     *
     * @param messageId 消息唯一标识
     * @return true 表示已处理（重复）
     */
    boolean isDuplicate(String messageId);

    /**
     * 标记消息已处理
     *
     * @param messageId 消息唯一标识
     * @param ttl       过期时间
     */
    void markProcessed(String messageId, Duration ttl);
}
