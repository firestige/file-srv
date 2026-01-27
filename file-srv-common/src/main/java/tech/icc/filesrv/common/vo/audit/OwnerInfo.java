package tech.icc.filesrv.common.vo.audit;

import lombok.Builder;

/**
 * Owner information.
 *
 * @param createdBy   creator's user ID
 * @param creatorName creator's display name
 */
@Builder
public record OwnerInfo(
        String createdBy,
        String creatorName
) {}
