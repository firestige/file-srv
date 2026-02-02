package tech.icc.filesrv.common.domain.events;

import tech.icc.filesrv.common.vo.task.TaskStatus;

import java.time.Instant;

/**
 * 任务失败事件
 * <p>
 * 当上传任务失败、中止或超时时发布到 Kafka。
 *
 * @param taskId             任务 ID
 * @param fKey               用户文件标识
 * @param finalStatus        最终状态 (FAILED / ABORTED / EXPIRED)
 * @param failureReason      失败原因
 * @param lastCallbackIndex  失败时执行到第几个 callback (-1 表示上传阶段失败)
 * @param failedAt           失败时间
 */
public record TaskFailedEvent(
        String taskId,
        String fKey,
        TaskStatus finalStatus,
        String failureReason,
        int lastCallbackIndex,
        Instant failedAt
) {

    /**
     * 创建上传阶段失败事件
     */
    public static TaskFailedEvent uploadFailed(String taskId, String fKey, String reason) {
        return new TaskFailedEvent(
                taskId,
                fKey,
                TaskStatus.FAILED,
                reason,
                -1,
                Instant.now()
        );
    }

    /**
     * 创建 callback 执行失败事件
     */
    public static TaskFailedEvent callbackFailed(
            String taskId,
            String fKey,
            String reason,
            int callbackIndex
    ) {
        return new TaskFailedEvent(
                taskId,
                fKey,
                TaskStatus.FAILED,
                reason,
                callbackIndex,
                Instant.now()
        );
    }

    /**
     * 创建中止事件
     */
    public static TaskFailedEvent aborted(String taskId, String fKey) {
        return new TaskFailedEvent(
                taskId,
                fKey,
                TaskStatus.ABORTED,
                "Task aborted by user",
                -1,
                Instant.now()
        );
    }

    /**
     * 创建超时事件
     */
    public static TaskFailedEvent expired(String taskId, String fKey) {
        return new TaskFailedEvent(
                taskId,
                fKey,
                TaskStatus.EXPIRED,
                "Task expired",
                -1,
                Instant.now()
        );
    }
}
