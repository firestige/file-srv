package tech.icc.filesrv.core.domain.storage;

/**
 * 存储节点
 * <p>
 * 领域实体，表示一个存储后端实例（如 OBS、S3、本地存储）。
 * Phase 1 使用单节点 "primary"，Phase 2+ 支持多节点池化。
 *
 * @param nodeId      节点唯一标识
 * @param name        人类可读名称
 * @param adapterType 适配器类型标识（如 "HCS_OBS", "AWS_S3", "LOCAL"）
 * @param tier        存储层级
 * @param status      节点状态
 * @param endpoint    连接端点（配置用）
 * @param bucket      存储桶或根目录
 */
public record StorageNode(
        String nodeId,
        String name,
        String adapterType,
        StorageTier tier,
        NodeStatus status,
        String endpoint,
        String bucket
) {

    /** Phase 1 默认节点 ID */
    public static final String PRIMARY_NODE_ID = "primary";

    /**
     * 创建 Phase 1 默认主节点
     *
     * @param adapterType 适配器类型
     * @param endpoint    连接端点
     * @param bucket      存储桶
     * @return 主节点
     */
    public static StorageNode primaryNode(String adapterType, String endpoint, String bucket) {
        return new StorageNode(
                PRIMARY_NODE_ID,
                "Primary Storage",
                adapterType,
                StorageTier.HOT,
                NodeStatus.ACTIVE,
                endpoint,
                bucket
        );
    }

    /**
     * 节点是否可用（可读）
     */
    public boolean isAvailable() {
        return status.isReadable();
    }

    /**
     * 节点是否可写
     */
    public boolean isWritable() {
        return status.isWritable();
    }

    /**
     * 设置节点状态
     */
    public StorageNode withStatus(NodeStatus newStatus) {
        return new StorageNode(nodeId, name, adapterType, tier, newStatus, endpoint, bucket);
    }
}
