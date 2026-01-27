package tech.icc.filesrv.core.domain.files;

import tech.icc.filesrv.core.domain.storage.StorageCopy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 物理文件信息
 * <p>
 * 领域实体，以 contentHash 为主键，实现全局文件去重。
 * 一个 FileInfo 可被多个 FileReference 引用。
 *
 * @param contentHash 内容哈希（xxHash-64），作为主键
 * @param size        文件大小（字节）
 * @param contentType MIME 类型
 * @param refCount    引用计数
 * @param status      文件状态
 * @param copies      存储副本列表
 * @param createdAt   创建时间
 */
public record FileInfo(
        String contentHash,
        Long size,
        String contentType,
        Integer refCount,
        FileStatus status,
        List<StorageCopy> copies,
        Instant createdAt
) {

    /**
     * 创建待上传文件（PENDING 状态）
     *
     * @param contentHash 内容哈希
     * @param size        文件大小
     * @param contentType MIME 类型
     * @return PENDING 状态的 FileInfo
     */
    public static FileInfo createPending(String contentHash, Long size, String contentType) {
        return new FileInfo(
                contentHash,
                size,
                contentType,
                1,  // 初始引用计数为 1
                FileStatus.PENDING,
                new ArrayList<>(),
                Instant.now()
        );
    }

    /**
     * 激活文件（上传成功）
     *
     * @param copy 存储副本
     * @return ACTIVE 状态的 FileInfo
     */
    public FileInfo activate(StorageCopy copy) {
        List<StorageCopy> newCopies = new ArrayList<>(copies);
        newCopies.add(copy.activate());
        return new FileInfo(
                contentHash, size, contentType, refCount,
                FileStatus.ACTIVE, newCopies, createdAt
        );
    }

    /**
     * 增加引用计数
     */
    public FileInfo incrementRef() {
        return new FileInfo(
                contentHash, size, contentType, refCount + 1,
                status, copies, createdAt
        );
    }

    /**
     * 减少引用计数
     */
    public FileInfo decrementRef() {
        int newCount = Math.max(0, refCount - 1);
        FileStatus newStatus = newCount <= 0 ? FileStatus.DELETED : status;
        return new FileInfo(
                contentHash, size, contentType, newCount,
                newStatus, copies, createdAt
        );
    }

    /**
     * 是否可被 GC 清理
     */
    public boolean canGC() {
        return refCount <= 0 && status == FileStatus.DELETED;
    }

    /**
     * 获取主副本（用于下载）
     * <p>
     * 返回第一个可用的副本。
     */
    public Optional<StorageCopy> getPrimaryCopy() {
        return copies.stream()
                .filter(StorageCopy::isAvailable)
                .findFirst();
    }

    /**
     * 文件是否可访问
     */
    public boolean isAccessible() {
        return status.isAccessible() && getPrimaryCopy().isPresent();
    }
}
