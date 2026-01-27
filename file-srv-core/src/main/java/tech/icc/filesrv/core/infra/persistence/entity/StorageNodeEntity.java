package tech.icc.filesrv.core.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tech.icc.filesrv.core.domain.storage.NodeStatus;
import tech.icc.filesrv.core.domain.storage.StorageNode;
import tech.icc.filesrv.core.domain.storage.StorageTier;

import java.time.Instant;

/**
 * 存储节点 JPA 实体
 */
@Entity
@Table(name = "storage_node")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageNodeEntity {

    @Id
    @Column(name = "node_id", length = 32)
    private String nodeId;

    @Column(name = "node_name", nullable = false, length = 64)
    private String nodeName;

    @Column(name = "adapter_type", nullable = false, length = 32)
    private String adapterType;

    @Column(name = "endpoint", length = 256)
    private String endpoint;

    @Column(name = "bucket", length = 64)
    private String bucket;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 16)
    private StorageTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private NodeStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * 从领域对象转换
     */
    public static StorageNodeEntity fromDomain(StorageNode node) {
        return StorageNodeEntity.builder()
                .nodeId(node.nodeId())
                .nodeName(node.name())
                .adapterType(node.adapterType())
                .endpoint(node.endpoint())
                .bucket(node.bucket())
                .tier(node.tier())
                .status(node.status())
                .createdAt(java.time.Instant.now())
                .build();
    }

    /**
     * 转换为领域对象
     */
    public StorageNode toDomain() {
        return new StorageNode(nodeId, nodeName, adapterType, tier, status, endpoint, bucket);
    }
}
