package tech.icc.filesrv.common.vo.audit;

import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Audit information.
 *
 * @param createdAt timestamp when the file was created
 * @param updatedAt timestamp when the file was last updated
 */
@Builder
public record AuditInfo(
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
