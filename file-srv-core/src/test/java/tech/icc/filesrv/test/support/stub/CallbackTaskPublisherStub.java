package tech.icc.filesrv.test.support.stub;

import lombok.Getter;
import tech.icc.filesrv.core.infra.executor.CallbackTaskPublisher;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Callback 任务发布器 Stub
 * <p>
 * 用于集成测试，不依赖 Kafka。
 * 提供任务发布记录功能，方便测试验证。
 */
public class CallbackTaskPublisherStub implements CallbackTaskPublisher {

    @Getter
    private final List<String> publishedTaskIds = new CopyOnWriteArrayList<>();

    @Override
    public void publish(String taskId) {
        publishedTaskIds.add(taskId);
    }

    /**
     * 清空所有记录的任务
     */
    public void clear() {
        publishedTaskIds.clear();
    }

    /**
     * 获取已发布的任务数量
     */
    public int getPublishedCount() {
        return publishedTaskIds.size();
    }

    /**
     * 检查任务是否已发布
     */
    public boolean isPublished(String taskId) {
        return publishedTaskIds.contains(taskId);
    }
}
