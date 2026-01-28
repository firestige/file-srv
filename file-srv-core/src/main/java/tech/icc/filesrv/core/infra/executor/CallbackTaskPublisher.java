package tech.icc.filesrv.core.infra.executor;

/**
 * Callback 任务发布器
 * <p>
 * 负责将 callback 任务发布到 Kafka。
 * 设计要点：
 * <ul>
 *   <li>Task 级别调度：一个 Task 只发布一条消息</li>
 *   <li>Consumer 从 DB 获取 currentCallbackIndex 实现断点恢复</li>
 * </ul>
 */
public interface CallbackTaskPublisher {

    /**
     * 发布 callback 任务
     *
     * @param taskId 任务 ID
     */
    void publish(String taskId);
}
