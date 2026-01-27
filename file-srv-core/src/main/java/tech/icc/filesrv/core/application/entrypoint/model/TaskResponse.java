package tech.icc.filesrv.core.application.entrypoint.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * 任务响应的密封接口，根据任务状态返回不同的响应结构。
 * <p>
 * 使用场景：
 * <ul>
 *   <li>createUploadTask - 返回 Pending</li>
 *   <li>getTaskDetail - 根据实际状态返回对应类型</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "status")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TaskResponse.Pending.class, name = "PENDING"),
        @JsonSubTypes.Type(value = TaskResponse.InProgress.class, name = "IN_PROGRESS"),
        @JsonSubTypes.Type(value = TaskResponse.Completed.class, name = "COMPLETED"),
        @JsonSubTypes.Type(value = TaskResponse.Failed.class, name = "FAILED"),
        @JsonSubTypes.Type(value = TaskResponse.Aborted.class, name = "ABORTED")
})
public sealed interface TaskResponse
        permits TaskResponse.Pending, TaskResponse.InProgress, TaskResponse.Completed,
        TaskResponse.Failed, TaskResponse.Aborted {

    // ==================== 共享组件 ====================

    /**
     * 任务摘要信息 - 包含任务身份标识和调度信息
     */
    @Builder
    record TaskSummary(
            String taskId,
            String uploadId,
            Instant createdAt,
            Instant expiresAt
    ) {}

    /**
     * 文件请求信息 - 用户创建任务时提交的原始请求
     */
    @Builder
    record FileRequest(
            String filename,
            String contentType,
            Long size,
            String checksum,
            String location
    ) {}

    /**
     * 上传进度信息
     */
    @Builder
    record UploadProgress(
            int totalParts,
            int uploadedParts,
            long uploadedBytes,
            List<PartInfo> parts
    ) {
        @Builder
        public record PartInfo(
                int partNumber,
                long size,
                String checksum,
                Instant uploadedAt
        ) {}
    }

    /**
     * 失败详情
     */
    @Builder
    record FailureDetail(
            String errorCode,
            String errorMessage,
            Instant failedAt
    ) {}

    /**
     * 衍生文件信息 - 如缩略图、转码文件等
     */
    @Builder
    record DerivedFile(
            String type,
            String fileId,
            String path,
            String contentType,
            Long size
    ) {}

    // ==================== 状态实现 ====================

    /**
     * 等待上传状态 - createUploadTask 的响应也使用此类型
     */
    @Builder
    record Pending(
            @JsonUnwrapped TaskSummary summary,
            @JsonUnwrapped FileRequest request,
            String uploadUrl,
            List<String> partUploadUrls
    ) implements TaskResponse {}

    /**
     * 上传中状态
     */
    @Builder
    record InProgress(
            @JsonUnwrapped TaskSummary summary,
            @JsonUnwrapped FileRequest request,
            @JsonUnwrapped UploadProgress progress
    ) implements TaskResponse {}

    /**
     * 上传完成状态
     */
    @Builder
    record Completed(
            @JsonUnwrapped TaskSummary summary,
            FileInfo file,
            List<DerivedFile> derivedFiles
    ) implements TaskResponse {}

    /**
     * 上传失败状态
     */
    @Builder
    record Failed(
            @JsonUnwrapped TaskSummary summary,
            @JsonUnwrapped FileRequest request,
            @JsonUnwrapped UploadProgress progress,
            @JsonUnwrapped FailureDetail failure
    ) implements TaskResponse {}

    /**
     * 上传中止状态
     */
    @Builder
    record Aborted(
            @JsonUnwrapped TaskSummary summary,
            @JsonUnwrapped FileRequest request,
            Instant abortedAt,
            String reason
    ) implements TaskResponse {}
}
