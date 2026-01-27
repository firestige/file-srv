package tech.icc.filesrv.core.domain.tasks;

import tech.icc.filesrv.common.context.TaskContext;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 上传任务聚合根
 * <p>
 * 管理分片上传的完整生命周期，包括：
 * <ul>
 *   <li>任务创建与状态管理</li>
 *   <li>分片记录</li>
 *   <li>Callback 执行进度跟踪</li>
 * </ul>
 */
public class TaskAggregate {

    private String taskId;
    private String fKey;
    private TaskStatus status;

    private String nodeId;
    private String sessionId;
    private String storagePath;

    private String hash;
    private Long totalSize;
    private String contentType;
    private String filename;

    private List<PartInfo> parts;
    private List<String> callbacks;
    private int currentCallbackIndex;

    private TaskContext context;
    private String failureReason;

    private Instant createdAt;
    private Instant expiresAt;
    private Instant completedAt;

    // ==================== 构造 ====================

    protected TaskAggregate() {
        // for JPA
    }

    private TaskAggregate(String taskId, String fKey, List<String> callbacks,
                          Map<String, Object> params, Duration expireAfter) {
        this.taskId = taskId;
        this.fKey = fKey;
        this.status = TaskStatus.PENDING;
        this.callbacks = callbacks != null ? new ArrayList<>(callbacks) : new ArrayList<>();
        this.currentCallbackIndex = 0;
        this.parts = new ArrayList<>();
        this.context = new TaskContext(params);
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plus(expireAfter);
    }

    /**
     * 创建新任务
     *
     * @param fKey        用户文件标识
     * @param callbacks   callback 列表（逗号分隔的字符串）
     * @param params      初始参数
     * @param expireAfter 过期时间
     * @return 新任务
     */
    public static TaskAggregate create(String fKey, String callbacks,
                                        Map<String, Object> params, Duration expireAfter) {
        String taskId = UUID.randomUUID().toString();
        List<String> callbackList = parseCallbacks(callbacks);
        return new TaskAggregate(taskId, fKey, callbackList, params, expireAfter);
    }

    private static List<String> parseCallbacks(String callbacks) {
        if (callbacks == null || callbacks.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(callbacks.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // ==================== 领域行为 ====================

    /**
     * 开始上传（记录 session）
     */
    public void startUpload(String sessionId, String nodeId) {
        assertStatus(TaskStatus.PENDING, "start upload");
        this.sessionId = sessionId;
        this.nodeId = nodeId;
        this.status = TaskStatus.IN_PROGRESS;
    }

    /**
     * 记录分片
     */
    public void recordPart(PartInfo part) {
        assertCanUpload();

        // 检查分片是否已存在
        parts.removeIf(p -> p.partNumber() == part.partNumber());
        parts.add(part);

        // 首次上传分片时切换状态
        if (status == TaskStatus.PENDING) {
            status = TaskStatus.IN_PROGRESS;
        }
    }

    /**
     * 完成上传，进入处理阶段
     */
    public void completeUpload(String storagePath, String hash, Long totalSize,
                                String contentType, String filename) {
        assertStatus(TaskStatus.IN_PROGRESS, "complete upload");

        this.storagePath = storagePath;
        this.hash = hash;
        this.totalSize = totalSize;
        this.contentType = contentType;
        this.filename = filename;

        // 更新 context
        context.put(TaskContext.KEY_STORAGE_PATH, storagePath);
        context.put(TaskContext.KEY_FILE_HASH, hash);
        context.put(TaskContext.KEY_FILE_SIZE, totalSize);
        context.put(TaskContext.KEY_CONTENT_TYPE, contentType);
        context.put(TaskContext.KEY_FILENAME, filename);

        // 如果没有 callback，直接完成
        if (callbacks.isEmpty()) {
            markCompleted();
        } else {
            this.status = TaskStatus.PROCESSING;
        }
    }

    /**
     * 推进 callback 索引
     */
    public void advanceCallback() {
        assertStatus(TaskStatus.PROCESSING, "advance callback");

        currentCallbackIndex++;
        if (currentCallbackIndex >= callbacks.size()) {
            markCompleted();
        }
    }

    /**
     * 标记完成
     */
    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * 标记失败
     */
    public void markFailed(String reason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }

    /**
     * 中止任务
     */
    public void abort() {
        if (!status.allowAbort()) {
            throw new IllegalStateException("Cannot abort task in status: " + status);
        }
        this.status = TaskStatus.ABORTED;
        this.completedAt = Instant.now();
    }

    /**
     * 标记过期
     */
    public void markExpired() {
        if (status.isTerminal()) {
            return; // 已经是终态，忽略
        }
        this.status = TaskStatus.EXPIRED;
        this.completedAt = Instant.now();
    }

    // ==================== 查询方法 ====================

    /**
     * 获取当前要执行的 callback 名称
     */
    public Optional<String> getCurrentCallback() {
        if (status != TaskStatus.PROCESSING) {
            return Optional.empty();
        }
        if (currentCallbackIndex >= callbacks.size()) {
            return Optional.empty();
        }
        return Optional.of(callbacks.get(currentCallbackIndex));
    }

    /**
     * 是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt) && !status.isTerminal();
    }

    /**
     * 获取已上传的分片（按 partNumber 排序）
     */
    public List<PartInfo> getSortedParts() {
        return parts.stream()
                .sorted(Comparator.comparingInt(PartInfo::partNumber))
                .toList();
    }

    // ==================== 断言方法 ====================

    private void assertStatus(TaskStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " in status " + status + ", expected " + expected);
        }
    }

    private void assertCanUpload() {
        if (!status.allowUpload()) {
            throw new IllegalStateException("Cannot upload part in status: " + status);
        }
    }

    // ==================== Getters ====================

    public String getTaskId() {
        return taskId;
    }

    public String getFKey() {
        return fKey;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getHash() {
        return hash;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFilename() {
        return filename;
    }

    public List<PartInfo> getParts() {
        return Collections.unmodifiableList(parts);
    }

    public List<String> getCallbacks() {
        return Collections.unmodifiableList(callbacks);
    }

    public int getCurrentCallbackIndex() {
        return currentCallbackIndex;
    }

    public TaskContext getContext() {
        return context;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    // ==================== Setters (for JPA) ====================

    protected void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    protected void setFKey(String fKey) {
        this.fKey = fKey;
    }

    protected void setStatus(TaskStatus status) {
        this.status = status;
    }

    protected void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    protected void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    protected void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    protected void setHash(String hash) {
        this.hash = hash;
    }

    protected void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    protected void setContentType(String contentType) {
        this.contentType = contentType;
    }

    protected void setFilename(String filename) {
        this.filename = filename;
    }

    protected void setParts(List<PartInfo> parts) {
        this.parts = parts;
    }

    protected void setCallbacks(List<String> callbacks) {
        this.callbacks = callbacks;
    }

    protected void setCurrentCallbackIndex(int currentCallbackIndex) {
        this.currentCallbackIndex = currentCallbackIndex;
    }

    protected void setContext(TaskContext context) {
        this.context = context;
    }

    protected void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    protected void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    protected void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    protected void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
