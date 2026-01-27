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
 * 文件信息 API 响应
 * <p>
 * 组合共享 VO，添加展示相关注解（@JsonUnwrapped 等）。
 * 由 Assembler 从应用层 DTO 转换而来。
 */
@Builder
public record FileInfoResponse(
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
