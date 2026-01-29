package tech.icc.filesrv.common.vo.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * File identity - core attributes independent of storage.
 *
 * @param fKey     unique file key
 * @param fileName original file name
 * @param fileType MIME type, e.g., "image/png"
 * @param fileSize file size in bytes
 * @param eTag     file checksum/ETag for integrity verification
 */
@Builder
public record FileIdentity(
        @JsonProperty("fkey") String fKey,
        String fileName,
        String fileType,
        Long fileSize,
        String eTag
) {}
