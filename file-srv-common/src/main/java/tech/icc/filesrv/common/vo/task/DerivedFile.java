package tech.icc.filesrv.common.vo.task;

import lombok.Builder;

/**
 * Derived file - e.g., thumbnail, transcoded file.
 *
 * @param type        derived file type, e.g., "THUMBNAIL", "PREVIEW", "TRANSCODE"
 * @param fileId      unique identifier of the derived file
 * @param path        storage path of the derived file
 * @param contentType MIME type of the derived file
 * @param size        size of the derived file in bytes
 */
@Builder
public record DerivedFile(
        String type,
        String fileId,
        String path,
        String contentType,
        Long size
) {}
