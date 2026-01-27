package tech.icc.filesrv.core.infra.storage;

import java.util.List;

/**
 * 存储适配器注册中心
 * <p>
 * 管理多个存储适配器实例，支持按节点 ID 获取。
 * Phase 1 仅注册一个适配器，Phase 2+ 支持多节点。
 */
public interface StorageAdapterRegistry {

    /**
     * 根据节点 ID 获取适配器
     *
     * @param nodeId 节点 ID
     * @return 存储适配器
     * @throws IllegalArgumentException 节点不存在时抛出
     */
    StorageAdapter getAdapter(String nodeId);

    /**
     * 注册适配器
     *
     * @param nodeId  节点 ID
     * @param adapter 存储适配器
     */
    void register(String nodeId, StorageAdapter adapter);

    /**
     * 注销适配器
     *
     * @param nodeId 节点 ID
     */
    void unregister(String nodeId);

    /**
     * 获取所有已注册的节点 ID
     *
     * @return 节点 ID 列表
     */
    List<String> getRegisteredNodeIds();

    /**
     * 检查节点是否已注册
     *
     * @param nodeId 节点 ID
     * @return 是否已注册
     */
    boolean isRegistered(String nodeId);
}
