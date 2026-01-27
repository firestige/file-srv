package tech.icc.filesrv.core.domain.files;

import java.util.List;
import java.util.Optional;

/**
 * 物理文件信息仓储接口
 * <p>
 * 领域层定义，基础设施层实现。
 */
public interface FileInfoRepository {

    /**
     * 保存文件信息
     */
    FileInfo save(FileInfo fileInfo);

    /**
     * 根据 contentHash 查找
     */
    Optional<FileInfo> findByContentHash(String contentHash);

    /**
     * 检查 contentHash 是否存在
     */
    boolean existsByContentHash(String contentHash);

    /**
     * 原子增加引用计数
     *
     * @return 更新的行数
     */
    int incrementRefCount(String contentHash);

    /**
     * 原子减少引用计数
     *
     * @return 更新的行数
     */
    int decrementRefCount(String contentHash);

    /**
     * 查找可 GC 的文件（refCount <= 0 且 status = DELETED）
     *
     * @param limit 最大数量
     * @return 待清理的文件列表
     */
    List<FileInfo> findGarbageFiles(int limit);

    /**
     * 根据 contentHash 删除
     */
    void deleteByContentHash(String contentHash);
}
