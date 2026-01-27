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
) {
    /**
     * 系统用户
     */
    public static OwnerInfo system() {
        return new OwnerInfo("SYSTEM", "System");
    }

    /**
     * 匿名用户
     */
    public static OwnerInfo anonymous() {
        return new OwnerInfo("ANONYMOUS", "Anonymous");
    }
}
