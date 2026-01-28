package tech.icc.filesrv.core.domain.services;

import tech.icc.filesrv.core.domain.storage.StorageNode;
import tech.icc.filesrv.core.domain.storage.StoragePolicy;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;

/**
 * 存储路由服务
 * <p>
 * 领域服务接口，负责存储节点选择和路由逻辑。
 */
public interface StorageRoutingService {

    /**
     * 根据策略选择目标存储节点
     * <p>
     * Phase 1: 固定返回 "primary" 节点
     *
     * @param policy 存储策略
     * @return 目标存储节点
     */
    StorageNode selectNode(StoragePolicy policy);

    /**
     * 获取节点对应的存储适配器
     *
     * @param nodeId 节点 ID
     * @return 存储适配器
     */
    StorageAdapter getAdapter(String nodeId);

    /**
     * 生成存储路径
     * <p>
     * 格式: {hash前2位}/{hash前4位}/{hash}.{extension}
     * 例如: ab/abcd/abcd1234567890.png
     *
     * @param contentHash 内容哈希
     * @param contentType MIME 类型（用于推断扩展名）
     * @return 存储路径
     */
    String buildStoragePath(String contentHash, String contentType);
}
