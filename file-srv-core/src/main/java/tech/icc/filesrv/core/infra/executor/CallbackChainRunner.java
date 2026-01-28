package tech.icc.filesrv.core.infra.executor;

import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.infra.executor.exception.CallbackExecutionException;
import tech.icc.filesrv.core.infra.executor.exception.CallbackTimeoutException;

/**
 * Callback 链执行器
 * <p>
 * 职责：
 * <ul>
 *   <li>本地文件准备</li>
 *   <li>逐个执行 callback（支持本地重试）</li>
 *   <li>解释 PluginResult，更新 Task 状态</li>
 *   <li>每步持久化进度（断点恢复）</li>
 *   <li>资源清理</li>
 * </ul>
 */
public interface CallbackChainRunner {

    /**
     * 执行 callback 链
     * <p>
     * 从 task.currentCallbackIndex 开始执行（支持断点恢复），直到：
     * <ul>
     *   <li>全部完成 → task.status = COMPLETED</li>
     *   <li>某个失败 → task.status = FAILED</li>
     *   <li>超时 → 抛出 CallbackTimeoutException</li>
     * </ul>
     * <p>
     * 整个链在当前节点执行，单个 callback 失败会本地重试，
     * 不会将任务迁移到其他节点。
     *
     * @param task 任务聚合（状态为 PROCESSING）
     * @throws CallbackTimeoutException   执行超时（重试耗尽）
     * @throws CallbackExecutionException 执行异常（不可重试）
     */
    void run(TaskAggregate task);
}
