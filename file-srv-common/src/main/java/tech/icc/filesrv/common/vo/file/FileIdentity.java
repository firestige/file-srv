package tech.icc.filesrv.common.vo.file;

import lombok.Builder;

/**
 * File identity - core attributes independent of storage.
 *
 * @param fKey     unique file key
 * @param fileName original file name
 * @param fileType MIME type, e.g., "image/png"
 * @param fileSize file size in bytes
 */
@Builder
public record FileIdentity(
        String fKey,
        String fileName,
        String fileType,
        Long fileSize
) {}
