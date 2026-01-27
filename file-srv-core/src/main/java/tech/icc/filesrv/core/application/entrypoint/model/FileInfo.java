package tech.icc.filesrv.core.application.entrypoint.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;
import tech.icc.filesrv.common.vo.audit.AuditInfo;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.common.vo.file.StorageRef;

import java.util.Map;

/**
 * File information response DTO - assembles shared VOs with presentation annotations
 */
@Builder
public record FileInfo(
        @JsonUnwrapped FileIdentity identity,
        @JsonUnwrapped StorageRef storageRef,
        @JsonUnwrapped OwnerInfo owner,
        @JsonUnwrapped AuditInfo audit,
        @JsonUnwrapped AccessControlView access
) {
    /**
     * Access control view - wraps shared VO with presentation annotations
     */
    @Builder
    public record AccessControlView(
            @JsonProperty("public") Boolean isPublic,
            String tags,
            Map<String, String> customMetadata
    ) {
        public static AccessControlView from(AccessControl ac) {
            return new AccessControlView(ac.isPublic(), ac.tags(), ac.customMetadata());
        }
    }
}
