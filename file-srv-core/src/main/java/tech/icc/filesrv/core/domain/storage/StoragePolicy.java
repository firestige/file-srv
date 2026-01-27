package tech.icc.filesrv.core.domain.storage;

/**
 * 存储策略
 * <p>
 * 值对象，定义文件的存储规则。
 *
 * @param replicaCount        副本数量（Phase 1 固定为 1）
 * @param preferredTier       首选存储层级
 * @param retentionDays       保留天数（0 表示永久）
 * @param enableDeduplication 是否启用去重
 */
public record StoragePolicy(
        Integer replicaCount,
        StorageTier preferredTier,
        Integer retentionDays,
        Boolean enableDeduplication
) {

    /**
     * 默认策略
     * <p>
     * 单副本、热存储、永久保留、启用去重
     */
    public static StoragePolicy defaultPolicy() {
        return new StoragePolicy(1, StorageTier.HOT, 0, true);
    }

    /**
     * 冷存储策略
     */
    public static StoragePolicy coldStoragePolicy() {
        return new StoragePolicy(1, StorageTier.COLD, 0, true);
    }

    /**
     * 归档策略
     *
     * @param retentionDays 保留天数
     */
    public static StoragePolicy archivePolicy(int retentionDays) {
        return new StoragePolicy(1, StorageTier.ARCHIVE, retentionDays, false);
    }

    /**
     * 是否永久保留
     */
    public boolean isPermanent() {
        return retentionDays == null || retentionDays <= 0;
    }
}
