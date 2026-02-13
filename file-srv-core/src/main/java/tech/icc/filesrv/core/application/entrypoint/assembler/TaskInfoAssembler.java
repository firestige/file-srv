package tech.icc.filesrv.core.application.entrypoint.assembler;

import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileTags;
import tech.icc.filesrv.common.vo.task.FileRequest;
import tech.icc.filesrv.core.application.entrypoint.model.CreateTaskRequest;
import tech.icc.filesrv.core.application.entrypoint.model.PartETag;
import tech.icc.filesrv.core.application.entrypoint.model.TaskResponse;
import tech.icc.filesrv.core.application.service.dto.PartETagDto;
import tech.icc.filesrv.core.application.service.dto.TaskInfoDto;

import java.util.List;

/**
 * 任务信息 Assembler
 * <p>
 * 负责 API 层 DTO 与应用层 DTO 之间的转换。
 * <ul>
 *   <li>Request → VO：入参转换，将扁平的 API 请求转换为嵌套的领域 VO</li>
 *   <li>Dto → Response：出参转换，Service 返回后包装为 API 响应</li>
 * </ul>
 */
public final class TaskInfoAssembler {

    private TaskInfoAssembler() {
        // 工具类，禁止实例化
    }

    // ==================== Request → VO ====================

    /**
     * 将 CreateTaskRequest 转换为 FileRequest VO
     * <p>
     * 从扁平的 API 请求模型构建嵌套的领域值对象，保持应用层 VO 复用性。
     *
     * @param request API 层创建任务请求
     * @return 应用层 FileRequest VO
     */
    public static FileRequest toFileRequest(CreateTaskRequest request) {
        if (request == null) {
            return null;
        }

        // 构建 AccessControl VO
        AccessControl accessControl = new AccessControl(
                request.getPublic() != null && request.getPublic()
        );

        // 构建 FileTags VO
        FileTags fileTags = request.getTags() != null
                ? FileTags.of(request.getTags())
                : FileTags.empty();

        // 构建 CustomMetadata VO
        CustomMetadata metadata = request.getCustomMetadata() != null
                ? CustomMetadata.of(request.getCustomMetadata())
                : CustomMetadata.empty();

        // 构建 OwnerInfo VO
        OwnerInfo owner = OwnerInfo.builder()
                .createdBy(request.getCreatedBy())
                .creatorName(request.getCreatorName())
                .build();

        // 组装 FileRequest
        return FileRequest.builder()
                .fKey(request.getFKey())  // 传递用户提供的 fKey
                .filename(request.getFilename())
                .contentType(request.getContentType())
                .size(request.getSize())
                .contentHash(request.getContentHash())
                .eTag(request.getETag())
                .owner(owner)
                .access(accessControl)
                .fileTags(fileTags)
                .metadata(metadata)
                .build();
    }

    // ==================== PartETag 转换 ====================

    /**
     * API 层 PartETag → 应用层 PartETagDto
     */
    public static PartETagDto toDto(PartETag partETag) {
        if (partETag == null) {
            return null;
        }
        return new PartETagDto(partETag.partNumber(), partETag.eTag());
    }

    /**
     * 应用层 PartETagDto → API 层 PartETag
     */
    public static PartETag toResponse(PartETagDto dto) {
        if (dto == null) {
            return null;
        }
        return new PartETag(dto.partNumber(), dto.eTag());
    }

    /**
     * 批量转换：API 层 → 应用层
     */
    public static List<PartETagDto> toDtoList(List<PartETag> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().map(TaskInfoAssembler::toDto).toList();
    }

    /**
     * 批量转换：应用层 → API 层
     */
    public static List<PartETag> toResponseList(List<PartETagDto> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().map(TaskInfoAssembler::toResponse).toList();
    }

    // ==================== TaskInfoDto → TaskResponse ====================

    /**
     * 应用层 TaskInfoDto → API 层 TaskResponse
     * <p>
     * 根据不同状态类型转换。
     */
    public static TaskResponse toResponse(TaskInfoDto dto) {
        if (dto == null) {
            return null;
        }

        if (dto instanceof TaskInfoDto.Pending p) {
            return TaskResponse.Pending.builder()
                    .summary(p.summary())
                    .request(p.request())
                    .build();
        }

        if (dto instanceof TaskInfoDto.InProgress ip) {
            return TaskResponse.InProgress.builder()
                    .summary(ip.summary())
                    .request(ip.request())
                    .progress(ip.progress())
                    .build();
        }

        if (dto instanceof TaskInfoDto.Completed c) {
            return TaskResponse.Completed.builder()
                    .summary(c.summary())
                    .file(FileInfoAssembler.toResponse(c.file()))
                    .derivedFiles(c.derivedFiles())
                    .build();
        }

        if (dto instanceof TaskInfoDto.Failed f) {
            return TaskResponse.Failed.builder()
                    .summary(f.summary())
                    .request(f.request())
                    .progress(f.progress())
                    .failure(f.failure())
                    .build();
        }

        if (dto instanceof TaskInfoDto.Aborted a) {
            return TaskResponse.Aborted.builder()
                    .summary(a.summary())
                    .request(a.request())
                    .abortedAt(a.abortedAt())
                    .reason(a.reason())
                    .build();
        }

        throw new IllegalArgumentException("Unknown TaskInfoDto type: " + dto.getClass().getName());
    }
}
