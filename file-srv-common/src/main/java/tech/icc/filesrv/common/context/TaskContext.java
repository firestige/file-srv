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

    public TaskContext(Map<String, Object> initialData) {
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

    @Override
    public String toString() {
        return "TaskContext" + data;
    }
}
