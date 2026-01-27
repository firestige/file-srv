package tech.icc.filesrv.core.domain.files;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 文件引用仓储接口
 * <p>
 * 领域层定义，基础设施层实现。
 */
public interface FileReferenceRepository {

    /**
     * 保存文件引用
     */
    FileReference save(FileReference reference);

    /**
     * 根据 fKey 查找
     */
    Optional<FileReference> findByFKey(String fKey);

    /**
     * 根据所有者查找
     */
    List<FileReference> findByOwner(String ownerId);

    /**
     * 根据 fKey 删除
     */
    void deleteByFKey(String fKey);

    /**
     * 检查 fKey 是否存在
     */
    boolean existsByFKey(String fKey);

    /**
     * 分页查询
     */
    Page<FileReference> findAll(FileReferenceSpec spec, Pageable pageable);
}
