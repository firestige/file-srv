package tech.icc.filesrv.core.application.entrypoint.model;

import lombok.Builder;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;

import java.util.Map;

/**
 * 文件上传请求
 * <p>
 * 用于简单上传接口的元数据请求参数。
 * 文件名、大小、类型从 MultipartFile 自动获取。
 */
@Builder
public record FileUploadRequest(
        /**
         * 文件所有者（可选，默认为系统）
         */
        String ownerId,
        String ownerName,

        /**
         * 访问控制（可选）
         */
        Boolean isPublic,
        String tags,
        Map<String, String> customMetadata
) {
    /**
     * 转换为 OwnerInfo
     */
    public OwnerInfo toOwnerInfo() {
        if (ownerId == null && ownerName == null) {
            return OwnerInfo.system();
        }
        return new OwnerInfo(ownerId, ownerName);
    }

    /**
     * 转换为 AccessControl
     */
    public AccessControl toAccessControl() {
        if (isPublic == null && tags == null && customMetadata == null) {
            return AccessControl.defaultAccess();
        }
        return new AccessControl(
                isPublic != null ? isPublic : false,
                tags,
                customMetadata != null ? customMetadata : Map.of()
        );
    }
}
