package tech.icc.filesrv.core.domain.files;

/**
 * 物理文件状态
 * <p>
 * 用于 FileInfo 的生命周期管理。
 */
public enum FileStatus {

    /** 待上传 - 元数据已创建，文件正在上传 */
    PENDING,

    /** 可用 - 文件上传完成，可正常访问 */
    ACTIVE,

    /** 已删除 - 标记删除，等待 GC 清理 */
    DELETED;

    /**
     * 文件是否可访问
     */
    public boolean isAccessible() {
        return this == ACTIVE;
    }

    /**
     * 文件是否可被 GC 清理
     */
    public boolean isGarbageCollectable() {
        return this == DELETED;
    }
}
