package tech.icc.filesrv.core.infra.persistence.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.domain.tasks.TaskRepository;
import tech.icc.filesrv.common.vo.task.TaskStatus;
import tech.icc.filesrv.core.infra.cache.TaskCacheService;
import tech.icc.filesrv.core.infra.persistence.entity.TaskEntity;
import tech.icc.filesrv.core.infra.persistence.repository.TaskJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 任务仓储实现
 * <p>
 * 集成了缓存层（Cache-Aside 模式），提供数据库和缓存的一致性保证。
 */
@Repository
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {

    private final TaskJpaRepository jpaRepository;
    private final TaskCacheService cacheService;

    @Override
    @Transactional
    public TaskAggregate save(TaskAggregate task) {
        TaskEntity entity = TaskEntity.fromDomain(task);
        TaskEntity saved = jpaRepository.save(entity);
        TaskAggregate result = saved.toDomain();
        
        // Write-Invalidate: 写DB成功后失效缓存
        // 避免并发场景下的version冲突和缓存不一致
        cacheService.evictTask(result.getTaskId());
        
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskAggregate> findByTaskId(String taskId) {
        // Cache-Aside 读模式：先查缓存
        Optional<TaskAggregate> cached = cacheService.getTask(taskId);
        if (cached.isPresent()) {
            return cached; // 缓存命中
        }
        
        // 缓存未命中，检查是否是 NULL 缓存（防穿透）
        if (cacheService.isNullCached(taskId)) {
            return Optional.empty();
        }
        
        // 查询数据库并缓存结果
        Optional<TaskAggregate> result = jpaRepository.findById(taskId)
                .map(TaskEntity::toDomain);
        
        if (result.isPresent()) {
            cacheService.cacheTask(result.get());
        } else {
            cacheService.cacheNull(taskId); // 防穿透：缓存空值
        }
        
        return result;
    }

    @Override
    @Transactional
    public Optional<TaskAggregate> findByTaskIdForUpdate(String taskId) {
        // 悲观锁查询：SELECT FOR UPDATE
        // 直接从数据库查询并加锁，不使用缓存，确保获取最新版本
        // 锁会持续到当前事务提交或回滚
        return jpaRepository.findByTaskIdForUpdate(taskId)
                .map(TaskEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskAggregate> findByFKey(String fKey) {
        return jpaRepository.findByFKey(fKey).stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskAggregate> findByStatus(TaskStatus status, Pageable pageable) {
        return jpaRepository.findByStatus(status, pageable)
                .map(TaskEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskAggregate> findExpiredTasks(Instant before, int limit) {
        Page<TaskEntity> page = jpaRepository.findExpiredTasks(before, PageRequest.of(0, limit));
        return page.getContent().stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskAggregate> findCompletedTasksForCleanup(Instant completedBefore, int limit) {
        Page<TaskEntity> page = jpaRepository.findCompletedTasksForCleanup(
                completedBefore,
                PageRequest.of(0, limit)
        );
        return page.getContent().stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByTaskId(String taskId) {
        jpaRepository.deleteById(taskId);
        
        // 删除操作：先删 DB，再失效缓存
        cacheService.evictTask(taskId);
    }

    @Override
    @Transactional
    public void deleteAllByTaskIds(List<String> taskIds) {
        if (taskIds != null && !taskIds.isEmpty()) {
            jpaRepository.deleteAllByTaskIds(taskIds);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTaskId(String taskId) {
        return jpaRepository.existsById(taskId);
    }
}
