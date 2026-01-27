package tech.icc.filesrv.core.application.entrypoint.assembler;

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
     * @param request API 层上传请求（可选的元数据）
     * @return 应用层 DTO（部分填充，文件信息由 MultipartFile 提供）
     */
    public static FileInfoDto toDto(FileUploadRequest request) {
        if (request == null) {
            return FileInfoDto.builder()
                    .owner(null)
                    .access(null)
                    .build();
        }
        return FileInfoDto.builder()
                .owner(request.toOwnerInfo())
                .access(request.toAccessControl())
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
                .fileName(request.getFileName())
                .creator(request.getCreator())
                .contentType(request.getContentType())
                .createdFrom(request.getCreatedFrom())
                .updatedTo(request.getUpdatedTo())
                .tags(request.getTags())
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
        return FileInfoResponse.builder()
                .identity(dto.identity())
                .storageRef(dto.storageRef())
                .owner(dto.owner())
                .audit(dto.audit())
                .access(FileInfoResponse.AccessControlView.from(dto.access()))
                .build();
    }
}
