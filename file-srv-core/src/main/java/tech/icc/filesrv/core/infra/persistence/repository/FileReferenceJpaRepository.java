package tech.icc.filesrv.core.infra.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tech.icc.filesrv.core.infra.persistence.entity.FileReferenceEntity;

import java.util.List;

/**
 * 文件引用 JPA Repository
 */
public interface FileReferenceJpaRepository extends 
        JpaRepository<FileReferenceEntity, String>,
        JpaSpecificationExecutor<FileReferenceEntity> {

    List<FileReferenceEntity> findByOwnerId(String ownerId);

    boolean existsByFKey(String fKey);
}
