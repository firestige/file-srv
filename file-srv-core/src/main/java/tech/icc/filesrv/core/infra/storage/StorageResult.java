package tech.icc.filesrv.core.infra.storage;

import java.time.Instant;

/**
 * 存储上传结果
 * <p>
 * 包含存储层返回的信息，用于更新元数据。
 *
 * @param path       实际存储路径
 * @param checksum   存储层返回的校验和（如 ETag）
 * @param size       实际存储大小（字节）
 * @param uploadedAt 上传完成时间
 */
public record StorageResult(
        String path,
        String checksum,
        Long size,
        Instant uploadedAt
) {
    /**
     * 创建上传结果
     */
    public static StorageResult of(String path, String checksum, Long size) {
        return new StorageResult(path, checksum, size, Instant.now());
    }
}
