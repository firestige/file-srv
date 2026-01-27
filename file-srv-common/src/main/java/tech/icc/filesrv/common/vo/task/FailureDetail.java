package tech.icc.filesrv.common.vo.task;

import lombok.Builder;

import java.time.Instant;

/**
 * Failure detail.
 *
 * @param errorCode    error code for programmatic handling
 * @param errorMessage human-readable error message
 * @param failedAt     timestamp when the failure occurred
 */
@Builder
public record FailureDetail(
        String errorCode,
        String errorMessage,
        Instant failedAt
) {}
