package tech.icc.filesrv.core.application.entrypoint.assembler;

import org.springframework.web.multipart.MultipartFile;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.common.vo.file.FileTags;
import tech.icc.filesrv.core.application.entrypoint.model.FileMeta;
import tech.icc.filesrv.core.application.entrypoint.model.FileInfoResponse;
import tech.icc.filesrv.core.application.entrypoint.model.FileUploadRequest;
import tech.icc.filesrv.core.application.entrypoint.model.MetaQueryRequest;
import tech.icc.filesrv.core.application.service.dto.FileInfoDto;
import tech.icc.filesrv.core.application.service.dto.MetaQueryCriteria;

/**
 * 文件信息 Assembler
 * <p>
 * 负责 API 层 DTO 与 应用层 DTO 之间的转换。
 * <ul>
 *   <li>Request → Criteria：入参转换，API 层验证后传递给 Service</li>
 *   <li>Dto → Response：出参转换，Service 返回后包装为 API 响应</li>
 * </ul>
 */
public final class FileInfoAssembler {

    private FileInfoAssembler() {
        // 工具类，禁止实例化
    }

    // ==================== Request → Criteria ====================

        /**
         * 将上传请求转换为应用层 DTO
         *
         * @param request API 层上传请求
         * @param file    上传的文件（用于获取实际大小）
         * @return 应用层 DTO（包含用户指定的 fileName 和 fileType）
         */
        public static FileInfoDto toDto(FileUploadRequest request, MultipartFile file) {
        if (request == null) {
            return FileInfoDto.builder()
                .identity(null)
                .owner(null)
                .access(null)
                .fileTags(null)
                .metadata(null)
                .build();
        }

        // 构建 FileIdentity（使用请求参数，不使用 MultipartFile 的原始值）
        FileIdentity identity = FileIdentity.builder()
            .fKey(request.getFKey())  // 传递用户提供的 fKey（可能为 null）
            .fileName(request.getFileName())
            .fileType(request.getFileType())
            .fileSize(file != null ? file.getSize() : null)
            .build();

        OwnerInfo owner = OwnerInfo.builder()
            .createdBy(request.getCreatedBy())
            .creatorName(request.getCreatorName())
            .build();

        AccessControl access = new AccessControl(
            request.getPublic() != null && request.getPublic()
        );

        FileTags tags = request.getTags() != null
            ? FileTags.of(request.getTags())
            : FileTags.empty();

        CustomMetadata metadata = request.getCustomMetadata() != null
            ? CustomMetadata.of(request.getCustomMetadata())
            : CustomMetadata.empty();

        return FileInfoDto.builder()
            .identity(identity)
            .owner(owner)
            .access(access)
            .fileTags(tags)
            .metadata(metadata)
            .build();
        }

    /**
     * 将 API 层查询请求转换为应用层查询条件
     *
     * @param request API 层请求（已通过 Bean Validation 验证）
     * @return 应用层查询条件
     */
    public static MetaQueryCriteria toCriteria(MetaQueryRequest request) {
        if (request == null) {
            return MetaQueryCriteria.builder().build();
        }
        return MetaQueryCriteria.builder()
                .name(request.getName())
                .namePrefix(request.getNamePrefix())
                .creator(request.getCreator())
                .creatorPrefix(request.getCreatorPrefix())
                .tagEither(request.getTagEither())
                .tagBoth(request.getTagBoth())
                .createdAt(request.getCreatedAt())
                .createdBefore(request.getCreatedBefore())
                .createdAfter(request.getCreatedAfter())
                .size(request.getSize())
                .sizeGe(request.getSizeGe())
                .sizeGt(request.getSizeGt())
                .sizeLe(request.getSizeLe())
                .sizeLt(request.getSizeLt())
                .build();
    }

    /**
     * 将应用层 DTO 转换为元数据响应（FileMeta）
     * <p>
     * FileMeta 用于元数据查询接口，包含文件标识、所有者、访问控制和审计信息，
     * 不包含存储引用（StorageRef）信息。
     *
     * @param dto 应用层 DTO
     * @return 元数据响应
     */
    public static FileMeta toFileMeta(FileInfoDto dto) {
        if (dto == null) {
            return null;
        }
        
        // 确保所有 VO 都有默认值
        AccessControl access = dto.access() != null ? dto.access() : AccessControl.defaultAccess();
        FileTags tags = dto.fileTags() != null ? dto.fileTags() : FileTags.empty();
        CustomMetadata metadata = dto.metadata() != null ? dto.metadata() : CustomMetadata.empty();
        
        return FileMeta.builder()
                .identity(dto.identity())
                .owner(dto.owner())
                .access(access)
                .fileTags(tags)
                .metadata(metadata)
                .audit(dto.audit())
                .build();
    }

    // ==================== Dto → Response ====================

    /**
     * 将应用层 DTO 转换为 API 层响应
     *
     * @param dto 应用层 DTO
     * @return API 层响应
     */
    public static FileInfoResponse toResponse(FileInfoDto dto) {
        if (dto == null) {
            return null;
        }
        
        // 确保所有 VO 都有默认值
        AccessControl access = dto.access() != null ? dto.access() : AccessControl.defaultAccess();
        FileTags tags = dto.fileTags() != null ? dto.fileTags() : FileTags.empty();
        CustomMetadata metadata = dto.metadata() != null ? dto.metadata() : CustomMetadata.empty();
        
        return FileInfoResponse.builder()
                .identity(dto.identity())
                .storageRef(dto.storageRef())
                .owner(dto.owner())
                .audit(dto.audit())
                .access(access)
                .fileTags(tags)
                .metadata(metadata)
                .build();
    }
}
