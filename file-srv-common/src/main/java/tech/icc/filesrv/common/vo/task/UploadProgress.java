package tech.icc.filesrv.common.vo.task;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Upload progress information.
 *
 * @param totalParts    total number of parts expected
 * @param uploadedParts number of parts successfully uploaded
 * @param uploadedBytes total bytes uploaded so far
 * @param parts         details of each uploaded part
 */
@Builder
public record UploadProgress(
        int totalParts,
        int uploadedParts,
        long uploadedBytes,
        List<PartInfo> parts
) {
    /**
     * Part upload information.
     *
     * @param partNumber part sequence number (1-based)
     * @param size       part size in bytes
     * @param eTag       part eTag/checksum
     * @param uploadedAt timestamp when the part was uploaded
     */
    @Builder
    public record PartInfo(
            int partNumber,
            long size,
            String eTag,
            Instant uploadedAt
    ) {}
}
