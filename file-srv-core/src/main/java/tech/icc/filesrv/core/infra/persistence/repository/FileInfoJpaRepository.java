package tech.icc.filesrv.core.infra.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.icc.filesrv.core.domain.files.FileStatus;
import tech.icc.filesrv.core.infra.persistence.entity.FileInfoEntity;

import java.util.List;

/**
 * 物理文件信息 JPA Repository
 */
public interface FileInfoJpaRepository extends JpaRepository<FileInfoEntity, String> {

    @Modifying
    @Query("UPDATE FileInfoEntity f SET f.refCount = f.refCount + 1 WHERE f.contentHash = :contentHash")
    int incrementRefCount(@Param("contentHash") String contentHash);

    @Modifying
    @Query("UPDATE FileInfoEntity f SET f.refCount = f.refCount - 1, " +
           "f.status = CASE WHEN f.refCount <= 1 THEN 'DELETED' ELSE f.status END " +
           "WHERE f.contentHash = :contentHash AND f.refCount > 0")
    int decrementRefCount(@Param("contentHash") String contentHash);

    @Query("SELECT f FROM FileInfoEntity f WHERE f.refCount <= 0 AND f.status = :status")
    List<FileInfoEntity> findGarbageFiles(@Param("status") FileStatus status, Pageable pageable);
}
