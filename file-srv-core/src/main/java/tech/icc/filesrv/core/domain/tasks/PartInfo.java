package tech.icc.filesrv.core.domain.tasks;

import jakarta.persistence.Embeddable;

/**
 * 分片信息值对象
 *
 * @param partNumber 分片序号 (1-based)
 * @param etag       存储层返回的 ETag
 * @param size       分片大小 (bytes)
 */
@Embeddable
public record PartInfo(
        int partNumber,
        String etag,
        long size
) {
    /**
     * 创建分片信息
     */
    public static PartInfo of(int partNumber, String etag, long size) {
        if (partNumber < 1) {
            throw new IllegalArgumentException("partNumber must be >= 1");
        }
        if (etag == null || etag.isBlank()) {
            throw new IllegalArgumentException("etag must not be blank");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0");
        }
        return new PartInfo(partNumber, etag, size);
    }
}
