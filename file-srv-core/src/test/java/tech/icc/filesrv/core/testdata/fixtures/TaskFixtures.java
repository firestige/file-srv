package tech.icc.filesrv.core.testdata.fixtures;

import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.common.vo.task.TaskStatus;

/**
 * 任务场景预设
 * <p>
 * 提供常用任务状态的快捷创建方法，提高测试可读性。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>命名直观 - 方法名即场景说明</li>
 *   <li>固定数据 - 同一场景总是返回相同结构的数据</li>
 *   <li>最小必要 - 只设置场景相关的关键字段</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 创建待处理任务
 * TaskAggregate task = TaskFixtures.pendingTask();
 * 
 * // 创建进行中的任务
 * TaskAggregate task = TaskFixtures.inProgressTask();
 * 
 * // 创建带回调的任务
 * TaskAggregate task = TaskFixtures.taskWithCallbacks();
 * }</pre>
 */
public class TaskFixtures {

    /**
     * 待处理任务（PENDING）
     * <p>
     * 状态: PENDING<br>
     * Callbacks: 空<br>
     * 用途: 测试任务创建、初始状态验证
     *
     * @return PENDING 状态的任务
     */
    public static TaskAggregate pendingTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 上传中的任务（IN_PROGRESS）
     * <p>
     * 状态: IN_PROGRESS<br>
     * SessionId: 已分配<br>
     * NodeId: node-1<br>
     * 用途: 测试分片上传流程
     *
     * @return IN_PROGRESS 状态的任务
     */
    public static TaskAggregate inProgressTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 处理中的任务（PROCESSING）
     * <p>
     * 状态: PROCESSING<br>
     * 已上传完成，开始执行回调<br>
     * 用途: 测试回调执行流程
     *
     * @return PROCESSING 状态的任务
     */
    public static TaskAggregate processingTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 已完成的任务（COMPLETED）
     * <p>
     * 状态: COMPLETED<br>
     * 所有回调已执行完成<br>
     * 用途: 测试最终状态、查询已完成任务
     *
     * @return COMPLETED 状态的任务
     */
    public static TaskAggregate completedTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 失败的任务（FAILED）
     * <p>
     * 状态: FAILED<br>
     * 错误原因: "Upload failed: network error"<br>
     * 用途: 测试失败处理、错误恢复
     *
     * @return FAILED 状态的任务
     */
    public static TaskAggregate failedTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 已中止的任务（ABORTED）
     * <p>
     * 状态: ABORTED<br>
     * 用户主动中止<br>
     * 用途: 测试任务取消、资源清理
     *
     * @return ABORTED 状态的任务
     */
    public static TaskAggregate abortedTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 已过期的任务（EXPIRED）
     * <p>
     * 状态: EXPIRED<br>
     * 创建时间: 25小时前（超过24小时过期时间）<br>
     * 用途: 测试任务过期清理
     *
     * @return EXPIRED 状态的任务
     */
    public static TaskAggregate expiredTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 带回调配置的任务
     * <p>
     * Callbacks: [thumbnail, hash-verify, notify]<br>
     * 用途: 测试回调链执行
     *
     * @return 带3个回调的任务
     */
    public static TaskAggregate taskWithCallbacks() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 带分片信息的任务
     * <p>
     * 状态: IN_PROGRESS<br>
     * 分片: 5个，每个5MB<br>
     * 用途: 测试多段上传、分片管理
     *
     * @return 带5个分片的任务
     */
    public static TaskAggregate taskWithParts() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 大文件任务（需分片上传）
     * <p>
     * 文件大小: 100MB<br>
     * 分片数: 20个（5MB/片）<br>
     * 用途: 测试大文件上传
     *
     * @return 100MB 文件的任务
     */
    public static TaskAggregate largeFileTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 小文件任务（同步上传）
     * <p>
     * 文件大小: 1MB<br>
     * 用途: 测试同步上传流程
     *
     * @return 1MB 文件的任务
     */
    public static TaskAggregate smallFileTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 即将过期的任务
     * <p>
     * 过期时间: 1小时<br>
     * 创建时间: 50分钟前<br>
     * 用途: 测试过期预警、自动清理触发
     *
     * @return 即将过期的任务
     */
    public static TaskAggregate expiringTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 创建指定状态的任务
     * <p>
     * 通用方法，用于需要特定状态但不需要其他场景设置的测试
     *
     * @param status 目标状态
     * @return 指定状态的任务
     */
    public static TaskAggregate taskWithStatus(TaskStatus status) {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }
}
