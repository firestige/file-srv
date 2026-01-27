package tech.icc.filesrv.core.domain.storage;

/**
 * 存储节点状态
 */
public enum NodeStatus {

    /** 正常服务 - 可读可写 */
    ACTIVE,

    /** 只读模式 - 迁移中或维护 */
    READONLY,

    /** 离线 - 不可用 */
    OFFLINE;

    /**
     * 节点是否可用于读取
     */
    public boolean isReadable() {
        return this == ACTIVE || this == READONLY;
    }

    /**
     * 节点是否可用于写入
     */
    public boolean isWritable() {
        return this == ACTIVE;
    }
}
