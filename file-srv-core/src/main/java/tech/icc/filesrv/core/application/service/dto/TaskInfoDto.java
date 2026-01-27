package tech.icc.filesrv.core.application.service.dto;

import lombok.Builder;
import tech.icc.filesrv.common.vo.task.DerivedFile;
import tech.icc.filesrv.common.vo.task.FailureDetail;
import tech.icc.filesrv.common.vo.task.FileRequest;
import tech.icc.filesrv.common.vo.task.TaskSummary;
import tech.icc.filesrv.common.vo.task.UploadProgress;

import java.time.Instant;
import java.util.List;

/**
 * 应用层任务信息
 * <p>
 * 无 JSON 注解，供 Service 层使用。
 * 使用 sealed interface 保持类型安全，与 API 层 TaskResponse 结构对应。
 */
public sealed interface TaskInfoDto
        permits TaskInfoDto.Pending, TaskInfoDto.InProgress,
        TaskInfoDto.Completed, TaskInfoDto.Failed, TaskInfoDto.Aborted {

    /**
     * 待上传状态
     */
    @Builder
    record Pending(
            TaskSummary summary,
            FileRequest request,
            String uploadUrl,
            List<String> partUploadUrls
    ) implements TaskInfoDto {}

    /**
     * 上传中状态
     */
    @Builder
    record InProgress(
            TaskSummary summary,
            FileRequest request,
            UploadProgress progress
    ) implements TaskInfoDto {}

    /**
     * 已完成状态
     */
    @Builder
    record Completed(
            TaskSummary summary,
            FileInfoDto file,
            List<DerivedFile> derivedFiles
    ) implements TaskInfoDto {}

    /**
     * 已失败状态
     */
    @Builder
    record Failed(
            TaskSummary summary,
            FileRequest request,
            UploadProgress progress,
            FailureDetail failure
    ) implements TaskInfoDto {}

    /**
     * 已中止状态
     */
    @Builder
    record Aborted(
            TaskSummary summary,
            FileRequest request,
            Instant abortedAt,
            String reason
    ) implements TaskInfoDto {}
}
