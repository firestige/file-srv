package tech.icc.filesrv.core.infra.cache.impl;

import tech.icc.filesrv.common.spi.cache.TaskIdValidator;

/**
 * @deprecated Moved to tech.icc.filesrv.spi.redis.cache.RedisBloomTaskIdValidator.
 */
@Deprecated
public class RedisBloomTaskIdValidator implements TaskIdValidator {

    @Override
    public boolean isValidFormat(String taskId) {
        throw new UnsupportedOperationException(
                "Moved to tech.icc.filesrv.spi.redis.cache.RedisBloomTaskIdValidator"
        );
    }

    @Override
    public boolean mightExist(String taskId) {
        throw new UnsupportedOperationException(
                "Moved to tech.icc.filesrv.spi.redis.cache.RedisBloomTaskIdValidator"
        );
    }

    @Override
    public void register(String taskId) {
        throw new UnsupportedOperationException(
                "Moved to tech.icc.filesrv.spi.redis.cache.RedisBloomTaskIdValidator"
        );
    }
}
