package tech.icc.filesrv.core.application.entrypoint.assembler;

import tech.icc.filesrv.core.application.entrypoint.model.PartETag;
import tech.icc.filesrv.core.application.entrypoint.model.TaskResponse;
import tech.icc.filesrv.core.application.service.dto.PartETagDto;
import tech.icc.filesrv.core.application.service.dto.TaskInfoDto;

import java.util.List;

/**
 * 任务信息 Assembler
 * <p>
 * 负责 API 层 DTO 与 应用层 DTO 之间的转换。
 */
public final class TaskInfoAssembler {

    private TaskInfoAssembler() {
        // 工具类，禁止实例化
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
                    .uploadUrl(p.uploadUrl())
                    .partUploadUrls(p.partUploadUrls())
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
