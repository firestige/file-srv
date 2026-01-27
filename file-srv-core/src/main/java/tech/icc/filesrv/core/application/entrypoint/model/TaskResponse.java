package tech.icc.filesrv.core.application.entrypoint.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;
import tech.icc.filesrv.common.vo.task.DerivedFile;
import tech.icc.filesrv.common.vo.task.FailureDetail;
import tech.icc.filesrv.common.vo.task.FileRequest;
import tech.icc.filesrv.common.vo.task.TaskSummary;
import tech.icc.filesrv.common.vo.task.UploadProgress;

import java.time.Instant;
import java.util.List;

/**
 * Task response sealed interface - returns different response structures based on task status.
 * <p>
 * Usage:
 * <ul>
 *   <li>createUploadTask - returns Pending</li>
 *   <li>getTaskDetail - returns corresponding type based on actual status</li>
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

    // ==================== Status Implementations ====================

    /**
     * Pending status - also used for createUploadTask response
     */
    @Builder
    record Pending(
            @JsonUnwrapped TaskSummary summary,
            @JsonUnwrapped FileRequest request,
            String uploadUrl,
            List<String> partUploadUrls
    ) implements TaskResponse {}

    /**
     * In progress status
     */
    @Builder
    record InProgress(
            @JsonUnwrapped TaskSummary summary,
            @JsonUnwrapped FileRequest request,
            @JsonUnwrapped UploadProgress progress
    ) implements TaskResponse {}

    /**
     * Completed status
     */
    @Builder
    record Completed(
            @JsonUnwrapped TaskSummary summary,
            FileInfo file,
            List<DerivedFile> derivedFiles
    ) implements TaskResponse {}

    /**
     * Failed status
     */
    @Builder
    record Failed(
            @JsonUnwrapped TaskSummary summary,
            @JsonUnwrapped FileRequest request,
            @JsonUnwrapped UploadProgress progress,
            @JsonUnwrapped FailureDetail failure
    ) implements TaskResponse {}

    /**
     * Aborted status
     */
    @Builder
    record Aborted(
            @JsonUnwrapped TaskSummary summary,
            @JsonUnwrapped FileRequest request,
            Instant abortedAt,
            String reason
    ) implements TaskResponse {}
}
