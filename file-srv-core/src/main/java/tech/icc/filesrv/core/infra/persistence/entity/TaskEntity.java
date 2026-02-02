package tech.icc.filesrv.core.infra.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.vo.task.CallbackConfig;
import tech.icc.filesrv.core.domain.tasks.PartInfo;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.common.vo.task.TaskStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务 JPA 实体
 * <p>
 * 对应表：upload_task
 */
@Entity
@Table(name = "upload_task", indexes = {
        @Index(name = "idx_fkey", columnList = "f_key"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEntity {

    @Id
    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "f_key", nullable = false, length = 128)
    private String fKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TaskStatus status;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Column(name = "storage_path", length = 512)
    private String storagePath;

    @Column(name = "hash", length = 64)
    private String hash;

    @Column(name = "total_size")
    private Long totalSize;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "filename", length = 255)
    private String filename;

    /**
     * 分片信息，存储为 JSON
     * <p>
     * 使用 Hibernate 6+ 的 JSON 支持
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parts", columnDefinition = "json")
    private List<PartInfo> parts;

    /**
     * Callback 配置，存储为 JSON
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "callbacks", columnDefinition = "json")
    private List<CallbackConfig> callbacks;

    @Column(name = "current_callback_index")
    private Integer currentCallbackIndex;

    /**
     * 任务上下文，存储为 JSON
     * <p>
     * 注意：TaskContext 的内部 Map 需要能够序列化为 JSON
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", columnDefinition = "json")
    private Map<String, Object> context;

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * 乐观锁版本号
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * 从领域对象转换
     */
    public static TaskEntity fromDomain(TaskAggregate task) {
        return TaskEntity.builder()
                .taskId(task.getTaskId())
                .fKey(task.getFKey())
                .status(task.getStatus())
                .nodeId(task.getNodeId())
                .sessionId(task.getSessionId())
                .storagePath(task.getStoragePath())
                .hash(task.getHash())
                .totalSize(task.getTotalSize())
                .contentType(task.getContentType())
                .filename(task.getFilename())
                .parts(new ArrayList<>(task.getParts()))
                .callbacks(new ArrayList<>(task.getCallbacks()))
                .currentCallbackIndex(task.getCurrentCallbackIndex())
                .context(task.getContext() != null ? task.getContext().toMap() : null)
                .failureReason(task.getFailureReason())
                .createdAt(task.getCreatedAt())
                .expiresAt(task.getExpiresAt())
                .completedAt(task.getCompletedAt())
                .version(task.getVersion())
                .build();
    }

    /**
     * 转换为领域对象
     */
    public TaskAggregate toDomain() {
        TaskAggregate task = new TaskAggregate();

        task.setTaskId(taskId);
        task.setFKey(fKey);
        task.setStatus(status);
        task.setNodeId(nodeId);
        task.setSessionId(sessionId);
        task.setStoragePath(storagePath);
        task.setHash(hash);
        task.setTotalSize(totalSize);
        task.setContentType(contentType);
        task.setFilename(filename);
        task.setParts(parts != null ? new ArrayList<>(parts) : new ArrayList<>());
        task.setCallbacks(callbacks != null ? new ArrayList<>(callbacks) : new ArrayList<>());
        task.setCurrentCallbackIndex(currentCallbackIndex != null ? currentCallbackIndex : 0);
        task.setContext(context != null ? new TaskContext(context) : new TaskContext());
        task.setFailureReason(failureReason);
        task.setCreatedAt(createdAt);
        task.setExpiresAt(expiresAt);
        task.setCompletedAt(completedAt);
        task.setVersion(version);

        return task;
    }
}
