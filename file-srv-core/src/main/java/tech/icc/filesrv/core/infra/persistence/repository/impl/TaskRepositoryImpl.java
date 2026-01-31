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
import tech.icc.filesrv.core.infra.persistence.entity.TaskEntity;
import tech.icc.filesrv.core.infra.persistence.repository.TaskJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 任务仓储实现
 */
@Repository
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {

    private final TaskJpaRepository jpaRepository;

    @Override
    @Transactional
    public TaskAggregate save(TaskAggregate task) {
        TaskEntity entity = TaskEntity.fromDomain(task);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskAggregate> findByTaskId(String taskId) {
        return jpaRepository.findById(taskId)
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
