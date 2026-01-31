package tech.icc.filesrv.common.vo.task;

/**
 * 任务状态枚举
 * <p>
 * 状态流转：
 * <pre>
 * PENDING ──▶ IN_PROGRESS ──▶ PROCESSING ──┬──▶ COMPLETED
 *    │                                     ├──▶ FAILED
 *    │                                     └──▶ EXPIRED
 *    └──▶ ABORTED
 * </pre>
 */
public enum TaskStatus {

    /**
     * 待上传 - 任务已创建，等待客户端上传分片
     */
    PENDING,

    /**
     * 上传中 - 至少有一个分片已上传
     */
    IN_PROGRESS,

    /**
     * 处理中 - 上传完成，正在执行 callback
     */
    PROCESSING,

    /**
     * 已完成 - callback 执行成功
     */
    COMPLETED,

    /**
     * 已失败 - 上传或 callback 执行失败
     */
    FAILED,

    /**
     * 已中止 - 客户端主动取消
     */
    ABORTED,

    /**
     * 已过期 - 任务超时未完成
     */
    EXPIRED;

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == ABORTED || this == EXPIRED;
    }

    /**
     * 是否允许上传分片
     */
    public boolean allowUpload() {
        return this == PENDING || this == IN_PROGRESS;
    }

    /**
     * 是否允许中止
     */
    public boolean allowAbort() {
        return this == PENDING || this == IN_PROGRESS;
    }
}
