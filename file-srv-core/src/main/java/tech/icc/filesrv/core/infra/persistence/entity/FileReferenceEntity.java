package tech.icc.filesrv.core.infra.persistence.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tech.icc.filesrv.common.vo.audit.AuditInfo;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileTags;
import tech.icc.filesrv.core.domain.files.FileReference;
import tech.icc.filesrv.core.domain.files.FileStatus;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 文件引用 JPA 实体
 */
@Entity
@Table(name = "file_reference")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileReferenceEntity {

    @Id
    @Column(name = "f_key", length = 36)
    private String fKey;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "size")
    private Long size;

    @Column(name = "etag", length = 128)
    private String eTag;

    @Column(name = "owner_id", length = 64)
    private String ownerId;

    @Column(name = "owner_name", length = 128)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private FileStatus status;

    @Column(name = "is_public")
    private Boolean isPublic;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_metadata", columnDefinition = "JSON")
    private Map<String, String> customMetadata;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * 从领域对象转换
     */
    public static FileReferenceEntity fromDomain(FileReference ref) {
        return FileReferenceEntity.builder()
                .fKey(ref.fKey())
                .contentHash(ref.contentHash())
                .filename(ref.filename())
                .contentType(ref.contentType())
                .size(ref.size())
                .eTag(ref.eTag())
                .ownerId(ref.owner() != null ? ref.owner().createdBy() : null)
                .ownerName(ref.owner() != null ? ref.owner().creatorName() : null)
                .status(ref.contentHash() != null ? FileStatus.ACTIVE : FileStatus.PENDING)
                .isPublic(ref.access() != null && ref.access().isPublic())
                .tags(ref.tags() != null ? ref.tags().tags() : null)
                .customMetadata(ref.metadata() != null ? ref.metadata().customMetadata() : null)
                .createdAt(ref.audit() != null ? ref.audit().createdAt() : OffsetDateTime.now())
                .updatedAt(ref.audit() != null ? ref.audit().updatedAt() : OffsetDateTime.now())
                .build();
    }

    /**
     * 转换为领域对象
     */
    public FileReference toDomain() {
        return new FileReference(
                fKey,
                contentHash,
                filename,
                contentType,
                size,
                eTag,
                new OwnerInfo(ownerId, ownerName),
                new AccessControl(isPublic != null && isPublic),
                tags != null ? new FileTags(tags) : FileTags.empty(),
                customMetadata != null ? new CustomMetadata(customMetadata) : CustomMetadata.empty(),
                new AuditInfo(createdAt, updatedAt)
        );
    }
}
