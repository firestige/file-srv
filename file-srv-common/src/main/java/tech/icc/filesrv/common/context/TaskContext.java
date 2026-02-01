package tech.icc.filesrv.common.context;

import tech.icc.filesrv.common.vo.task.DerivedFile;

import java.util.*;

/**
 * 任务上下文
 * <p>
 * 存储任务执行期间的参数和输出。Plugin 可以读取参数、追加输出。
 * <p>
 * 参数约定：
 * <ul>
 *   <li>Plugin 特定参数使用 {@code {pluginName}.{paramKey}} 格式</li>
 *   <li>内置 Key 使用 {@code KEY_*} 常量</li>
 * </ul>
 */
public class TaskContext {

    // ==================== 内置 Key ====================

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

    // ==================== 数据存储 ====================

    private final Map<String, Object> data;

    public TaskContext() {
        this.data = new HashMap<>();
    }

    public TaskContext(Map<String, ?> initialData) {
        this.data = new HashMap<>(initialData != null ? initialData : Map.of());
    }

    // ==================== 读取操作 ====================

    /**
     * 获取值
     */
    public Optional<Object> get(String key) {
        return Optional.ofNullable(data.get(key));
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
        Object value = data.get(key);
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
     * 获取 Plugin 特定参数
     * <p>
     * 参数格式: {@code {pluginName}.{paramKey}}
     *
     * @param pluginName 插件名称
     * @param paramKey   参数 Key
     * @return 参数值
     */
    public Optional<Object> getPluginParam(String pluginName, String paramKey) {
        return get(pluginName + "." + paramKey);
    }

    /**
     * 获取 Plugin 字符串参数
     */
    public Optional<String> getPluginString(String pluginName, String paramKey) {
        return getPluginParam(pluginName, paramKey).map(Object::toString);
    }

    /**
     * 获取 Plugin 整数参数
     */
    public Optional<Integer> getPluginInt(String pluginName, String paramKey) {
        return getPluginParam(pluginName, paramKey).map(v -> {
            if (v instanceof Number n) {
                return n.intValue();
            }
            return Integer.parseInt(v.toString());
        });
    }

    // ==================== 写入操作 ====================

    /**
     * 设置值
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 批量设置值
     */
    public void putAll(Map<String, Object> values) {
        if (values != null) {
            data.putAll(values);
        }
    }

    /**
     * 移除值
     */
    public void remove(String key) {
        data.remove(key);
    }

    // ==================== 元数据修改操作 ====================

    /** 元数据变更记录 Key（内部使用） */
    public static final String KEY_METADATA_CHANGES = "_metadataChanges";

    // --- 元数据字段常量 ---
    /** 文件名字段 */
    public static final String METADATA_FILENAME = "filename";
    /** MIME 类型字段 */
    public static final String METADATA_CONTENT_TYPE = "contentType";

    /**
     * 设置元数据字段值（通用方法）
     * <p>
     * 供元数据修改类插件调用，变更会在 callback 链全部成功后应用到 FileReference。
     * <p>
     * 支持的字段：
     * <ul>
     *   <li>{@link #METADATA_FILENAME} - 文件名</li>
     *   <li>{@link #METADATA_CONTENT_TYPE} - MIME 类型</li>
     * </ul>
     *
     * @param field 元数据字段名，建议使用 {@code METADATA_*} 常量
     * @param value 新值
     */
    public void setMetadata(String field, String value) {
        getMetadataChanges().put(field, value);
    }

    /**
     * 获取元数据变更记录
     *
     * @return 元数据变更 Map，key 为字段名，value 为新值
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getMetadataChanges() {
        return (Map<String, String>) data.computeIfAbsent(
                KEY_METADATA_CHANGES, k -> new HashMap<String, String>());
    }

    /**
     * 是否有元数据变更
     */
    public boolean hasMetadataChanges() {
        Object changes = data.get(KEY_METADATA_CHANGES);
        return changes instanceof Map<?, ?> map && !map.isEmpty();
    }

    // ==================== 衍生文件操作 ====================

    /**
     * 获取衍生文件列表
     */
    @SuppressWarnings("unchecked")
    public List<DerivedFile> getDerivedFiles() {
        Object value = data.get(KEY_DERIVED_FILES);
        if (value instanceof List<?> list) {
            return (List<DerivedFile>) list;
        }
        return new ArrayList<>();
    }

    /**
     * 添加衍生文件
     */
    @SuppressWarnings("unchecked")
    public void addDerivedFile(DerivedFile derivedFile) {
        List<DerivedFile> files = (List<DerivedFile>) data.computeIfAbsent(
                KEY_DERIVED_FILES, k -> new ArrayList<>());
        files.add(derivedFile);
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取存储路径
     */
    public Optional<String> getStoragePath() {
        return getString(KEY_STORAGE_PATH);
    }

    /**
     * 获取本地文件路径
     */
    public Optional<String> getLocalFilePath() {
        return getString(KEY_LOCAL_FILE_PATH);
    }

    /**
     * 获取所有数据的只读视图
     */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(data);
    }

    /**
     * 获取所有数据的可修改副本
     */
    public Map<String, Object> toMap() {
        return new HashMap<>(data);
    }

    /**
     * 获取 Plugin 输出（排除内置 Key）
     * <p>
     * 用于构建完成事件，只返回 Plugin 写入的数据。
     */
    public Map<String, Object> getPluginOutputs() {
        Map<String, Object> outputs = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            // 排除内置 key 和内部 key
            if (!key.startsWith("_") && !isBuiltinKey(key)) {
                outputs.put(key, entry.getValue());
            }
        }
        return outputs;
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
        return data.containsKey(key);
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return data.isEmpty();
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
        return Collections.unmodifiableSet(data.keySet());
    }

    /**
     * 获取诊断信息
     * <p>
     * 提供 Context 的概览信息，便于调试和日志输出。
     *
     * @return 诊断信息 Map，包含以下字段：
     *         <ul>
     *           <li>totalKeys: 总键数量</li>
     *           <li>taskId: 任务ID（如果存在）</li>
     *           <li>taskStatus: 任务状态（如果存在）</li>
     *           <li>fileName: 文件名（如果存在）</li>
     *           <li>fileSize: 文件大小（如果存在）</li>
     *           <li>metadataChanges: 元数据变更数量</li>
     *           <li>derivedFiles: 衍生文件数量</li>
     *           <li>builtinKeys: 内置键列表</li>
     *           <li>pluginKeys: 插件键列表</li>
     *         </ul>
     */
    public Map<String, Object> getDiagnosticInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        
        // 基本统计
        info.put("totalKeys", data.size());
        
        // 任务信息（使用 TaskContextKeys 常量）
        getString(TaskContextKeys.TASK_ID).ifPresent(v -> info.put("taskId", v));
        getString(TaskContextKeys.TASK_STATUS).ifPresent(v -> info.put("taskStatus", v));
        
        // 文件信息
        getString(TaskContextKeys.KEY_FILENAME).ifPresent(v -> info.put("fileName", v));
        getLong(TaskContextKeys.FILE_SIZE).ifPresent(v -> info.put("fileSize", v));
        getString(TaskContextKeys.FILE_HASH).ifPresent(v -> info.put("fileHash", v));
        
        // 元数据变更统计
        if (hasMetadataChanges()) {
            info.put("metadataChanges", getMetadataChanges().size());
        } else {
            info.put("metadataChanges", 0);
        }
        
        // 衍生文件统计
        info.put("derivedFiles", getDerivedFiles().size());
        
        // 键分类
        List<String> builtinKeys = new ArrayList<>();
        List<String> pluginKeys = new ArrayList<>();
        
        for (String key : data.keySet()) {
            if (isBuiltinKey(key) || key.startsWith(TaskContextKeys.TASK_ID.substring(0, 5))) {
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
        return "TaskContext" + data;
    }
}
