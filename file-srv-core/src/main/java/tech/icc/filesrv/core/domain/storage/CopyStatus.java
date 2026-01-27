package tech.icc.filesrv.core.domain.storage;

/**
 * 存储副本状态
 */
public enum CopyStatus {

    /** 创建中 - 正在上传 */
    PENDING,

    /** 可用 - 正常状态 */
    ACTIVE,

    /** 迁移中 - 正在转移到其他节点 */
    MIGRATING,

    /** 已删除 - 等待物理清理 */
    DELETED;

    /**
     * 副本是否可用于读取
     */
    public boolean isReadable() {
        return this == ACTIVE;
    }
}
