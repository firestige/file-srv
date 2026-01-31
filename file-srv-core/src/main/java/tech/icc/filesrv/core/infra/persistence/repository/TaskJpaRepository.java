package tech.icc.filesrv.core.infra.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.icc.filesrv.core.domain.tasks.TaskStatus;
import tech.icc.filesrv.core.infra.persistence.entity.TaskEntity;

import java.time.Instant;
import java.util.List;

/**
 * 任务 JPA Repository
 */
public interface TaskJpaRepository extends JpaRepository<TaskEntity, String> {

    /**
     * 根据 fKey 查找任务列表
     */
    @Query("SELECT t FROM TaskEntity t WHERE t.fKey = :fKey")
    List<TaskEntity> findByFKey(@Param("fKey") String fKey);

    /**
     * 根据状态分页查询
     */
    Page<TaskEntity> findByStatus(TaskStatus status, Pageable pageable);

    /**
     * 查询过期任务
     * <p>
     * 注意：只查询非终态任务
     *
     * @param before 过期时间阈值
     * @param pageable 分页参数（用于限制数量）
     */
    @Query("SELECT t FROM TaskEntity t WHERE t.expiresAt < :before " +
           "AND t.status NOT IN ('COMPLETED', 'FAILED', 'ABORTED', 'EXPIRED') " +
           "ORDER BY t.expiresAt ASC")
    Page<TaskEntity> findExpiredTasks(@Param("before") Instant before, Pageable pageable);

    /**
     * 查询需要清理的已完成任务
     *
     * @param completedBefore 完成时间阈值
     * @param pageable 分页参数（用于限制数量）
     */
    @Query("SELECT t FROM TaskEntity t WHERE t.status = 'COMPLETED' " +
           "AND t.completedAt < :completedBefore " +
           "ORDER BY t.completedAt ASC")
    Page<TaskEntity> findCompletedTasksForCleanup(@Param("completedBefore") Instant completedBefore,
                                                   Pageable pageable);

    /**
     * 批量删除任务
     */
    @Modifying
    @Query("DELETE FROM TaskEntity t WHERE t.taskId IN :taskIds")
    void deleteAllByTaskIds(@Param("taskIds") List<String> taskIds);
}
