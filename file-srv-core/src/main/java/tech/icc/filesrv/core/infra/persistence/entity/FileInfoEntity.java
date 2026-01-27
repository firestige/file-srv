package tech.icc.filesrv.core.infra.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tech.icc.filesrv.core.domain.files.FileInfo;
import tech.icc.filesrv.core.domain.files.FileStatus;
import tech.icc.filesrv.core.domain.storage.CopyStatus;
import tech.icc.filesrv.core.domain.storage.StorageCopy;
import tech.icc.filesrv.core.domain.storage.StorageTier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 物理文件信息 JPA 实体
 */
@Entity
@Table(name = "file_info")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoEntity {

    @Id
    @Column(name = "content_hash", length = 32)
    private String contentHash;

    @Column(name = "size")
    private Long size;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "ref_count")
    private Integer refCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private FileStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "storage_copy", joinColumns = @JoinColumn(name = "content_hash"))
    @Builder.Default
    private List<StorageCopyEmbeddable> copies = new ArrayList<>();

    /**
     * 从领域对象转换
     */
    public static FileInfoEntity fromDomain(FileInfo info) {
        List<StorageCopyEmbeddable> copyEntities = info.copies().stream()
                .map(StorageCopyEmbeddable::fromDomain)
                .collect(Collectors.toList());

        return FileInfoEntity.builder()
                .contentHash(info.contentHash())
                .size(info.size())
                .contentType(info.contentType())
                .refCount(info.refCount())
                .status(info.status())
                .createdAt(info.createdAt())
                .copies(copyEntities)
                .build();
    }

    /**
     * 转换为领域对象
     */
    public FileInfo toDomain() {
        List<StorageCopy> domainCopies = copies.stream()
                .map(StorageCopyEmbeddable::toDomain)
                .collect(Collectors.toList());

        return new FileInfo(
                contentHash,
                size,
                contentType,
                refCount,
                status,
                domainCopies,
                createdAt
        );
    }
}
