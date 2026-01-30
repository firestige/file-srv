package tech.icc.filesrv.core.testdata;

import net.datafaker.Faker;
import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.vo.audit.AuditInfo;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.task.CallbackConfig;
import tech.icc.filesrv.core.domain.files.FileReference;
import tech.icc.filesrv.core.domain.tasks.PartInfo;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.domain.tasks.TaskStatus;
import tech.icc.filesrv.core.infra.persistence.entity.FileReferenceEntity;
import tech.icc.filesrv.core.infra.persistence.entity.FileInfoEntity;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 测试数据构建器工具类
 * <p>
 * 提供流畅的 Builder API 用于创建测试数据，所有对象都有合理的默认值。
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 使用默认值
 * TaskAggregate task = TestDataBuilders.aTask().build();
 * 
 * // 自定义字段
 * TaskAggregate task = TestDataBuilders.aTask()
 *     .withFKey("custom-fkey")
 *     .withStatus(TaskStatus.IN_PROGRESS)
 *     .build();
 * 
 * // 使用 Faker 生成随机数据
 * FileReference ref = TestDataBuilders.aFileReference()
 *     .withRandomFilename()
 *     .withRandomContentType()
 *     .build();
 * }</pre>
 */
public class TestDataBuilders {

    private static final Faker faker = new Faker(new java.util.Random(42));

    // ==================== Domain Builders ====================

    /**
     * TaskAggregate Builder
     */
    public static TaskAggregateBuilder aTask() {
        return new TaskAggregateBuilder();
    }

    public static class TaskAggregateBuilder {
        private String fKey = "test-fkey-" + UUID.randomUUID();
        private List<CallbackConfig> callbacks = new ArrayList<>();
        private Duration expireAfter = Duration.ofHours(24);
        private TaskStatus status = null; // 让 create() 决定
        private String nodeId = null;
        private String sessionId = null;
        private String storagePath = null;
        private String hash = null;
        private Long totalSize = null;
        private String contentType = null;
        private String filename = null;
        private List<PartInfo> parts = new ArrayList<>();

        public TaskAggregateBuilder withFKey(String fKey) {
            this.fKey = fKey;
            return this;
        }

        public TaskAggregateBuilder withCallbacks(List<CallbackConfig> callbacks) {
            this.callbacks = callbacks;
            return this;
        }

        public TaskAggregateBuilder withCallback(CallbackConfig callback) {
            this.callbacks.add(callback);
            return this;
        }

        public TaskAggregateBuilder withExpireAfter(Duration expireAfter) {
            this.expireAfter = expireAfter;
            return this;
        }

        public TaskAggregateBuilder withStatus(TaskStatus status) {
            this.status = status;
            return this;
        }

        public TaskAggregateBuilder withNodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public TaskAggregateBuilder withSessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public TaskAggregateBuilder withStoragePath(String storagePath) {
            this.storagePath = storagePath;
            return this;
        }

        public TaskAggregateBuilder withHash(String hash) {
            this.hash = hash;
            return this;
        }

        public TaskAggregateBuilder withTotalSize(Long totalSize) {
            this.totalSize = totalSize;
            return this;
        }

        public TaskAggregateBuilder withContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public TaskAggregateBuilder withFilename(String filename) {
            this.filename = filename;
            return this;
        }

        public TaskAggregateBuilder withPart(PartInfo part) {
            this.parts.add(part);
            return this;
        }

        public TaskAggregateBuilder withParts(List<PartInfo> parts) {
            this.parts = new ArrayList<>(parts);
            return this;
        }

        public TaskAggregateBuilder withRandomFilename() {
            this.filename = faker.file().fileName();
            return this;
        }

        public TaskAggregateBuilder withRandomContentType() {
            this.contentType = faker.file().mimeType();
            return this;
        }

        public TaskAggregateBuilder withRandomSize() {
            this.totalSize = faker.number().numberBetween(1024L, 100 * 1024 * 1024L);
            return this;
        }

        public TaskAggregate build() {
            TaskAggregate task = TaskAggregate.create(fKey, callbacks, expireAfter);

            // 设置状态（如果指定）
            if (status != null && status != TaskStatus.PENDING) {
                if (status == TaskStatus.IN_PROGRESS) {
                    task.startUpload(sessionId != null ? sessionId : "session-" + UUID.randomUUID(), 
                                    nodeId != null ? nodeId : "node-1");
                } else if (status == TaskStatus.PROCESSING) {
                    task.startUpload("session-" + UUID.randomUUID(), "node-1");
                    // 添加所有分片
                    if (!parts.isEmpty()) {
                        parts.forEach(part -> task.recordPart(part.partNumber(), part.etag(), part.size()));
                    }
                    task.startProcessing(storagePath != null ? storagePath : "/test/path", 
                                        hash != null ? hash : "test-hash", 
                                        totalSize != null ? totalSize : 1024L);
                }
            }

            // 设置文件元数据（如果指定）
            if (filename != null) {
                task.getContext().put(TaskContext.KEY_FILENAME, filename);
            }
            if (contentType != null) {
                task.getContext().put(TaskContext.KEY_CONTENT_TYPE, contentType);
            }

            return task;
        }
    }

    /**
     * PartInfo Builder
     */
    public static PartInfoBuilder aPart() {
        return new PartInfoBuilder();
    }

    public static class PartInfoBuilder {
        private int partNumber = 1;
        private String etag = "etag-" + UUID.randomUUID();
        private long size = 1024L;

        public PartInfoBuilder withPartNumber(int partNumber) {
            this.partNumber = partNumber;
            return this;
        }

        public PartInfoBuilder withEtag(String etag) {
            this.etag = etag;
            return this;
        }

        public PartInfoBuilder withSize(long size) {
            this.size = size;
            return this;
        }

        public PartInfoBuilder withRandomSize() {
            this.size = faker.number().numberBetween(1024L, 10 * 1024 * 1024L);
            return this;
        }

        public PartInfo build() {
            return new PartInfo(partNumber, etag, size);
        }
    }

    /**
     * FileReference Builder
     */
    public static FileReferenceBuilder aFileReference() {
        return new FileReferenceBuilder();
    }

    public static class FileReferenceBuilder {
        private String fKey = UUID.randomUUID().toString();
        private String contentHash = null;
        private String filename = "test-file.txt";
        private String contentType = "text/plain";
        private Long size = 1024L;
        private String eTag = null;
        private OwnerInfo owner = new OwnerInfo("user-123", "Test User");
        private AccessControl access = new AccessControl(false);
        private AuditInfo audit = new AuditInfo(OffsetDateTime.now(), OffsetDateTime.now());

        public FileReferenceBuilder withFKey(String fKey) {
            this.fKey = fKey;
            return this;
        }

        public FileReferenceBuilder withContentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public FileReferenceBuilder withFilename(String filename) {
            this.filename = filename;
            return this;
        }

        public FileReferenceBuilder withContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public FileReferenceBuilder withSize(Long size) {
            this.size = size;
            return this;
        }

        public FileReferenceBuilder withETag(String eTag) {
            this.eTag = eTag;
            return this;
        }

        public FileReferenceBuilder withOwner(OwnerInfo owner) {
            this.owner = owner;
            return this;
        }

        public FileReferenceBuilder withAccess(AccessControl access) {
            this.access = access;
            return this;
        }

        public FileReferenceBuilder withPublicAccess() {
            this.access = new AccessControl(true);
            return this;
        }

        public FileReferenceBuilder withRandomFilename() {
            this.filename = faker.file().fileName();
            return this;
        }

        public FileReferenceBuilder withRandomContentType() {
            this.contentType = faker.file().mimeType();
            return this;
        }

        public FileReferenceBuilder withRandomSize() {
            this.size = faker.number().numberBetween(1024L, 100 * 1024 * 1024L);
            return this;
        }

        public FileReference build() {
            return new FileReference(fKey, contentHash, filename, contentType, 
                                   size, eTag, owner, access, audit);
        }
    }

    /**
     * CallbackConfig Builder
     */
    public static CallbackConfigBuilder aCallback() {
        return new CallbackConfigBuilder();
    }

    public static class CallbackConfigBuilder {
        private String pluginName = "test-plugin";
        private Map<String, Object> params = new HashMap<>();

        public CallbackConfigBuilder withPluginName(String pluginName) {
            this.pluginName = pluginName;
            return this;
        }

        public CallbackConfigBuilder withParam(String key, Object value) {
            this.params.put(key, value);
            return this;
        }

        public CallbackConfigBuilder withParams(Map<String, Object> params) {
            this.params = new HashMap<>(params);
            return this;
        }

        public CallbackConfig build() {
            return new CallbackConfig(pluginName, params);
        }
    }

    // ==================== Entity Builders ====================

    /**
     * FileReferenceEntity Builder
     */
    public static FileReferenceEntityBuilder aFileReferenceEntity() {
        return new FileReferenceEntityBuilder();
    }

    public static class FileReferenceEntityBuilder {
        private String fKey = UUID.randomUUID().toString();
        private String contentHash = "hash-" + UUID.randomUUID();
        private String filename = "test-file.txt";
        private String contentType = "text/plain";
        private Long size = 1024L;
        private String eTag = "etag-" + UUID.randomUUID();
        private String ownerId = "user-123";
        private String ownerName = "Test User";
        private Boolean isPublic = false;
        private OffsetDateTime createdAt = OffsetDateTime.now();
        private OffsetDateTime updatedAt = OffsetDateTime.now();

        public FileReferenceEntityBuilder withFKey(String fKey) {
            this.fKey = fKey;
            return this;
        }

        public FileReferenceEntityBuilder withContentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public FileReferenceEntityBuilder withFilename(String filename) {
            this.filename = filename;
            return this;
        }

        public FileReferenceEntityBuilder withRandomFilename() {
            this.filename = faker.file().fileName();
            return this;
        }

        public FileReferenceEntity build() {
            return FileReferenceEntity.builder()
                    .fKey(fKey)
                    .contentHash(contentHash)
                    .filename(filename)
                    .contentType(contentType)
                    .size(size)
                    .eTag(eTag)
                    .ownerId(ownerId)
                    .ownerName(ownerName)
                    .isPublic(isPublic)
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .build();
        }
    }

    /**
     * FileInfoEntity Builder
     */
    public static FileInfoEntityBuilder aFileInfoEntity() {
        return new FileInfoEntityBuilder();
    }

    public static class FileInfoEntityBuilder {
        private String contentHash = "hash-" + UUID.randomUUID();
        private Long size = 1024L;
        private String contentType = "text/plain";
        private Integer refCount = 1;
        private Instant createdAt = Instant.now();

        public FileInfoEntityBuilder withContentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public FileInfoEntityBuilder withSize(Long size) {
            this.size = size;
            return this;
        }

        public FileInfoEntityBuilder withRefCount(Integer refCount) {
            this.refCount = refCount;
            return this;
        }

        public FileInfoEntity build() {
            return FileInfoEntity.builder()
                    .contentHash(contentHash)
                    .size(size)
                    .contentType(contentType)
                    .refCount(refCount)
                    .createdAt(createdAt)
                    .copies(new ArrayList<>())
                    .build();
        }
    }

    // ==================== 便捷批量创建方法 ====================

    /**
     * 创建多个 Part
     */
    public static List<PartInfo> createParts(int count) {
        List<PartInfo> parts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            parts.add(aPart()
                    .withPartNumber(i)
                    .withRandomSize()
                    .build());
        }
        return parts;
    }

    /**
     * 创建带分片的任务
     */
    public static TaskAggregate createTaskWithParts(int partCount) {
        return aTask()
                .withParts(createParts(partCount))
                .build();
    }

    /**
     * 创建处于 IN_PROGRESS 状态的任务
     */
    public static TaskAggregate createInProgressTask() {
        return aTask()
                .withStatus(TaskStatus.IN_PROGRESS)
                .withSessionId("session-test")
                .withNodeId("node-1")
                .build();
    }
}
