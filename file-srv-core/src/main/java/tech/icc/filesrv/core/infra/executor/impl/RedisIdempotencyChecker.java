package tech.icc.filesrv.core.infra.executor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tech.icc.filesrv.core.infra.executor.IdempotencyChecker;

import java.time.Duration;

/**
 * Redis 实现的幂等检查器
 * <p>
 * 使用 Redis SET NX 命令实现幂等检查。
 */
public class RedisIdempotencyChecker implements IdempotencyChecker {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyChecker.class);

    private static final String KEY_PREFIX = "file-srv:callback:idempotency:";

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyChecker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isDuplicate(String messageId) {
        String key = buildKey(messageId);
        Boolean exists = redisTemplate.hasKey(key);
        boolean duplicate = Boolean.TRUE.equals(exists);

        if (duplicate) {
            log.debug("Duplicate message detected: messageId={}", messageId);
        }

        return duplicate;
    }

    @Override
    public void markProcessed(String messageId, Duration ttl) {
        String key = buildKey(messageId);
        redisTemplate.opsForValue().set(key, "1", ttl);
        log.debug("Message marked as processed: messageId={}, ttl={}", messageId, ttl);
    }

    private String buildKey(String messageId) {
        return KEY_PREFIX + messageId;
    }
}
