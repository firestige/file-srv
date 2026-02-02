package tech.icc.filesrv.config;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import tech.icc.filesrv.common.spi.cache.TaskIdValidator;
import tech.icc.filesrv.common.spi.executor.IdempotencyChecker;
import tech.icc.filesrv.spi.redis.cache.RedisBloomTaskIdValidator;
import tech.icc.filesrv.spi.redis.executor.RedisIdempotencyChecker;

/**
 * Redis SPI 自动配置（装配层）
 */
@AutoConfiguration(after = FileServiceAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(FileServiceProperties.class)
public class RedisSpiAutoConfiguration {

    /**
     * 幂等检查器
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyChecker.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public IdempotencyChecker redisIdempotencyChecker(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyChecker(redisTemplate);
    }

    /**
     * 任务ID校验器 - Redis 布隆过滤器实现（优先）
     * <p>
     * 适用于多节点部署，全局共享布隆过滤器状态。
     * 要求：
     * <ul>
     *   <li>Redis 可用且配置了 Redisson</li>
     *   <li>file-srv.bloom-filter.use-redis=true（默认）</li>
     * </ul>
     *
     * <p>配置项：
     * - file-srv.bloom-filter.expected-insertions: 预期插入数量，默认 1000000
     * - file-srv.bloom-filter.fpp: 误判率，默认 0.01
     */
    @Bean
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnProperty(name = "file-srv.bloom-filter.use-redis", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(TaskIdValidator.class)
    public TaskIdValidator redisBloomTaskIdValidator(RedissonClient redissonClient,
                                                     FileServiceProperties properties) {
        FileServiceProperties.BloomFilterProperties bloomProps = properties.getBloomFilter();
        return new RedisBloomTaskIdValidator(
                redissonClient,
                bloomProps.getExpectedInsertions(),
                bloomProps.getFpp()
        );
    }
}