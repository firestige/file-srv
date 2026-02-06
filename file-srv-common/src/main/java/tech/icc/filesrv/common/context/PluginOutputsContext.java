package tech.icc.filesrv.common.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 插件输出上下文
 * <p>
 * 存储插件间通信的数据，前序插件的输出可以被后续插件读取
 */
public class PluginOutputsContext {
    
    private final Map<String, Object> outputs;

    public PluginOutputsContext() {
        this.outputs = new HashMap<>();
    }

    /**
     * 获取输出值
     */
    public Optional<Object> get(String key) {
        return Optional.ofNullable(outputs.get(key));
    }

    /**
     * 获取输出值
     */
    public <T> Optional<T> get(String key, Class<T> tClass) {
        return Optional.ofNullable(outputs.get(key)).filter(tClass::isInstance).map(tClass::cast);
    }

    /**
     * 写入输出值
     */
    public void put(String key, Object value) {
        outputs.put(key, value);
    }

    /**
     * 批量写入输出
     */
    public void putAll(Map<String, Object> outputs) {
        if (outputs != null) {
            this.outputs.putAll(outputs);
        }
    }

    /**
     * 检查输出是否存在
     */
    public boolean contains(String key) {
        return outputs.containsKey(key);
    }

    /**
     * 移除输出
     */
    public void remove(String key) {
        outputs.remove(key);
    }

    /**
     * 获取所有输出的只读视图
     */
    public Map<String, Object> asMap() {
        return Map.copyOf(outputs);
    }

    @Override
    public String toString() {
        return "PluginOutputsContext{outputs=" + outputs + '}';
    }
}
