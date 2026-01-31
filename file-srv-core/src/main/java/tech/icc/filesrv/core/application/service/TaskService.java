package tech.icc.filesrv.core.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.icc.filesrv.common.exception.validation.InvalidTaskIdException;
import tech.icc.filesrv.common.exception.validation.TaskNotFoundException;
import tech.icc.filesrv.common.vo.task.*;
import tech.icc.filesrv.core.application.service.dto.PartETagDto;
import tech.icc.filesrv.core.application.service.dto.TaskInfoDto;
import tech.icc.filesrv.core.domain.events.TaskCompletedEvent;
import tech.icc.filesrv.core.domain.events.TaskFailedEvent;
import tech.icc.filesrv.core.domain.tasks.*;
import tech.icc.filesrv.core.infra.cache.TaskCacheService;
import tech.icc.filesrv.core.infra.cache.TaskIdValidator;
import tech.icc.filesrv.core.infra.event.TaskEventPublisher;
import tech.icc.filesrv.core.infra.executor.CallbackTaskPublisher;
import tech.icc.filesrv.core.infra.file.LocalFileManager;
import tech.icc.filesrv.common.exception.PluginNotFoundException;
import tech.icc.filesrv.core.infra.plugin.PluginRegistry;
import tech.icc.filesrv.common.spi.storage.PartETagInfo;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;
import tech.icc.filesrv.common.spi.storage.UploadSession;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 任务服务
 * <p>
 * 处理异步上传任务的完整生命周期：创建 → 分片上传 → 完成/中止 → 状态查询。
 * 使用应用层 DTO（TaskInfoDto、PartETagDto），不依赖 API 层类型。
 * <p>
 * Callback 执行已迁移到 executor 模块，本服务只负责发布任务消息。
 * <p>
 * 集成缓存和防护机制：
 * <ul>
 *   <li>布隆过滤器快速过滤不存在的 taskId</li>
 *   <li>本地缓存减少数据库访问</li>
 *   <li>空值缓存防止缓存穿透</li>
 * </ul>
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final Duration DEFAULT_EXPIRE_AFTER = Duration.ofHours(24);

    private final TaskRepository taskRepository;
    private final StorageAdapter storageAdapter;
    private final PluginRegistry pluginRegistry;
    private final TaskEventPublisher eventPublisher;
    private final CallbackTaskPublisher callbackPublisher;
    private final LocalFileManager localFileManager;
    private final TaskCacheService cacheService;
    private final TaskIdValidator idValidator;

    public TaskService(TaskRepository taskRepository,
                       StorageAdapter storageAdapter,
                       PluginRegistry pluginRegistry,
                       TaskEventPublisher eventPublisher,
                       CallbackTaskPublisher callbackPublisher,
                       LocalFileManager localFileManager,
                       TaskCacheService cacheService,
                       TaskIdValidator idValidator) {
        this.taskRepository = taskRepository;
        this.storageAdapter = storageAdapter;
        this.pluginRegistry = pluginRegistry;
        this.eventPublisher = eventPublisher;
        this.callbackPublisher = callbackPublisher;
        this.localFileManager = localFileManager;
        this.cacheService = cacheService;
        this.idValidator = idValidator;
    }

    // ==================== 命令操作 ====================

    /**
     * 创建上传任务
     *
     * @param request   文件请求信息
     * @param cfgs      回调配置
     * @return Pending 状态任务信息
     */
    @Transactional
    public TaskInfoDto.Pending createTask(FileRequest request, List<CallbackConfig> cfgs) {
        // 验证所有 callback 插件都存在
        validateCallbacks(cfgs);

        // 创建任务聚合
        String fKey = generateFKey(request);
        TaskAggregate task = TaskAggregate.create(fKey, cfgs, DEFAULT_EXPIRE_AFTER);

        // 生成存储路径并开始上传会话
        String storagePath = buildStoragePath(fKey, request.contentType());
        UploadSession session = storageAdapter.beginUpload(storagePath, request.contentType());

        // 记录会话信息
        task.startUpload(session.getSessionId(), getNodeId());

        // 保存任务
        taskRepository.save(task);

        // 注册到布隆过滤器并缓存
        idValidator.register(task.getTaskId());
        cacheService.cacheTask(task);

        log.info("Task created: taskId={}, fKey={}, callbacks={}", task.getTaskId(), fKey, cfgs.stream().map(CallbackConfig::name).reduce(",", (a, b) -> a + b));

        return TaskInfoDto.Pending.builder()
                .summary(toSummary(task))
                .request(request)
                .uploadUrl(null)  // 服务端中转，不需要预签名 URL
                .partUploadUrls(null)
                .build();
    }

    /**
     * 上传分片
     *
     * @param taskId     任务标识
     * @param partNumber 分片序号（1-based）
     * @param content    分片内容流
     * @param size       分片大小
     * @return 分片 ETag
     */
    @Transactional
    public PartETagDto uploadPart(String taskId, int partNumber, InputStream content, long size) {
        TaskAggregate task = getTaskOrThrow(taskId);

        // 恢复上传会话（支持降级：如果不支持断点续传，则重新初始化）
        String storagePath = buildStoragePath(task.getFKey(), task.getContentType());
        UploadSession session;
        try {
            session = storageAdapter.resumeUpload(storagePath, task.getSessionId());
        } catch (UnsupportedOperationException e) {
            // 降级：存储层不支持断点续传，重新初始化上传会话
            log.debug("Resume upload not supported, initiating new session: taskId={}", taskId);
            session = storageAdapter.beginUpload(storagePath, task.getContentType());
            // 更新任务的 sessionId
            task.updateSessionId(session.getSessionId());
            taskRepository.save(task);
        }

        try {
            // 上传分片
            String etag = session.uploadPart(partNumber, content, size);

            // 记录分片信息
            PartInfo partInfo = PartInfo.of(partNumber, etag, size);
            task.recordPart(partInfo);
            taskRepository.save(task);
            updateTaskCache(task);

            log.debug("Part uploaded: taskId={}, partNumber={}, etag={}", taskId, partNumber, etag);

            return new PartETagDto(partNumber, etag);
        } finally {
            session.close();
        }
    }

    /**
     * 完成上传，触发 callback 处理
     *
     * @param taskId      任务标识
     * @param parts       已上传分片的 ETag 列表
     * @param hash        文件哈希
     * @param totalSize   文件总大小
     * @param contentType MIME 类型
     * @param filename    文件名
     * @return 完成后的任务信息
     */
    @Transactional
    public TaskInfoDto completeUpload(String taskId, List<PartETagDto> parts,
                                      String hash, Long totalSize,
                                      String contentType, String filename) {
        TaskAggregate task = getTaskOrThrow(taskId);

        // 转换分片信息
        List<PartInfo> partInfos = parts.stream()
                .map(p -> PartInfo.of(p.partNumber(), p.eTag(), 0))
                .toList();

        // 恢复会话并完成上传（支持降级：如果不支持断点续传，则重新初始化）
        String storagePath = buildStoragePath(task.getFKey(), contentType);
        UploadSession session;
        try {
            session = storageAdapter.resumeUpload(storagePath, task.getSessionId());
        } catch (UnsupportedOperationException e) {
            // 降级：存储层不支持断点续传，重新初始化上传会话
            log.debug("Resume upload not supported for complete, initiating new session: taskId={}", taskId);
            session = storageAdapter.beginUpload(storagePath, contentType);
            // 更新任务的 sessionId
            task.updateSessionId(session.getSessionId());
            taskRepository.save(task);
        }

        try {
            // 转换为 SPI 层的 PartETagInfo
            List<PartETagInfo> partEtags = partInfos.stream()
                    .map(p -> PartETagInfo.of(p.partNumber(), p.etag()))
                    .toList();
            String finalPath = session.complete(partEtags);

            // 更新任务状态
            task.completeUpload(finalPath, hash, totalSize, contentType, filename);
            taskRepository.save(task);
            updateTaskCache(task);

            log.info("Upload completed: taskId={}, path={}", taskId, finalPath);

            // 发布 callback 任务到 Kafka（异步执行）
            if (task.getStatus() == TaskStatus.PROCESSING && task.hasCallbacks()) {
                callbackPublisher.publish(taskId);
                log.info("Callback task published: taskId={}", taskId);
            } else if (task.getStatus() == TaskStatus.COMPLETED) {
                // 无 callback，已直接完成，发布完成事件
                publishCompletedEvent(task);
            }

            return toDto(task);
        } catch (Exception e) {
            log.error("Complete upload failed: taskId={}", taskId, e);
            task.markFailed(e.getMessage());
            taskRepository.save(task);
            updateTaskCache(task);
            publishFailedEvent(task);
            throw e;
        } finally {
            session.close();
        }
    }

    /**
     * 完成上传（简化版本）
     */
    @Transactional
    public void completeUpload(String taskId, List<PartETagDto> parts) {
        TaskAggregate task = getTaskOrThrow(taskId);
        // 使用已记录的分片信息
        List<PartInfo> partInfos = task.getSortedParts();
        long totalSize = partInfos.stream().mapToLong(PartInfo::size).sum();

        completeUpload(taskId, parts, null, totalSize, task.getContentType(), task.getFilename());
    }

    /**
     * 中止上传
     *
     * @param taskId 任务标识
     * @param reason 中止原因（可选）
     */
    @Transactional
    public void abortUpload(String taskId, String reason) {
        TaskAggregate task = getTaskOrThrow(taskId);

        // 如果有会话，中止存储层上传
        if (task.getSessionId() != null) {
            try {
                String storagePath = buildStoragePath(task.getFKey(), task.getContentType());
                UploadSession session;
                try {
                    session = storageAdapter.resumeUpload(storagePath, task.getSessionId());
                } catch (UnsupportedOperationException e) {
                    // 降级：存储层不支持断点续传，无需中止会话
                    log.debug("Resume upload not supported for abort, skipping session abort: taskId={}", taskId);
                    return;
                }
                session.abort();
                session.close();
            } catch (Exception e) {
                log.warn("Failed to abort storage session: taskId={}", taskId, e);
            }
        }

        // 清理本地文件
        localFileManager.cleanup(taskId);

        // 更新状态
        task.abort();
        taskRepository.save(task);
        updateTaskCache(task);

        log.info("Task aborted: taskId={}, reason={}", taskId, reason);

        // 发布事件
        publishFailedEvent(task);
    }

    // ==================== 查询操作 ====================

    /**
     * 获取任务详情（轮询接口）
     *
     * @param taskId 任务标识
     * @return 对应状态的任务信息
     */
    public TaskInfoDto getTask(String taskId) {
        TaskAggregate task = getTaskOrThrow(taskId);
        return toDto(task);
    }

    /**
     * 查询任务列表（管理接口）
     *
     * @param status   状态过滤（可选，null 表示全部）
     * @param pageable 分页参数
     * @return 任务摘要分页列表
     */
    public Page<TaskSummary> listTasks(TaskStatus status, Pageable pageable) {
        Page<TaskAggregate> tasks = taskRepository.findByStatus(status, pageable);
        return tasks.map(this::toSummary);
    }

    // ==================== 事件发布 ====================

    /**
     * 发布完成事件（用于无 callback 的任务）
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

    private void publishFailedEvent(TaskAggregate task) {
        TaskFailedEvent event;
        switch (task.getStatus()) {
            case ABORTED -> event = TaskFailedEvent.aborted(task.getTaskId(), task.getFKey());
            case EXPIRED -> event = TaskFailedEvent.expired(task.getTaskId(), task.getFKey());
            default -> event = TaskFailedEvent.callbackFailed(
                    task.getTaskId(),
                    task.getFKey(),
                    task.getFailureReason(),
                    task.getCurrentCallbackIndex()
            );
        }
        eventPublisher.publishFailed(event);
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取任务（带缓存和防护）
     */
    private TaskAggregate getTaskOrThrow(String taskId) {
        // 1. 格式校验
        if (!idValidator.isValidFormat(taskId)) {
            throw new InvalidTaskIdException(taskId);
        }

        // 2. 布隆过滤器快速检查
        if (!idValidator.mightExist(taskId)) {
            log.debug("Task not in bloom filter: taskId={}", taskId);
            throw new TaskNotFoundException(taskId);
        }

        // 3. 检查空值缓存（防止缓存穿透）
        if (cacheService.isNullCached(taskId)) {
            log.debug("Task is null-cached: taskId={}", taskId);
            throw new TaskNotFoundException(taskId);
        }

        // 4. 缓存查询
        Optional<TaskAggregate> cached = cacheService.getTask(taskId);
        if (cached.isPresent()) {
            log.debug("Task cache hit: taskId={}", taskId);
            return cached.get();
        }

        // 5. 数据库查询
        log.debug("Task cache miss, querying database: taskId={}", taskId);
        TaskAggregate task = taskRepository.findByTaskId(taskId)
                .orElseGet(() -> {
                    // 缓存空值，防止重复穿透
                    cacheService.cacheNull(taskId);
                    return null;
                });

        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }

        // 6. 回填缓存
        cacheService.cacheTask(task);

        return task;
    }

    /**
     * 获取任务并更新缓存（用于写操作后）
     */
    private void updateTaskCache(TaskAggregate task) {
        cacheService.cacheTask(task);
    }

    /**
     * 失效任务缓存
     */
    private void evictTaskCache(String taskId) {
        cacheService.evictTask(taskId);
    }

    private void validateCallbacks(List<CallbackConfig> cfgs) {
        if (cfgs == null || cfgs.isEmpty()) {
            return;
        }
        for (CallbackConfig cfg : cfgs) {
            String trimmed = cfg.name().trim();
            if (!trimmed.isEmpty() && !pluginRegistry.hasPlugin(trimmed)) {
                throw new PluginNotFoundException(trimmed);
            }
        }
    }

    private String generateFKey(FileRequest request) {
        // 简单实现：使用 UUID
        return java.util.UUID.randomUUID().toString();
    }

    private String buildStoragePath(String fKey, String contentType) {
        // 格式: {hash前2位}/{hash前4位}/{fKey}
        String prefix = fKey.substring(0, 2) + "/" + fKey.substring(0, 4) + "/";
        return prefix + fKey;
    }

    private String getNodeId() {
        // 单节点场景返回固定值
        return "primary";
    }

    private TaskSummary toSummary(TaskAggregate task) {
        return TaskSummary.builder()
                .taskId(task.getTaskId())
                .uploadId(task.getSessionId())
                .createdAt(task.getCreatedAt())
                .expiresAt(task.getExpiresAt())
                .build();
    }

    private TaskInfoDto toDto(TaskAggregate task) {
        TaskSummary summary = toSummary(task);
        FileRequest request = FileRequest.builder()
                .filename(task.getFilename())
                .contentType(task.getContentType())
                .size(task.getTotalSize())
                .eTag(null)  // TODO: 从 task 中获取 eTag
                .build();

        return switch (task.getStatus()) {
            case PENDING -> TaskInfoDto.Pending.builder()
                    .summary(summary)
                    .request(request)
                    .build();

            case IN_PROGRESS, PROCESSING -> TaskInfoDto.InProgress.builder()
                    .summary(summary)
                    .request(request)
                    .progress(toProgress(task))
                    .build();

            case COMPLETED -> TaskInfoDto.Completed.builder()
                    .summary(summary)
                    .derivedFiles(task.getContext().getDerivedFiles())
                    .build();

            case FAILED -> TaskInfoDto.Failed.builder()
                    .summary(summary)
                    .request(request)
                    .progress(toProgress(task))
                    .failure(FailureDetail.builder()
                            .errorCode("TASK_FAILED")
                            .errorMessage(task.getFailureReason())
                            .build())
                    .build();

            case ABORTED -> TaskInfoDto.Aborted.builder()
                    .summary(summary)
                    .request(request)
                    .abortedAt(task.getCompletedAt())
                    .build();

            case EXPIRED -> TaskInfoDto.Failed.builder()
                    .summary(summary)
                    .request(request)
                    .failure(FailureDetail.builder()
                            .errorCode("TASK_EXPIRED")
                            .errorMessage("Task expired")
                            .build())
                    .build();
        };
    }

    private UploadProgress toProgress(TaskAggregate task) {
        List<PartInfo> parts = task.getParts();
        long uploadedSize = parts.stream().mapToLong(PartInfo::size).sum();
        
        // 转换为 UploadProgress.PartInfo 列表
        List<UploadProgress.PartInfo> progressParts = parts.stream()
                .map(p -> UploadProgress.PartInfo.builder()
                        .partNumber(p.partNumber())
                        .size(p.size())
                        .eTag(p.etag())
                        .build())
                .toList();
        
        return UploadProgress.builder()
                .uploadedParts(parts.size())
                .totalParts(0)  // 未知
                .uploadedBytes(uploadedSize)
                .parts(progressParts)
                .build();
    }
}
