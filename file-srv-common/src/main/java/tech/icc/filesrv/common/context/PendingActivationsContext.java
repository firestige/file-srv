package tech.icc.filesrv.common.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待激活文件 Context
 * <p>
 * 管理衍生文件的延迟激活信息，采用 PENDING → 批量 ACTIVE 的延迟激活机制。
 * <p>
 * 设计目标：
 * <ul>
 *   <li>类型安全：通过 PendingActivation record 封装激活信息</li>
 *   <li>持久化支持：支持序列化/反序列化（断点恢复）</li>
 *   <li>批量处理：减少数据库交互次数</li>
 * </ul>
 */
public class PendingActivationsContext {

    /**
     * 待激活信息
     * <p>
     * 记录衍生文件上传后待激活的信息：
     * <ul>
     *   <li>contentHash - 文件内容哈希（xxHash-64）</li>
     *   <li>storagePath - 存储路径</li>
     *   <li>nodeId - 存储节点 ID</li>
     * </ul>
     */
    public record PendingActivation(
        String contentHash,
        String storagePath,
        String nodeId
    ) {
        public PendingActivation {
            if (contentHash == null || contentHash.isBlank()) {
                throw new IllegalArgumentException("contentHash cannot be null or blank");
            }
            if (storagePath == null || storagePath.isBlank()) {
                throw new IllegalArgumentException("storagePath cannot be null or blank");
            }
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId cannot be null or blank");
            }
        }
    }

    /** 待激活文件 Map：fKey -> PendingActivation */
    private final Map<String, PendingActivation> activations = new HashMap<>();

    // ==================== 核心操作 ====================

    /**
     * 记录待激活的衍生文件
     * <p>
     * 由 PluginStorageService 上传时调用，在 callback chain 结束后统一激活。
     *
     * @param fKey        文件唯一标识
     * @param contentHash 内容哈希
     * @param storagePath 存储路径
     * @param nodeId      存储节点 ID
     */
    public void add(String fKey, String contentHash, String storagePath, String nodeId) {
        if (fKey == null || fKey.isBlank()) {
            throw new IllegalArgumentException("fKey cannot be null or blank");
        }
        activations.put(fKey, new PendingActivation(contentHash, storagePath, nodeId));
    }

    /**
     * 记录待激活的衍生文件（直接传入 PendingActivation）
     */
    public void add(String fKey, PendingActivation activation) {
        if (fKey == null || fKey.isBlank()) {
            throw new IllegalArgumentException("fKey cannot be null or blank");
        }
        if (activation == null) {
            throw new IllegalArgumentException("activation cannot be null");
        }
        activations.put(fKey, activation);
    }

    /**
     * 获取所有待激活文件
     * <p>
     * 返回不可变视图，供 CallbackChainRunner 批量激活使用。
     *
     * @return 不可变的待激活文件 Map
     */
    public Map<String, PendingActivation> getAll() {
        return Collections.unmodifiableMap(activations);
    }

    /**
     * 获取指定文件的激活信息
     */
    public PendingActivation get(String fKey) {
        return activations.get(fKey);
    }

    /**
     * 检查是否有待激活文件
     */
    public boolean isEmpty() {
        return activations.isEmpty();
    }

    /**
     * 获取待激活文件数量
     */
    public int count() {
        return activations.size();
    }

    /**
     * 清空所有待激活信息
     * <p>
     * 注意：通常不需要手动调用，激活成功后会自然清空。
     */
    public void clear() {
        activations.clear();
    }

    // ==================== 持久化支持 ====================

    /**
     * 序列化为 Map（用于持久化）
     * <p>
     * 格式：List<Map<String, String>>
     * 每个 Map 包含：fKey, contentHash, storagePath, nodeId
     *
     * @return 序列化后的数据列表
     */
    public List<Map<String, String>> toMapList() {
        return activations.entrySet().stream()
            .map(entry -> {
                Map<String, String> item = new HashMap<>();
                item.put("fKey", entry.getKey());
                item.put("contentHash", entry.getValue().contentHash());
                item.put("storagePath", entry.getValue().storagePath());
                item.put("nodeId", entry.getValue().nodeId());
                return item;
            })
            .toList();
    }

    /**
     * 从 Map 列表反序列化（用于断点恢复）
     *
     * @param mapList 序列化的数据列表
     */
    public void mergeFromMapList(List<Map<String, String>> mapList) {
        if (mapList == null || mapList.isEmpty()) {
            return;
        }
        
        for (Map<String, String> item : mapList) {
            String fKey = item.get("fKey");
            String contentHash = item.get("contentHash");
            String storagePath = item.get("storagePath");
            String nodeId = item.get("nodeId");
            
            if (fKey != null && contentHash != null && storagePath != null && nodeId != null) {
                activations.put(fKey, new PendingActivation(contentHash, storagePath, nodeId));
            }
        }
    }

    @Override
    public String toString() {
        return "PendingActivationsContext{count=" + count() + "}";
    }
}
