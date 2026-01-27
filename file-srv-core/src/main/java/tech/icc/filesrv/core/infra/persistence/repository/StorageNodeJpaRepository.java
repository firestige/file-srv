package tech.icc.filesrv.core.infra.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.icc.filesrv.core.domain.storage.NodeStatus;
import tech.icc.filesrv.core.infra.persistence.entity.StorageNodeEntity;

import java.util.List;
import java.util.Optional;

/**
 * 存储节点 JPA Repository
 */
public interface StorageNodeJpaRepository extends JpaRepository<StorageNodeEntity, String> {

    Optional<StorageNodeEntity> findByNodeId(String nodeId);

    @Query("SELECT n FROM StorageNodeEntity n WHERE n.status = :status")
    List<StorageNodeEntity> findByStatus(@Param("status") NodeStatus status);

    @Query("SELECT n FROM StorageNodeEntity n WHERE n.status IN ('ACTIVE')")
    List<StorageNodeEntity> findWritableNodes();
}
