package tech.icc.filesrv.common.spi.storage;

/**
 * 分片 ETag 信息
 * <p>
 * SPI 层的分片标识，用于完成分片上传时标识各分片。
 * 不依赖任何持久化框架。
 *
 * @param partNumber 分片序号 (1-based)
 * @param etag       存储层返回的 ETag
 */
public record PartETagInfo(
        int partNumber,
        String etag
) {
    /**
     * 创建分片 ETag 信息
     */
    public static PartETagInfo of(int partNumber, String etag) {
        if (partNumber < 1) {
            throw new IllegalArgumentException("partNumber must be >= 1");
        }
        if (etag == null || etag.isBlank()) {
            throw new IllegalArgumentException("etag must not be blank");
        }
        return new PartETagInfo(partNumber, etag);
    }
}
