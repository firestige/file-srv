package tech.icc.filesrv.common.vo.file;

import lombok.Builder;

import java.util.Map;

/**
 * Access control and extensions.
 *
 * @param isPublic       whether the file is publicly accessible
 * @param tags           comma-separated tags for categorization
 * @param customMetadata custom metadata key-value pairs
 */
@Builder
public record AccessControl(
        Boolean isPublic,
        String tags,
        Map<String, String> customMetadata
) {}
