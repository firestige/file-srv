package tech.icc.filesrv.common.spi.event;

import tech.icc.filesrv.common.domain.events.DerivedFilesAddedEvent;
import tech.icc.filesrv.common.domain.events.TaskCompletedEvent;
import tech.icc.filesrv.common.domain.events.TaskFailedEvent;

/**
 * 任务事件发布器
 * <p>
 * 将任务生命周期事件发布到消息中间件。
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

    /**
     * 发布衍生文件添加事件
     *
     * @param event 衍生文件添加事件
     */
    void publishDerivedFilesAdded(DerivedFilesAddedEvent event);
}