package tech.icc.filesrv.core.domain.storage;

import java.util.List;
import java.util.Optional;

/**
 * 存储节点仓储接口
 * <p>
 * 领域层定义，基础设施层实现。
 */
public interface StorageNodeRepository {

    /**
     * 根据节点 ID 查找
     */
    Optional<StorageNode> findByNodeId(String nodeId);

    /**
     * 根据状态查找节点
     */
    List<StorageNode> findByStatus(NodeStatus status);

    /**
     * 根据存储层级查找节点
     */
    List<StorageNode> findByTier(StorageTier tier);

    /**
     * 查找所有可写节点
     */
    List<StorageNode> findWritableNodes();

    /**
     * 查找所有可用节点
     */
    List<StorageNode> findAvailableNodes();
}
