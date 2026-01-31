package tech.icc.filesrv.core.testdata.fixtures;

import tech.icc.filesrv.common.vo.task.CallbackConfig;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.domain.tasks.TaskStatus;
import tech.icc.filesrv.core.testdata.TestDataBuilders;

import java.time.Duration;
import java.util.List;

/**
 * 任务场景预设
 * <p>
 * 提供常用任务状态的快捷创建方法，简化测试代码。
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 创建待处理任务
 * TaskAggregate task = TaskFixtures.pendingTask();
 *
 * // 创建进行中的任务
 * TaskAggregate task = TaskFixtures.inProgressTask();
 *
 * // 创建有 callback 的任务
 * TaskAggregate task = TaskFixtures.taskWithCallbacks();
 * }</pre>
 */
public class TaskFixtures {

    /**
     * 创建 PENDING 状态的任务（无 callback）
     */
    public static TaskAggregate pendingTask() {
        return TestDataBuilders.aTask()
                .withFKey("pending-task-fkey")
                .build();
    }

    /**
     * 创建 IN_PROGRESS 状态的任务
     */
    public static TaskAggregate inProgressTask() {
        return TestDataBuilders.aTask()
                .withFKey("in-progress-fkey")
                .withStatus(TaskStatus.IN_PROGRESS)
                .withSessionId("session-12345")
                .withNodeId("node-1")
                .build();
    }

    /**
     * 创建 PROCESSING 状态的任务（已上传完成，正在处理）
     */
    public static TaskAggregate processingTask() {
        return TestDataBuilders.aTask()
                .withFKey("processing-fkey")
                .withStatus(TaskStatus.PROCESSING)
                .withSessionId("session-67890")
                .withNodeId("node-1")
                .withStoragePath("/storage/path/file")
                .withHash("sha256-hash-value")
                .withTotalSize(1024 * 1024L) // 1MB
                .withFilename("processing-file.pdf")
                .withContentType("application/pdf")
                .build();
    }

    /**
     * 创建 COMPLETED 状态的任务
     */
    public static TaskAggregate completedTask() {
        TaskAggregate task = TestDataBuilders.aTask()
                .withFKey("completed-fkey")
                .withStatus(TaskStatus.PROCESSING)
                .withStoragePath("/storage/path/completed-file")
                .withHash("sha256-completed-hash")
                .withTotalSize(2048 * 1024L) // 2MB
                .build();
        
        // 完成所有 callback（如果有）
        if (task.hasCallbacks()) {
            while (task.hasNextCallback()) {
                task.advanceCallback();
            }
        } else {
            // 没有 callback 直接标记完成
            try {
                var method = TaskAggregate.class.getDeclaredMethod("markCompleted");
                method.setAccessible(true);
                method.invoke(task);
            } catch (Exception e) {
                throw new RuntimeException("Failed to mark task as completed", e);
            }
        }
        
        return task;
    }

    /**
     * 创建 FAILED 状态的任务
     */
    public static TaskAggregate failedTask() {
        return failedTask("Storage service unavailable");
    }

    /**
     * 创建 FAILED 状态的任务（指定失败原因）
     */
    public static TaskAggregate failedTask(String reason) {
        TaskAggregate task = TestDataBuilders.aTask()
                .withFKey("failed-fkey")
                .withStatus(TaskStatus.IN_PROGRESS)
                .build();
        
        task.fail(reason);
        return task;
    }

    /**
     * 创建 ABORTED 状态的任务
     */
    public static TaskAggregate abortedTask() {
        TaskAggregate task = TestDataBuilders.aTask()
                .withFKey("aborted-fkey")
                .withStatus(TaskStatus.IN_PROGRESS)
                .build();
        
        task.abort();
        return task;
    }

    /**
     * 创建 EXPIRED 状态的任务
     */
    public static TaskAggregate expiredTask() {
        TaskAggregate task = TestDataBuilders.aTask()
                .withFKey("expired-fkey")
                .withExpireAfter(Duration.ofMillis(-1000)) // 已过期
                .build();
        
        task.markExpired();
        return task;
    }

    /**
     * 创建带有 callback 的任务
     */
    public static TaskAggregate taskWithCallbacks() {
        List<CallbackConfig> callbacks = List.of(
                CallbackConfig.builder()
                        .name("virus-scan")
                        .params(List.of())
                        .build(),
                CallbackConfig.builder()
                        .name("thumbnail")
                        .params(List.of())
                        .build()
        );

        return TestDataBuilders.aTask()
                .withFKey("task-with-callbacks-fkey")
                .withCallbacks(callbacks)
                .build();
    }

    /**
     * 创建带有多个分片的任务
     */
    public static TaskAggregate taskWithParts(int partCount) {
        return TestDataBuilders.aTask()
                .withFKey("task-with-parts-fkey")
                .withParts(TestDataBuilders.createParts(partCount))
                .build();
    }

    /**
     * 创建大文件任务（100MB）
     */
    public static TaskAggregate largeFileTask() {
        return TestDataBuilders.aTask()
                .withFKey("large-file-fkey")
                .withFilename("large-video.mp4")
                .withContentType("video/mp4")
                .withTotalSize(100 * 1024 * 1024L) // 100MB
                .build();
    }

    /**
     * 创建小文件任务（10KB）
     */
    public static TaskAggregate smallFileTask() {
        return TestDataBuilders.aTask()
                .withFKey("small-file-fkey")
                .withFilename("document.txt")
                .withContentType("text/plain")
                .withTotalSize(10 * 1024L) // 10KB
                .build();
    }

    /**
     * 创建即将过期的任务（1 分钟后过期）
     */
    public static TaskAggregate expiringTask() {
        return TestDataBuilders.aTask()
                .withFKey("expiring-task-fkey")
                .withExpireAfter(Duration.ofMinutes(1))
                .build();
    }
}
