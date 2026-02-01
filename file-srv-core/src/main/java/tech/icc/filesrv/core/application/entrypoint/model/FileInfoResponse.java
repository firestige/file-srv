package tech.icc.filesrv.core.application.entrypoint.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;
import tech.icc.filesrv.common.vo.audit.AuditInfo;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.common.vo.file.FileRelations;
import tech.icc.filesrv.common.vo.file.FileTags;
import tech.icc.filesrv.common.vo.file.StorageRef;

/**
 * 文件信息 API 响应
 * <p>
 * 组合共享 VO，添加展示相关注解（@JsonUnwrapped 等）。
 * 由 Assembler 从应用层 DTO 转换而来。
 * </p>
 * <p>
 * The {@link FileRelations} field is unwrapped to expose source, main, and derived
 * file relationships directly in the JSON response.
 * </p>
 */
@Builder
public record FileInfoResponse(
        @JsonUnwrapped FileIdentity identity,
        @JsonUnwrapped StorageRef storageRef,
        @JsonUnwrapped OwnerInfo owner,
        @JsonUnwrapped AuditInfo audit,
        @JsonUnwrapped AccessControl access,
        @JsonUnwrapped FileTags fileTags,
        @JsonUnwrapped CustomMetadata metadata,
        @JsonUnwrapped FileRelations relations
) {}
