package tech.icc.filesrv.common.context;

import tech.icc.filesrv.common.vo.file.FileMetadataUpdate;
import tech.icc.filesrv.common.vo.task.CallbackConfig;
import tech.icc.filesrv.common.vo.task.DerivedFile;

import java.util.*;
import java.util.function.Consumer;

/**
 * 任务上下文（分层设计）
 * <p>
 * 内部使用分层 Context 结构管理数据，外部保留兼容 API。
 * <p>
 * 分层结构：
 * <ul>
 *   <li>{@link PluginParamsContext} - 插件参数</li>
 *   <li>{@link ExecutionInfoContext} - 执行信息（只读）</li>
 *   <li>{@link PluginOutputsContext} - 插件输出</li>
 *   <li>{@link FileMetadataContext} - 元数据更新</li>
 *   <li>{@link DerivedFilesContext} - 衍生文件列表</li>
 * </ul>
 */
public class TaskContext {

    // ==================== 内置 Key（保留用于兼容性）====================

    /** 存储层路径 */
    public static final String KEY_STORAGE_PATH = "storagePath";

    /** 本地临时文件路径 */
    public static final String KEY_LOCAL_FILE_PATH = "localFilePath";

    /** 文件哈希 */
    public static final String KEY_FILE_HASH = "fileHash";

    /** MIME 类型 */
    public static final String KEY_CONTENT_TYPE = "contentType";

    /** 文件大小 */
    public static final String KEY_FILE_SIZE = "fileSize";

    /** 原始文件名 */
    public static final String KEY_FILENAME = "filename";

    /** 衍生文件列表 */
    public static final String KEY_DERIVED_FILES = "derivedFiles";

    /** 元数据变更记录 Key（内部使用） */
    public static final String KEY_METADATA_UPDATE = "_metadataUpdate";

    // --- 元数据字段常量（已废弃，保留给旧插件兼容） ---
    /** @deprecated 使用 {@link #updateMetadata(Consumer)} 替代 */
    @Deprecated
    public static final String METADATA_FILENAME = "filename";
    /** @deprecated 使用 {@link #updateMetadata(Consumer)} 替代 */
    @Deprecated
    public static final String METADATA_CONTENT_TYPE = "contentType";
    /** @deprecated 使用 {@link #updateMetadata(Consumer)} 替代 */
    @Deprecated
    public static final String KEY_METADATA_CHANGES = "_metadataChanges";

    // ==================== 分层Context ====================

    /** 插件参数 */
    private final PluginParamsContext pluginParams;

    /** 执行信息 */
    private final ExecutionInfoContext executionInfo;

    /** 插件输出 */
    private final PluginOutputsContext pluginOutputs;

    /** 元数据更新 */
    private final FileMetadataContext fileMetadata;

    /** 衍生文件 */
    private final DerivedFilesContext derivedFiles;

    /** 任务ID（从executionInfo引用，便于访问） */
    private String taskId;

    // ==================== 构造函数 ====================

    public TaskContext() {
        this.pluginParams = new PluginParamsContext();
        this.executionInfo = new ExecutionInfoContext();
        this.pluginOutputs = new PluginOutputsContext();
        this.fileMetadata = new FileMetadataContext();
        this.derivedFiles = new DerivedFilesContext();
    }

    /**
     * 从 callbacks 列表创建（推荐）
     *
     * @param callbacks callback 配置列表
     */
    public TaskContext(List<CallbackConfig> callbacks) {
        this.pluginParams = new PluginParamsContext(callbacks);
        this.executionInfo = new ExecutionInfoContext();
        this.pluginOutputs = new PluginOutputsContext();
        this.fileMetadata = new FileMetadataContext();
        this.derivedFiles = new DerivedFilesContext();
    }

    /**
     * 从旧的 Map 结构迁移（兼容旧代码）
     */
    public TaskContext(Map<String, ?> initialData) {
        this();
        if (initialData != null) {
            migrateFromLegacyData(initialData);
        }
    }

    /**
     * 迁移旧数据到分层结构
     */
    @SuppressWarnings("unchecked")
    private void migrateFromLegacyData(Map<String, ?> data) {
        for (Map.Entry<String, ?> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 执行信息
            if (KEY_STORAGE_PATH.equals(key)) {
                executionInfo.setStoragePath((String) value);
            } else if (KEY_LOCAL_FILE_PATH.equals(key)) {
                executionInfo.setLocalFilePath((String) value);
            } else if (KEY_FILE_HASH.equals(key)) {
                executionInfo.setFileHash((String) value);
            } else if (KEY_CONTENT_TYPE.equals(key)) {
                executionInfo.setContentType((String) value);
            } else if (KEY_FILE_SIZE.equals(key)) {
                executionInfo.setFileSize(((Number) value).longValue());
            } else if (KEY_FILENAME.equals(key)) {
                executionInfo.setFilename((String) value);
            }
            // 衍生文件
            else if (KEY_DERIVED_FILES.equals(key) && value instanceof List) {
                ((List<DerivedFile>) value).forEach(derivedFiles::add);
            }
            // 元数据更新
            else if (KEY_METADATA_UPDATE.equals(key) && value instanceof FileMetadataUpdate) {
                // 已有元数据更新，直接使用
            }
            // 其他键视为插件输出
            else if (!key.startsWith("_")) {
                pluginOutputs.put(key, value);
            }
        }
    }

    // ==================== 分层Context访问器 ====================

    /**
     * 获取插件参数Context
     */
    public PluginParamsContext pluginParams() {
        return pluginParams;
    }

    /**
     * 获取执行信息Context（只读）
     */
    public ExecutionInfoContext executionInfo() {
        return executionInfo;
    }

    /**
     * 获取插件输出Context
     */
    public PluginOutputsContext pluginOutputs() {
        return pluginOutputs;
    }

    /**
     * 获取文件元数据Context
     */
    public FileMetadataContext fileMetadata() {
        return fileMetadata;
    }

    /**
     * 获取衍生文件Context
     */
    public DerivedFilesContext derivedFiles() {
        return derivedFiles;
    }

    // ==================== 任务ID管理 ====================

    /**
     * 设置任务ID（由Runner初始化时调用）
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
        this.executionInfo.setTaskId(taskId);
    }

    /**
     * 获取任务ID
     */
    public Optional<String> getTaskId() {
        return Optional.ofNullable(taskId);
    }

    // ==================== 兼容API：通用读写 ====================

    /**
     * 获取值（兼容旧API）
     * <p>
     * 查找顺序：执行信息 → 插件输出
     */
    public Optional<Object> get(String key) {
        // 先查执行信息
        if (KEY_STORAGE_PATH.equals(key)) {
            return executionInfo.getStoragePath().map(v -> v);
        } else if (KEY_LOCAL_FILE_PATH.equals(key)) {
            return executionInfo.getLocalFilePath().map(v -> v);
        } else if (KEY_FILE_HASH.equals(key)) {
            return executionInfo.getFileHash().map(v -> v);
        } else if (KEY_CONTENT_TYPE.equals(key)) {
            return executionInfo.getContentType().map(v -> v);
        } else if (KEY_FILE_SIZE.equals(key)) {
            return executionInfo.getFileSize().map(v -> v);
        } else if (KEY_FILENAME.equals(key)) {
            return executionInfo.getFilename().map(v -> v);
        }

        // 衍生文件
        if (KEY_DERIVED_FILES.equals(key)) {
            return Optional.of(derivedFiles.getAll());
        }

        // 元数据更新
        if (KEY_METADATA_UPDATE.equals(key)) {
            return fileMetadata.getPrimaryMetadata().map(v -> (Object) v);
        }

        // 最后查插件输出
        return pluginOutputs.get(key);
    }

    /**
     * 获取字符串值
     */
    public Optional<String> getString(String key) {
        return get(key).map(Object::toString);
    }

    /**
     * 获取整数值
     */
    public Optional<Integer> getInt(String key) {
        return get(key).map(v -> {
            if (v instanceof Number n) {
                return n.intValue();
            }
            return Integer.parseInt(v.toString());
        });
    }

    /**
     * 获取长整数值
     */
    public Optional<Long> getLong(String key) {
        return get(key).map(v -> {
            if (v instanceof Number n) {
                return n.longValue();
            }
            return Long.parseLong(v.toString());
        });
    }

    /**
     * 必须获取字符串，不存在则抛异常
     */
    public String requireString(String key) {
        return getString(key)
                .orElseThrow(() -> new IllegalArgumentException("Required key not found: " + key));
    }

    /**
     * 必须获取指定类型的值
     */
    @SuppressWarnings("unchecked")
    public <T> T require(String key, Class<T> type) {
        Object value = get(key).orElse(null);
        if (value == null) {
            throw new IllegalArgumentException("Required key not found: " + key);
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Key " + key + " is not of type " + type.getSimpleName());
        }
        return (T) value;
    }

    // ==================== Plugin 参数读取 ====================

    /**
     * 获取当前 callback 的参数
     * <p>
     * 注意：此方法返回当前正在执行的 callback 的参数（由 currentCallbackIndex 决定）。
     * 如果需要获取其他 callback 的参数，请直接访问 {@link #pluginParams()}。
     *
     * @param paramKey 参数 Key
     * @return 参数值
     */
    public Optional<String> getPluginParam(String paramKey) {
        return pluginParams.get(paramKey);
    }

    /**
     * 获取 Plugin 特定参数（兼容旧 API）
     *
     * @param pluginName 插件名称（已废弃，不再使用）
     * @param paramKey   参数 Key
     * @return 参数值
     * @deprecated 使用 {@link #getPluginParam(String)} 替代
     */
    @Deprecated
    public Optional<Object> getPluginParam(String pluginName, String paramKey) {
        return pluginParams.get(paramKey).map(v -> (Object) v);
    }

    /**
     * 获取 Plugin 字符串参数（兼容旧 API）
     *
     * @deprecated 使用 {@link #getPluginParam(String)} 替代
     */
    @Deprecated
    public Optional<String> getPluginString(String pluginName, String paramKey) {
        return pluginParams.get(paramKey);
    }

    /**
     * 获取 Plugin 整数参数（兼容旧 API）
     *
     * @deprecated 使用 {@link #getPluginParam(String)} 并手动转换
     */
    @Deprecated
    public Optional<Integer> getPluginInt(String pluginName, String paramKey) {
        return pluginParams.get(paramKey).map(Integer::parseInt);
    }

    // ==================== 写入操作 ====================

    /**
     * 设置值（写入插件输出）
     */
    public void put(String key, Object value) {
        pluginOutputs.put(key, value);
    }

    /**
     * 批量设置值
     */
    public void putAll(Map<String, Object> values) {
        if (values != null) {
            pluginOutputs.putAll(values);
        }
    }

    /**
     * 移除值
     */
    public void remove(String key) {
        pluginOutputs.remove(key);
    }

    // ==================== 元数据修改操作 ====================

    /**
     * 更新文件元数据（类型安全 Builder API）
     * <p>
     * 供元数据修改类插件调用，变更会在 callback 链全部成功后应用到 FileReference。
     * <p>
     * 例如 RenamePlugin：
     * <pre>
     * context.updateMetadata(builder -> builder
     *     .filename("new-name.txt")
     *     .tags("processed validated")
     *     .mergeMetadata("processedBy", "RenamePlugin")
     * );
     * </pre>
     *
     * @param updater Builder 消费者
     */
    public void updateMetadata(Consumer<FileMetadataUpdate.FileMetadataUpdateBuilder> updater) {
        fileMetadata.updatePrimaryMetadata(updater);
    }

    /**
     * 是否有元数据变更
     */
    public boolean hasMetadataUpdates() {
        return fileMetadata.getPrimaryMetadata().map(FileMetadataUpdate::hasUpdates).orElse(false);
    }

    /**
     * 获取元数据变更
     */
    public Optional<FileMetadataUpdate> getMetadataUpdate() {
        return fileMetadata.getPrimaryMetadata();
    }

    /**
     * 更新衍生文件的元数据
     * <p>
     * 供需要修改衍生文件元数据的插件使用。
     *
     * @param fileKey 衍生文件的 fileKey
     * @param updater Builder 消费者
     */
    public void updateDerivedFileMetadata(String fileKey, Consumer<FileMetadataUpdate.FileMetadataUpdateBuilder> updater) {
        fileMetadata.updateDerivedFileMetadata(fileKey, updater);
    }

    /**
     * 获取衍生文件的元数据更新
     */
    public Map<String, FileMetadataUpdate> getDerivedFileMetadataUpdates() {
        return fileMetadata.getAllDerivedFileMetadata();
    }

    // --- 旧 API（已废弃，保留给旧插件兼容） ---

    /**
     * @deprecated 使用 {@link #updateMetadata(Consumer)} 替代
     */
    @Deprecated
    public void setMetadata(String field, String value) {
        updateMetadata(builder -> {
            if (METADATA_FILENAME.equals(field)) {
                builder.filename(value);
            } else if (METADATA_CONTENT_TYPE.equals(field)) {
                builder.contentType(value);
            }
        });
    }

    /**
     * @deprecated 使用 {@link #getMetadataUpdate()} 替代
     */
    @Deprecated
    public Map<String, String> getMetadataChanges() {
        FileMetadataUpdate update = getMetadataUpdate().orElse(null);
        if (update == null) {
            return new HashMap<>();
        }
        Map<String, String> legacy = new HashMap<>();
        if (update.filename() != null) {
            legacy.put(METADATA_FILENAME, update.filename());
        }
        if (update.contentType() != null) {
            legacy.put(METADATA_CONTENT_TYPE, update.contentType());
        }
        return legacy;
    }

    /**
     * @deprecated 使用 {@link #hasMetadataUpdates()} 替代
     */
    @Deprecated
    public boolean hasMetadataChanges() {
        return hasMetadataUpdates();
    }

    // ==================== 衍生文件操作 ====================

    /**
     * 获取衍生文件列表
     */
    public List<DerivedFile> getDerivedFiles() {
        return derivedFiles.getAll();
    }

    /**
     * 添加衍生文件
     */
    public void addDerivedFile(DerivedFile derivedFile) {
        derivedFiles.add(derivedFile);
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取存储路径
     */
    public Optional<String> getStoragePath() {
        return executionInfo.getStoragePath();
    }

    /**
     * 获取本地文件路径
     */
    public Optional<String> getLocalFilePath() {
        return executionInfo.getLocalFilePath();
    }

    /**
     * 获取所有数据的只读视图（兼容旧API）
     */
    public Map<String, Object> asMap() {
        Map<String, Object> merged = new HashMap<>();

        // 执行信息
        executionInfo.getTaskId().ifPresent(v -> merged.put("taskId", v));
        executionInfo.getStoragePath().ifPresent(v -> merged.put(KEY_STORAGE_PATH, v));
        executionInfo.getLocalFilePath().ifPresent(v -> merged.put(KEY_LOCAL_FILE_PATH, v));
        executionInfo.getFileHash().ifPresent(v -> merged.put(KEY_FILE_HASH, v));
        executionInfo.getContentType().ifPresent(v -> merged.put(KEY_CONTENT_TYPE, v));
        executionInfo.getFileSize().ifPresent(v -> merged.put(KEY_FILE_SIZE, v));
        executionInfo.getFilename().ifPresent(v -> merged.put(KEY_FILENAME, v));

        // 插件参数
        merged.putAll(pluginParams.asMap());

        // 插件输出
        merged.putAll(pluginOutputs.asMap());

        // 衍生文件
        merged.put(KEY_DERIVED_FILES, derivedFiles.getAll());

        // 元数据更新
        fileMetadata.getPrimaryMetadata().ifPresent(v -> merged.put(KEY_METADATA_UPDATE, v));

        return Collections.unmodifiableMap(merged);
    }

    /**
     * 获取所有数据的可修改副本
     */
    public Map<String, Object> toMap() {
        return new HashMap<>(asMap());
    }

    /**
     * 获取 Plugin 输出（排除内置 Key）
     * <p>
     * 用于构建完成事件，只返回 Plugin 写入的数据。
     */
    public Map<String, Object> getPluginOutputs() {
        return pluginOutputs.asMap();
    }

    private boolean isBuiltinKey(String key) {
        return key.equals(KEY_STORAGE_PATH)
                || key.equals(KEY_LOCAL_FILE_PATH)
                || key.equals(KEY_FILE_HASH)
                || key.equals(KEY_CONTENT_TYPE)
                || key.equals(KEY_FILE_SIZE)
                || key.equals(KEY_FILENAME)
                || key.equals(KEY_DERIVED_FILES);
    }

    /**
     * 检查是否包含 Key
     */
    public boolean containsKey(String key) {
        return get(key).isPresent();
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return pluginParams.asMap().isEmpty()
                && pluginOutputs.asMap().isEmpty()
                && derivedFiles.isEmpty();
    }

    // ==================== 诊断与调试 (P3.13) ====================

    /**
     * 获取所有可用的键名
     * <p>
     * 用于调试和问题排查，快速查看 Context 中存储了哪些数据。
     *
     * @return 键名集合的不可变视图
     */
    public Set<String> getAvailableKeys() {
        return Collections.unmodifiableSet(asMap().keySet());
    }

    /**
     * 获取诊断信息
     * <p>
     * 提供 Context 的概览信息，便于调试和日志输出。
     *
     * @return 诊断信息 Map
     */
    public Map<String, Object> getDiagnosticInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        // 基本统计
        info.put("totalKeys", asMap().size());

        // 任务信息
        executionInfo.getTaskId().ifPresent(v -> info.put("taskId", v));

        // 文件信息
        executionInfo.getFilename().ifPresent(v -> info.put("fileName", v));
        executionInfo.getFileSize().ifPresent(v -> info.put("fileSize", v));
        executionInfo.getFileHash().ifPresent(v -> info.put("fileHash", v));

        // 元数据变更统计
        info.put("metadataChanges", hasMetadataUpdates() ? 1 : 0);

        // 衍生文件统计
        info.put("derivedFiles", derivedFiles.count());

        // 键分类
        List<String> builtinKeys = new ArrayList<>();
        List<String> pluginKeys = new ArrayList<>();

        for (String key : asMap().keySet()) {
            if (isBuiltinKey(key)) {
                builtinKeys.add(key);
            } else {
                pluginKeys.add(key);
            }
        }

        info.put("builtinKeys", builtinKeys);
        info.put("pluginKeys", pluginKeys);

        return info;
    }

    @Override
    public String toString() {
        return "TaskContext{" +
                "taskId=" + taskId +
                ", pluginParams=" + pluginParams.asMap().size() +
                ", pluginOutputs=" + pluginOutputs.asMap().size() +
                ", derivedFiles=" + derivedFiles.count() +
                '}';
    }
}
