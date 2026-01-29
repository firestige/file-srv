package tech.icc.filesrv.core.infra.cache.impl;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.icc.filesrv.core.infra.cache.TaskIdValidator;

import java.util.UUID;

/**
 * 基于 Redis Bloom Filter 的分布式任务 ID 验证器
 * <p>
 * 特点：
 * <ul>
 *   <li>全局共享：所有服务节点共享同一个布隆过滤器</li>
 *   <li>高性能：Redis 原生实现，O(1) 时间复杂度</li>
 *   <li>持久化：布隆过滤器数据持久化到 Redis</li>
 *   <li>防穿透：快速过滤不存在的 taskId，避免无效查询</li>
 * </ul>
 * 
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>多节点部署时，共享布隆过滤器状态</li>
 *   <li>防止恶意请求或误用导致的数据库压力</li>
 * </ul>
 * 
 * <p><b>权衡：</b>
 * <ul>
 *   <li>✅ 全局一致性：所有节点共享状态</li>
 *   <li>✅ 容量可扩展：基于 Redis 内存，可动态调整</li>
 *   <li>⚠️ 网络开销：每次检查需要 Redis 网络调用（~1ms）</li>
 *   <li>⚠️ Redis 依赖：需要 Redis 和 RedisBloom 模块</li>
 * </ul>
 */
public class RedisBloomTaskIdValidator implements TaskIdValidator {

    private static final Logger log = LoggerFactory.getLogger(RedisBloomTaskIdValidator.class);
    
    private static final String BLOOM_KEY = "file-srv:task:bloom";
    
    private final RBloomFilter<String> bloomFilter;
    
    /**
     * 创建分布式布隆过滤器
     * 
     * @param redissonClient Redisson 客户端
     * @param expectedInsertions 预期插入数量
     * @param falsePositiveRate 误判率 (0-1)
     */
    public RedisBloomTaskIdValidator(RedissonClient redissonClient, 
                                     int expectedInsertions, 
                                     double falsePositiveRate) {
        this.bloomFilter = redissonClient.getBloomFilter(BLOOM_KEY);
        
        // 初始化布隆过滤器（幂等操作，已存在则跳过）
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(expectedInsertions, falsePositiveRate);
            log.info("Redis BloomFilter initialized: key={}, expectedInsertions={}, fpp={}", 
                     BLOOM_KEY, expectedInsertions, falsePositiveRate);
        } else {
            log.info("Redis BloomFilter already exists: key={}, count={}", 
                     BLOOM_KEY, bloomFilter.count());
        }
    }
    
    /**
     * 使用默认配置创建（100万任务，1%误判率）
     */
    public RedisBloomTaskIdValidator(RedissonClient redissonClient) {
        this(redissonClient, 1_000_000, 0.01);
    }

    @Override
    public boolean isValidFormat(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        // UUID 格式校验
        try {
            UUID.fromString(taskId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean mightExist(String taskId) {
        if (!isValidFormat(taskId)) {
            return false;
        }
        
        try {
            return bloomFilter.contains(taskId);
        } catch (Exception e) {
            log.error("Failed to check bloom filter: taskId={}", taskId, e);
            // 降级：Redis 故障时，认为可能存在（放行到下一层防护）
            return true;
        }
    }

    @Override
    public void register(String taskId) {
        if (!isValidFormat(taskId)) {
            log.warn("Invalid taskId format, skip register: {}", taskId);
            return;
        }
        
        try {
            boolean added = bloomFilter.add(taskId);
            if (added) {
                log.debug("TaskId registered to Redis bloom filter: {}", taskId);
            }
        } catch (Exception e) {
            log.error("Failed to register taskId to bloom filter: {}", taskId, e);
            // 降级：注册失败不影响业务（下一层有缓存和 DB）
        }
    }

    /**
     * 批量注册（使用接口默认方法）
     */
    @Override
    public void registerAll(Iterable<String> taskIds) {
        if (taskIds == null) {
            return;
        }
        
        int count = 0;
        for (String taskId : taskIds) {
            if (isValidFormat(taskId)) {
                try {
                    bloomFilter.add(taskId);
                    count++;
                } catch (Exception e) {
                    log.error("Failed to register taskId in batch: {}", taskId, e);
                }
            }
        }
        
        log.info("Batch registered {} taskIds to Redis bloom filter", count);
    }
    
    /**
     * 获取当前布隆过滤器中的元素数量（近似值）
     */
    public long getCount() {
        try {
            return bloomFilter.count();
        } catch (Exception e) {
            log.error("Failed to get bloom filter count", e);
            return -1;
        }
    }
    
    /**
     * 获取布隆过滤器配置信息（用于监控）
     */
    public String getInfo() {
        try {
            return String.format("BloomFilter[key=%s, count=%d, size=%d]", 
                               BLOOM_KEY, bloomFilter.count(), bloomFilter.getSize());
        } catch (Exception e) {
            return String.format("BloomFilter[key=%s, error=%s]", BLOOM_KEY, e.getMessage());
        }
    }
}
