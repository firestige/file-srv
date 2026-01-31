package tech.icc.filesrv.test.support.stub;

import lombok.Getter;
import tech.icc.filesrv.core.domain.events.TaskCompletedEvent;
import tech.icc.filesrv.core.domain.events.TaskFailedEvent;
import tech.icc.filesrv.core.infra.event.TaskEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务事件发布器 Stub
 * <p>
 * 用于集成测试，不依赖 Kafka。
 * 提供事件记录功能，方便测试验证。
 */
public class TaskEventPublisherStub implements TaskEventPublisher {

    @Getter
    private final List<TaskCompletedEvent> completedEvents = new CopyOnWriteArrayList<>();

    @Getter
    private final List<TaskFailedEvent> failedEvents = new CopyOnWriteArrayList<>();

    @Override
    public void publishCompleted(TaskCompletedEvent event) {
        completedEvents.add(event);
    }

    @Override
    public void publishFailed(TaskFailedEvent event) {
        failedEvents.add(event);
    }

    /**
     * 清空所有记录的事件
     */
    public void clear() {
        completedEvents.clear();
        failedEvents.clear();
    }

    /**
     * 获取所有已发布的事件总数
     */
    public int getTotalEventCount() {
        return completedEvents.size() + failedEvents.size();
    }
}
