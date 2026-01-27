package tech.icc.filesrv.core.infra.event;

import tech.icc.filesrv.core.domain.events.TaskCompletedEvent;
import tech.icc.filesrv.core.domain.events.TaskFailedEvent;

/**
 * 任务事件发布器
 * <p>
 * 将任务生命周期事件发布到 Kafka。
 */
public interface TaskEventPublisher {

    /**
     * 发布任务完成事件
     *
     * @param event 完成事件
     */
    void publishCompleted(TaskCompletedEvent event);

    /**
     * 发布任务失败事件
     *
     * @param event 失败事件
     */
    void publishFailed(TaskFailedEvent event);
}
