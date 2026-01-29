package tech.icc.filesrv.core.application.entrypoint.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;
import tech.icc.filesrv.common.vo.audit.AuditInfo;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.common.vo.file.FileTags;

/**
 * 文件元数据 API 响应（用于查询接口）
 * <p>
 * 用于元数据查询和列表展示场景，组合所有文件相关信息。
 */
@Builder
public record FileMeta(
        @JsonUnwrapped FileIdentity identity,
        @JsonUnwrapped OwnerInfo owner,
        @JsonUnwrapped AccessControl access,
        @JsonUnwrapped FileTags fileTags,
        @JsonUnwrapped CustomMetadata metadata,
        @JsonUnwrapped AuditInfo audit
) {}
