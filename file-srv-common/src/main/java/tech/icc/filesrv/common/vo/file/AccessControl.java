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
) {
    /**
     * 默认访问控制（私有）
     */
    public static AccessControl defaultAccess() {
        return new AccessControl(false, null, Map.of());
    }

    /**
     * 公开访问
     */
    public static AccessControl publicAccess() {
        return new AccessControl(true, null, Map.of());
    }
}
