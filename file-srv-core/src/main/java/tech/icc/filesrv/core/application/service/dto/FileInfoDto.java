package tech.icc.filesrv.core.application.service.dto;

import lombok.Builder;
import tech.icc.filesrv.common.vo.audit.AuditInfo;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.common.vo.file.StorageRef;

/**
 * 应用层文件信息 DTO
 * <p>
 * 组合共享 VO，无框架注解，供 Service 层使用。
 * 与 API 层 DTO 的区别：不包含展示相关注解（如 @JsonUnwrapped）。
 *
 * @param identity   文件身份标识
 * @param storageRef 存储引用
 * @param owner      所有者信息
 * @param audit      审计信息
 * @param access     访问控制
 */
@Builder
public record FileInfoDto(
        FileIdentity identity,
        StorageRef storageRef,
        OwnerInfo owner,
        AuditInfo audit,
        AccessControl access
) {}
