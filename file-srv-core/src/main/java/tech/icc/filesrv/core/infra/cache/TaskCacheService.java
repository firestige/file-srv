package tech.icc.filesrv.core.infra.cache;

import tech.icc.filesrv.core.domain.tasks.TaskAggregate;

import java.util.Optional;

/**
 * 任务缓存服务
 * <p>
 * 提供任务的多级缓存能力，减少数据库访问。
 */
public interface TaskCacheService {

    /**
     * 从缓存获取任务
     *
     * @param taskId 任务 ID
     * @return 任务（如果缓存中存在）
     */
    Optional<TaskAggregate> getTask(String taskId);

    /**
     * 缓存任务
     *
     * @param task 任务聚合
     */
    void cacheTask(TaskAggregate task);

    /**
     * 失效缓存
     *
     * @param taskId 任务 ID
     */
    void evictTask(String taskId);

    /**
     * 缓存空值标记（防止缓存穿透）
     *
     * @param taskId 不存在的任务 ID
     */
    void cacheNull(String taskId);

    /**
     * 检查是否为空值缓存
     *
     * @param taskId 任务 ID
     * @return 如果是空值缓存返回 true
     */
    boolean isNullCached(String taskId);
}
