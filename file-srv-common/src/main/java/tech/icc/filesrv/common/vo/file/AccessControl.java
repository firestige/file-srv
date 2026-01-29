package tech.icc.filesrv.common.vo.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Access control - defines file visibility.
 *
 * @param isPublic whether the file is publicly accessible
 */
@Builder
public record AccessControl(
        @JsonProperty("public") Boolean isPublic
) {
    /**
     * 默认访问控制（私有）
     */
    public static AccessControl defaultAccess() {
        return new AccessControl(false);
    }

    /**
     * 公开访问
     */
    public static AccessControl publicAccess() {
        return new AccessControl(true);
    }

    /**
     * 私有访问
     */
    public static AccessControl privateAccess() {
        return new AccessControl(false);
    }
}
