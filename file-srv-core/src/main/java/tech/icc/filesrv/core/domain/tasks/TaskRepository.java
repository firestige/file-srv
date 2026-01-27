package tech.icc.filesrv.core.domain.tasks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 任务仓储接口
 */
public interface TaskRepository {

    /**
     * 保存任务
     *
     * @param task 任务聚合
     * @return 保存后的任务
     */
    TaskAggregate save(TaskAggregate task);

    /**
     * 根据 ID 查询任务
     *
     * @param taskId 任务 ID
     * @return 任务（如果存在）
     */
    Optional<TaskAggregate> findByTaskId(String taskId);

    /**
     * 根据 fKey 查询任务列表
     *
     * @param fKey 用户文件标识
     * @return 任务列表
     */
    List<TaskAggregate> findByFKey(String fKey);

    /**
     * 根据状态查询任务
     *
     * @param status   任务状态
     * @param pageable 分页参数
     * @return 任务分页
     */
    Page<TaskAggregate> findByStatus(TaskStatus status, Pageable pageable);

    /**
     * 查询过期任务
     *
     * @param before 过期时间阈值
     * @param limit  最大数量
     * @return 过期任务列表
     */
    List<TaskAggregate> findExpiredTasks(Instant before, int limit);

    /**
     * 查询需要清理的已完成任务
     *
     * @param completedBefore 完成时间阈值
     * @param limit           最大数量
     * @return 任务列表
     */
    List<TaskAggregate> findCompletedTasksForCleanup(Instant completedBefore, int limit);

    /**
     * 删除任务
     *
     * @param taskId 任务 ID
     */
    void deleteByTaskId(String taskId);

    /**
     * 批量删除任务
     *
     * @param taskIds 任务 ID 列表
     */
    void deleteAllByTaskIds(List<String> taskIds);

    /**
     * 检查任务是否存在
     *
     * @param taskId 任务 ID
     * @return 是否存在
     */
    boolean existsByTaskId(String taskId);
}
