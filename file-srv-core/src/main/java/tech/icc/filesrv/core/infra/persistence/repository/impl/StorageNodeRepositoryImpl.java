package tech.icc.filesrv.core.infra.persistence.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import tech.icc.filesrv.core.domain.storage.NodeStatus;
import tech.icc.filesrv.core.domain.storage.StorageNode;
import tech.icc.filesrv.core.domain.storage.StorageNodeRepository;
import tech.icc.filesrv.core.domain.storage.StorageTier;
import tech.icc.filesrv.core.infra.persistence.entity.StorageNodeEntity;
import tech.icc.filesrv.core.infra.persistence.repository.StorageNodeJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 存储节点仓储实现
 */
@Repository
@RequiredArgsConstructor
public class StorageNodeRepositoryImpl implements StorageNodeRepository {

    private final StorageNodeJpaRepository jpaRepository;

    @Override
    public Optional<StorageNode> findByNodeId(String nodeId) {
        return jpaRepository.findByNodeId(nodeId).map(StorageNodeEntity::toDomain);
    }

    @Override
    public List<StorageNode> findByStatus(NodeStatus status) {
        return jpaRepository.findByStatus(status).stream()
                .map(StorageNodeEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<StorageNode> findByTier(StorageTier tier) {
        // TODO: 添加 JPA 方法
        return jpaRepository.findAll().stream()
                .filter(e -> e.getTier() == tier)
                .map(StorageNodeEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<StorageNode> findWritableNodes() {
        return jpaRepository.findWritableNodes().stream()
                .map(StorageNodeEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<StorageNode> findAvailableNodes() {
        return jpaRepository.findAll().stream()
                .filter(e -> e.getStatus().isReadable())
                .map(StorageNodeEntity::toDomain)
                .collect(Collectors.toList());
    }
}
