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
) {
    /**
     * 创建当前时间的审计信息
     */
    public static AuditInfo now() {
        OffsetDateTime now = OffsetDateTime.now();
        return new AuditInfo(now, now);
    }

    /**
     * 更新修改时间
     */
    public AuditInfo touch() {
        return new AuditInfo(createdAt, OffsetDateTime.now());
    }
}
