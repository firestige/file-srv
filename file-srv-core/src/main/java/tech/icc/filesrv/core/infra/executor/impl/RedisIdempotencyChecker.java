package tech.icc.filesrv.core.infra.executor.impl;

import tech.icc.filesrv.common.spi.executor.IdempotencyChecker;

import java.time.Duration;

/**
 * @deprecated Moved to tech.icc.filesrv.spi.redis.executor.RedisIdempotencyChecker.
 */
@Deprecated
public class RedisIdempotencyChecker implements IdempotencyChecker {

    @Override
    public boolean isDuplicate(String messageId) {
        throw new UnsupportedOperationException(
                "Moved to tech.icc.filesrv.spi.redis.executor.RedisIdempotencyChecker"
        );
    }

    @Override
    public void markProcessed(String messageId, Duration ttl) {
        throw new UnsupportedOperationException(
                "Moved to tech.icc.filesrv.spi.redis.executor.RedisIdempotencyChecker"
        );
    }
}
