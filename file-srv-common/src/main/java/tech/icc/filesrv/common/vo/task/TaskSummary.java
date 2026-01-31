package tech.icc.filesrv.common.vo.task;

import lombok.Builder;

import java.time.Instant;

/**
 * Task summary - task identity and scheduling info.
 *
 * @param taskId    unique task identifier
 * @param uploadId  storage provider's upload ID for multipart upload
 * @param status    current status of the task
 * @param createdAt timestamp when the task was created
 * @param expiresAt timestamp when the task expires
 */
@Builder
public record TaskSummary(
        String taskId,
        String uploadId,
        TaskStatus status,
        Instant createdAt,
        Instant expiresAt
) {}
