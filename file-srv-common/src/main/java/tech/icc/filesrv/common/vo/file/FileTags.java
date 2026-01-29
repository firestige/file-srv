package tech.icc.filesrv.common.vo.file;

import lombok.Builder;

/**
 * File tags for categorization and search.
 *
 * @param tags comma-separated tag list
 */
@Builder
public record FileTags(
        String tags
) {
    /**
     * Empty tags
     */
    public static FileTags empty() {
        return new FileTags(null);
    }

    /**
     * Create tags from string
     */
    public static FileTags of(String tags) {
        return new FileTags(tags);
    }
}
