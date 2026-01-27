package tech.icc.filesrv.core.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tech.icc.filesrv.core.domain.storage.CopyStatus;
import tech.icc.filesrv.core.domain.storage.StorageCopy;
import tech.icc.filesrv.core.domain.storage.StorageTier;

import java.time.Instant;

/**
 * 存储副本嵌入式对象
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageCopyEmbeddable {

    @Column(name = "copy_id", length = 36)
    private String copyId;

    @Column(name = "node_id", length = 32)
    private String nodeId;

    @Column(name = "path", length = 512)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 16)
    private StorageTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "copy_status", length = 16)
    private CopyStatus status;

    @Column(name = "copy_created_at")
    private Instant createdAt;

    /**
     * 从领域对象转换
     */
    public static StorageCopyEmbeddable fromDomain(StorageCopy copy) {
        return StorageCopyEmbeddable.builder()
                .copyId(copy.copyId())
                .nodeId(copy.nodeId())
                .path(copy.path())
                .tier(copy.tier())
                .status(copy.status())
                .createdAt(copy.createdAt())
                .build();
    }

    /**
     * 转换为领域对象
     */
    public StorageCopy toDomain() {
        return new StorageCopy(copyId, nodeId, path, tier, status, createdAt);
    }
}
