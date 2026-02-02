package tech.icc.filesrv.core.infra.cache.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.infra.cache.TaskCacheService;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于 Caffeine 的本地任务缓存实现
 */
public class CaffeineTaskCacheService implements TaskCacheService {

    private static final Logger log = LoggerFactory.getLogger(CaffeineTaskCacheService.class);

    private static final String NULL_MARKER = "__NULL__";

    private final Cache<String, Object> cache;

    public CaffeineTaskCacheService() {
        this(10000, Duration.ofSeconds(30));
    }

    public CaffeineTaskCacheService(int maxSize, Duration ttl) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build();
        log.info("CaffeineTaskCacheService initialized: maxSize={}, ttl={}", maxSize, ttl);
    }

    @Override
    public Optional<TaskAggregate> getTask(String taskId) {
        Object value = cache.getIfPresent(taskId);
        if (value == null) {
            return Optional.empty();
        }
        if (NULL_MARKER.equals(value)) {
            return Optional.empty();
        }
        return Optional.of((TaskAggregate) value);
    }

    @Override
    public void cacheTask(TaskAggregate task) {
        if (task != null && task.getTaskId() != null) {
            cache.put(task.getTaskId(), task);
            log.debug("Task cached: taskId={}", task.getTaskId());
        }
    }

    @Override
    public void evictTask(String taskId) {
        cache.invalidate(taskId);
        log.debug("Task evicted: taskId={}", taskId);
    }

    @Override
    public void cacheNull(String taskId) {
        cache.put(taskId, NULL_MARKER);
        log.debug("Null cached for taskId={}", taskId);
    }

    @Override
    public boolean isNullCached(String taskId) {
        Object value = cache.getIfPresent(taskId);
        return NULL_MARKER.equals(value);
    }

    /**
     * 获取缓存统计信息
     */
    public String getStats() {
        return cache.stats().toString();
    }
}
