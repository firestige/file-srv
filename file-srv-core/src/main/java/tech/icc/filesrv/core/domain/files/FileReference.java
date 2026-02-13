package tech.icc.filesrv.core.domain.files;

import tech.icc.filesrv.common.vo.audit.AuditInfo;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileTags;

import java.util.UUID;

/**
 * 文件引用
 * <p>
 * 聚合根，表示用户视角的文件。
 * 每个用户上传的文件对应一个独立的 FileReference，即使内容相同（去重对用户透明）。
 *
 * @param fKey        用户文件唯一标识（UUID v7）
 * @param contentHash 关联的物理文件 contentHash（xxHash-64）
 * @param filename    用户可见文件名
 * @param contentType MIME 类型
 * @param size        文件大小（字节）
 * @param eTag        文件 ETag/校验和（用于完整性验证）
 * @param owner       所有者信息
 * @param access      访问控制
 * @param tags        文件标签
 * @param metadata    自定义元数据
 * @param audit       审计信息
 */
public record FileReference(
        String fKey,
        String contentHash,
        String filename,
        String contentType,
        Long size,
        String eTag,
        OwnerInfo owner,
        AccessControl access,
        FileTags tags,
        CustomMetadata metadata,
        AuditInfo audit
) {

    /**
     * 创建新的文件引用（未绑定内容）
     *
     * @param fKey        用户指定的 fKey（若为 null 或空白则自动生成）
     * @param filename    文件名
     * @param contentType MIME 类型
     * @param size        文件大小
     * @param owner       所有者
     * @param tags        文件标签
     * @param metadata    自定义元数据
     * @return 新文件引用（contentHash 为 null）
     */
    public static FileReference create(String fKey, String filename, String contentType, Long size, 
                                       OwnerInfo owner, FileTags tags, CustomMetadata metadata) {
        // 若未提供 fKey，则自动生成
        String finalFKey = (fKey == null || fKey.isBlank()) 
                ? UUID.randomUUID().toString()  // TODO: 升级为 UUID v7
                : fKey.trim();
        
        return new FileReference(
                finalFKey,
                null,  // 待绑定
                filename,
                contentType,
                size,
                null,  // eTag 待计算
                owner,
                AccessControl.defaultAccess(),
                tags != null ? tags : FileTags.empty(),
                metadata != null ? metadata : CustomMetadata.empty(),
                AuditInfo.now()
        );
    }

    /**
     * 创建新的文件引用（未绑定内容）- 向后兼容重载
     *
     * @param filename    文件名
     * @param contentType MIME 类型
     * @param size        文件大小
     * @param owner       所有者
     * @param tags        文件标签
     * @param metadata    自定义元数据
     * @return 新文件引用（contentHash 为 null）
     */
    public static FileReference create(String filename, String contentType, Long size, 
                                       OwnerInfo owner, FileTags tags, CustomMetadata metadata) {
        return create(null, filename, contentType, size, owner, tags, metadata);
    }

    /**
     * 绑定内容（上传完成后）
     *
     * @param contentHash 物理文件的 contentHash
     * @return 绑定后的文件引用
     */
    public FileReference bindContent(String contentHash) {
        return new FileReference(
                fKey, contentHash, filename, contentType, size, eTag, owner, access, tags, metadata, audit
        );
    }

    /**
     * 绑定内容和 ETag
     *
     * @param contentHash 物理文件的 contentHash
     * @param eTag        文件 ETag
     * @return 绑定后的文件引用
     */
    public FileReference bindContent(String contentHash, String eTag) {
        return new FileReference(
                fKey, contentHash, filename, contentType, size, eTag, owner, access, tags, metadata, audit
        );
    }

    /**
     * 更新访问控制
     */
    public FileReference withAccess(AccessControl newAccess) {
        return new FileReference(
                fKey, contentHash, filename, contentType, size, eTag, owner, newAccess, tags, metadata, audit.touch()
        );
    }

    /**
     * 重命名文件
     */
    public FileReference rename(String newFilename) {
        return new FileReference(
                fKey, contentHash, newFilename, contentType, size, eTag, owner, access, tags, metadata, audit.touch()
        );
    }

    /**
     * 更新内容类型
     */
    public FileReference withContentType(String newContentType) {
        return new FileReference(
                fKey, contentHash, filename, newContentType, size, eTag, owner, access, tags, metadata, audit.touch()
        );
    }

    /**
     * 更新标签
     */
    public FileReference withTags(FileTags newTags) {
        return new FileReference(
                fKey, contentHash, filename, contentType, size, eTag, owner, access, newTags, metadata, audit.touch()
        );
    }

    /**
     * 更新自定义元数据
     */
    public FileReference withMetadata(CustomMetadata newMetadata) {
        return new FileReference(
                fKey, contentHash, filename, contentType, size, eTag, owner, access, tags, newMetadata, audit.touch()
        );
    }

    /**
     * 文件是否已绑定内容
     */
    public boolean isBound() {
        return contentHash != null;
    }
}
