package tech.icc.filesrv.common.context;

import lombok.Getter;
import tech.icc.filesrv.common.vo.task.CallbackConfig;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 插件参数上下文
 * <p>
 * 存储完整的 callback 配置链，通过索引指针指向当前执行的 callback。
 * <p>
 * 设计说明：
 * <ul>
 *   <li>保存整个 {@code List<CallbackConfig>}（只读）</li>
 *   <li>维护 {@code currentIndex} 指向当前执行的 callback</li>
 *   <li>参数获取直接从 {@code callbacks[currentIndex].params} 读取</li>
 * </ul>
 */
public class PluginParamsContext {
    
    private final List<CallbackConfig> callbacks;
    /**
     * -- GETTER --
     *  获取当前索引
     */
    @Getter
    private int currentIndex;

    /**
     * 创建空的参数上下文
     */
    public PluginParamsContext() {
        this.callbacks = Collections.emptyList();
        this.currentIndex = 0;
    }

    /**
     * 从 callback 配置列表创建
     *
     * @param callbacks callback 配置列表
     */
    public PluginParamsContext(List<CallbackConfig> callbacks) {
        this.callbacks = callbacks != null ? List.copyOf(callbacks) : Collections.emptyList();
        this.currentIndex = 0;
    }

    /**
     * 设置当前执行的 callback 索引
     *
     * @param index 索引（0-based）
     */
    public void setCurrentIndex(int index) {
        if (index < 0 || index >= callbacks.size()) {
            throw new IllegalArgumentException(
                    "Invalid callback index: " + index + ", valid range: [0, " + (callbacks.size() - 1) + "]");
        }
        this.currentIndex = index;
    }

    /**
     * 获取当前 callback 的参数
     *
     * @param key 参数 key
     * @return 参数值
     */
    public Optional<String> get(String key) {
        if (callbacks.isEmpty() || currentIndex >= callbacks.size()) {
            return Optional.empty();
        }
        
        CallbackConfig currentCallback = callbacks.get(currentIndex);
        return currentCallback.params().stream()
                .filter(param -> param.key().equals(key))
                .map(CallbackConfig.CallbackParam::value)
                .findFirst();
    }

    /**
     * 获取当前 callback 的参数（带默认值）
     *
     * @param key          参数 key
     * @param defaultValue 默认值
     * @return 参数值或默认值
     */
    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /**
     * 检查当前 callback 是否有指定参数
     *
     * @param key 参数 key
     * @return 是否存在
     */
    public boolean contains(String key) {
        return get(key).isPresent();
    }

    /**
     * 获取当前 callback 的所有参数（Map 格式）
     *
     * @return 参数 Map（只读）
     */
    public Map<String, String> asMap() {
        if (callbacks.isEmpty() || currentIndex >= callbacks.size()) {
            return Collections.emptyMap();
        }
        
        CallbackConfig currentCallback = callbacks.get(currentIndex);
        return currentCallback.params().stream()
                .collect(Collectors.toUnmodifiableMap(
                        CallbackConfig.CallbackParam::key,
                        CallbackConfig.CallbackParam::value
                ));
    }

    /**
     * 获取当前 callback 的名称
     *
     * @return callback 名称
     */
    public Optional<String> getCurrentCallbackName() {
        if (callbacks.isEmpty() || currentIndex >= callbacks.size()) {
            return Optional.empty();
        }
        return Optional.of(callbacks.get(currentIndex).name());
    }

    /**
     * 获取 callback 总数
     *
     * @return callback 数量
     */
    public int getCallbackCount() {
        return callbacks.size();
    }

    @Override
    public String toString() {
        return "PluginParamsContext{" +
                "currentIndex=" + currentIndex +
                ", totalCallbacks=" + callbacks.size() +
                ", currentCallback=" + getCurrentCallbackName().orElse("none") +
                '}';
    }
}
