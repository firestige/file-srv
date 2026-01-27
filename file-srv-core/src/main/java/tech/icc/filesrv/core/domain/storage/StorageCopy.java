package tech.icc.filesrv.core.domain.storage;

import java.time.Instant;
import java.util.UUID;

/**
 * 存储副本
 * <p>
 * 值对象，表示文件在某存储节点上的一份拷贝。
 * 一个 FileInfo 可以有多个 StorageCopy，分布在不同节点。
 *
 * @param copyId    副本唯一标识
 * @param nodeId    所属存储节点
 * @param path      存储路径
 * @param tier      存储层级
 * @param status    副本状态
 * @param createdAt 创建时间
 */
public record StorageCopy(
        String copyId,
        String nodeId,
        String path,
        StorageTier tier,
        CopyStatus status,
        Instant createdAt
) {

    /**
     * 创建新副本（PENDING 状态）
     *
     * @param nodeId 目标节点
     * @param path   存储路径
     * @param tier   存储层级
     * @return 新副本
     */
    public static StorageCopy create(String nodeId, String path, StorageTier tier) {
        return new StorageCopy(
                UUID.randomUUID().toString(),
                nodeId,
                path,
                tier,
                CopyStatus.PENDING,
                Instant.now()
        );
    }

    /**
     * 激活副本（上传完成）
     */
    public StorageCopy activate() {
        return new StorageCopy(copyId, nodeId, path, tier, CopyStatus.ACTIVE, createdAt);
    }

    /**
     * 标记迁移中
     */
    public StorageCopy markMigrating() {
        return new StorageCopy(copyId, nodeId, path, tier, CopyStatus.MIGRATING, createdAt);
    }

    /**
     * 标记删除
     */
    public StorageCopy markDeleted() {
        return new StorageCopy(copyId, nodeId, path, tier, CopyStatus.DELETED, createdAt);
    }

    /**
     * 副本是否可用于读取
     */
    public boolean isAvailable() {
        return status == CopyStatus.ACTIVE;
    }
}
