package tech.icc.filesrv.core.infra.executor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginInvoker;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.vo.file.FileMetadataUpdate;
import tech.icc.filesrv.common.vo.task.CallbackConfig;
import tech.icc.filesrv.common.vo.task.DerivedFile;
import tech.icc.filesrv.core.application.service.FileService;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.domain.tasks.TaskRepository;
import tech.icc.filesrv.core.infra.executor.CallbackChainRunner;
import tech.icc.filesrv.common.config.ExecutorProperties;
import tech.icc.filesrv.core.infra.executor.exception.CallbackExecutionException;
import tech.icc.filesrv.core.infra.executor.exception.CallbackTimeoutException;
import tech.icc.filesrv.core.infra.file.LocalFileManager;
import tech.icc.filesrv.core.infra.plugin.PluginRegistry;
import tech.icc.filesrv.common.spi.plugin.PluginStorageService;
import tech.icc.filesrv.common.spi.plugin.PluginStorageServiceAware;
import tech.icc.filesrv.common.domain.events.DerivedFilesAddedEvent;
import tech.icc.filesrv.common.domain.events.TaskCompletedEvent;
import tech.icc.filesrv.common.domain.events.TaskFailedEvent;
import tech.icc.filesrv.common.spi.event.TaskEventPublisher;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 默认 Callback 链执行器实现
 * <p>
 * 在单节点内执行整个 callback 链，支持本地重试。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>从 task.currentCallbackIndex 开始执行（断点恢复）</li>
 *   <li>每个 callback 允许本地重试 maxRetries 次</li>
 *   <li>重试之间有指数退避间隔</li>
 *   <li>只有不可恢复异常才向上抛出</li>
 *   <li>整个链在同一节点完成，避免文件重复下载</li>
 * </ul>
 */
public class DefaultCallbackChainRunner implements CallbackChainRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultCallbackChainRunner.class);
    private final TaskRepository taskRepository;
    private final PluginRegistry pluginRegistry;
    private final LocalFileManager localFileManager;
    private final TaskEventPublisher eventPublisher;
    private final ExecutorService timeoutExecutor;
    private final ExecutorProperties properties;
    private final PluginStorageService pluginStorageService;
    private final FileService fileService;
    
    public DefaultCallbackChainRunner(TaskRepository taskRepository,
                                       PluginRegistry pluginRegistry,
                                       LocalFileManager localFileManager,
                                       TaskEventPublisher eventPublisher,
                                       ExecutorService timeoutExecutor,
                                       ExecutorProperties properties,
                                       PluginStorageService pluginStorageService,
                                       FileService fileService) {
        this.taskRepository = taskRepository;
        this.pluginRegistry = pluginRegistry;
        this.localFileManager = localFileManager;
        this.eventPublisher = eventPublisher;
        this.timeoutExecutor = timeoutExecutor;
        this.properties = properties;
        this.pluginStorageService = pluginStorageService;
        this.fileService = fileService;
    }

    @Override
    @Transactional
    public void run(TaskAggregate task) {
        // IMPORTANT: 使用悲观锁重新加载task，防止并发修改冲突
        // SELECT FOR UPDATE 确保在整个callback链执行期间独占访问此task
        // 这样可以避免：
        // 1. 其他线程（如GET请求）查询并缓存旧版本
        // 2. 多个callback执行器同时修改同一个task
        // 3. 乐观锁version冲突
        final String taskId = task.getTaskId();
        task = taskRepository.findByTaskIdForUpdate(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        
        TaskContext context = task.getContext();

        // 1. 初始化 ExecutionInfo（关键修复：为注解注入提供数据源）
        context.setTaskId(task.getTaskId());
        context.executionInfo().setFileHash(task.getHash());
        context.executionInfo().setContentType(task.getContentType());
        context.executionInfo().setFileSize(task.getTotalSize());
        context.executionInfo().setFilename(task.getFilename());
        context.executionInfo().setStoragePath(task.getStoragePath());

        // 2. 准备本地文件（只下载一次）
        Path localPath = localFileManager.prepareLocalFile(task.getStoragePath(), task.getTaskId());
        context.executionInfo().setLocalFilePath(localPath.toString());

        try {
            List<CallbackConfig> callbacks = task.getCallbacks();
            int startIndex = task.getCurrentCallbackIndex();

            log.info("Starting callback chain: taskId={}, startIndex={}, totalCallbacks={}",
                    task.getTaskId(), startIndex, callbacks.size());

            // 2. 从 startIndex 开始执行
            for (int i = startIndex; i < callbacks.size(); i++) {
                String callbackName = callbacks.get(i).name();
                log.info("Executing callback: taskId={}, callback={}, index={}",
                        task.getTaskId(), callbackName, i);

                // 同步 PluginParamsContext 的当前索引（关键：确保读取正确的插件参数）
                context.pluginParams().setCurrentIndex(i);

                // 3. 执行单个 callback（带本地重试）
                PluginResult result = executeWithLocalRetry(task.getTaskId(), callbackName, context, i);

                // 4. 解释 PluginResult
                task = handleResult(task, callbackName, result, context);
                
                // CRITICAL: 更新 context 引用！
                // handleResult 中调用 save() 返回新的 task 对象，其 context 也是新对象
                // 必须更新引用，否则后续插件的输出会丢失
                context = task.getContext();
            }

            // 5. 应用元数据更新（如果有）
            if (context.hasMetadataUpdates()) {
                FileMetadataUpdate update = context.getMetadataUpdate().orElse(null);
                if (update != null) {
                    log.info("Applying metadata updates from callbacks: taskId={}, fKey={}", 
                            task.getTaskId(), task.getFKey());
                    fileService.applyMetadataUpdate(task.getFKey(), update);
                }
            }

            // 6. 标记完成
            task.markCompleted();
            task = taskRepository.save(task);
            publishCompletedEvent(task);
            log.info("All callbacks completed: taskId={}", task.getTaskId());

        } finally {
            // 6. 清理本地文件
            localFileManager.cleanup(task.getTaskId());
        }
    }

    /**
     * 处理 callback 执行结果
     */
    private TaskAggregate handleResult(TaskAggregate task, String callbackName,
                                       PluginResult result, TaskContext context) {
        if (result instanceof PluginResult.Success success) {
            // 记录执行前的衍生文件数量
            int beforeCount = context.getDerivedFiles().size();
            
            // 合并输出到 context
            context.putAll(success.outputs());
            
            // 检测新增的衍生文件并发布事件
            List<DerivedFile> allDerivedFiles = context.getDerivedFiles();
            if (allDerivedFiles.size() > beforeCount) {
                List<DerivedFile> newDerivedFiles = allDerivedFiles.subList(beforeCount, allDerivedFiles.size());
                publishDerivedFilesAddedEvent(task.getTaskId(), task.getFKey(), newDerivedFiles);
            }
            
            // 推进并持久化（断点恢复关键）
            task.advanceCallback();
            task = taskRepository.save(task);
            log.debug("Callback succeeded: {}", callbackName);
            return task;

        } else if (result instanceof PluginResult.Failure failure) {
            // 标记任务失败，停止链
            task.markFailed("Callback [" + callbackName + "] failed: " + failure.reason());
                task = taskRepository.save(task);
            publishFailedEvent(task);
            log.error("Callback failed: {} - {}", callbackName, failure.reason());
            // 不再继续执行，抛异常终止
            throw new CallbackExecutionException(
                    task.getTaskId(), callbackName,
                    task.getCurrentCallbackIndex(),
                    failure.reason(), false
            );

        } else if (result instanceof PluginResult.Skip skip) {
            // 跳过当前 callback，继续下一个
            log.info("Callback skipped: {} - {}", callbackName, skip.reason());
            task.advanceCallback();
            task = taskRepository.save(task);
            return task;
        }

        return task;
    }

    /**
     * 带本地重试执行单个 callback
     */
    private PluginResult executeWithLocalRetry(String taskId, String callbackName,
                                                TaskContext context, int index) {
        PluginInvoker invoker = pluginRegistry.getPlugin(callbackName);
        
        // 如果 Plugin 实现了 PluginStorageServiceAware，注入存储服务
        Object pluginInstance = invoker.getPluginInstance();
        if (pluginInstance instanceof PluginStorageServiceAware aware) {
            aware.setPluginStorageService(pluginStorageService);
        }
        int maxRetries = properties.retry().maxRetriesPerCallback();
        Duration callbackTimeout = properties.timeout().callback();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                // 重试前等待（指数退避）
                if (attempt > 0) {
                    Duration delay = properties.retry().getBackoff(attempt);
                    log.info("Retry callback: taskId={}, callback={}, attempt={}, delay={}",
                            taskId, callbackName, attempt, delay);
                    Thread.sleep(delay.toMillis());
                }

                // 执行 callback（带超时）
                Future<PluginResult> future = timeoutExecutor.submit(() -> invoker.invoke(context));
                PluginResult result = future.get(callbackTimeout.toMillis(), TimeUnit.MILLISECONDS);

                // 检查是否为可重试的失败
                if (result instanceof PluginResult.Failure failure && failure.retryable()) {
                    log.warn("Callback returned retryable failure: taskId={}, callback={}, attempt={}, reason={}",
                            taskId, callbackName, attempt, failure.reason());

                    if (attempt >= maxRetries) {
                        // 重试耗尽，返回失败
                        return result;
                    }
                    // 继续重试
                    continue;
                }

                return result;

            } catch (TimeoutException e) {
                log.warn("Callback timeout: taskId={}, callback={}, attempt={}",
                        taskId, callbackName, attempt);

                if (attempt >= maxRetries) {
                    throw new CallbackTimeoutException(taskId, callbackName, index);
                }
                // 继续本地重试

            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                boolean retryable = isRetryable(cause);

                log.warn("Callback error: taskId={}, callback={}, attempt={}, retryable={}, error={}",
                        taskId, callbackName, attempt, retryable, cause.getMessage());

                if (!retryable || attempt >= maxRetries) {
                    throw new CallbackExecutionException(taskId, callbackName, index,
                            cause.getMessage(), retryable, cause);
                }
                // 继续本地重试

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CallbackExecutionException(taskId, callbackName, index, "Interrupted", false);
            }
        }

        // 不应该到达这里
        throw new CallbackExecutionException(taskId, callbackName, index, "Unknown error", false);
    }

    /**
     * 判断异常是否可本地重试
     */
    private boolean isRetryable(Throwable cause) {
        // 临时性故障可重试
        if (cause instanceof IOException
                || cause instanceof SocketTimeoutException
                || cause instanceof ConnectException) {
            return true;
        }

        // 业务逻辑错误不可重试
        if (cause instanceof IllegalArgumentException
                || cause instanceof SecurityException
                || cause instanceof NullPointerException) {
            return false;
        }

        // 默认不重试，保守策略
        return false;
    }

    /**
     * 发布完成事件
     */
    private void publishCompletedEvent(TaskAggregate task) {
        TaskCompletedEvent event = TaskCompletedEvent.of(
                task.getTaskId(),
                task.getFKey(),
                task.getStoragePath(),
                task.getHash(),
                task.getTotalSize(),
                task.getContentType(),
                task.getFilename(),
                task.getContext().getDerivedFiles(),
                task.getContext().getPluginOutputs()
        );
        eventPublisher.publishCompleted(event);
    }

    /**
     * 发布失败事件
     */
    private void publishFailedEvent(TaskAggregate task) {
        TaskFailedEvent event = TaskFailedEvent.callbackFailed(
                task.getTaskId(),
                task.getFKey(),
                task.getFailureReason(),
                task.getCurrentCallbackIndex()
        );
        eventPublisher.publishFailed(event);
    }

    /**
     * 发布衍生文件添加事件
     */
    private void publishDerivedFilesAddedEvent(String taskId, String sourceFkey, List<DerivedFile> newDerivedFiles) {
        if (newDerivedFiles == null || newDerivedFiles.isEmpty()) {
            return;
        }
        DerivedFilesAddedEvent event = DerivedFilesAddedEvent.of(taskId, sourceFkey, newDerivedFiles);
        eventPublisher.publishDerivedFilesAdded(event);
        log.debug("Published DerivedFilesAddedEvent: taskId={}, sourceFkey={}, count={}", 
                taskId, sourceFkey, newDerivedFiles.size());
    }
}
