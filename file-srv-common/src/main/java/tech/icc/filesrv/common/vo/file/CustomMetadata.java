package tech.icc.filesrv.common.vo.file;

import lombok.Builder;

import java.util.Map;

/**
 * Custom metadata for business extensions.
 *
 * @param customMetadata key-value pairs for custom metadata
 */
@Builder
public record CustomMetadata(
        Map<String, String> customMetadata
) {
    public CustomMetadata {
        customMetadata = customMetadata != null ? Map.copyOf(customMetadata) : Map.of();
    }

    /**
     * Empty metadata
     */
    public static CustomMetadata empty() {
        return new CustomMetadata(Map.of());
    }

    /**
     * Create metadata from map
     */
    public static CustomMetadata of(Map<String, String> metadata) {
        return new CustomMetadata(metadata);
    }
}
