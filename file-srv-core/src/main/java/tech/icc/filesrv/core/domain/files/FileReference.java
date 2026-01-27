package tech.icc.filesrv.core.domain.files;

import tech.icc.filesrv.common.vo.audit.AuditInfo;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;

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
 * @param owner       所有者信息
 * @param access      访问控制
 * @param audit       审计信息
 */
public record FileReference(
        String fKey,
        String contentHash,
        String filename,
        String contentType,
        Long size,
        OwnerInfo owner,
        AccessControl access,
        AuditInfo audit
) {

    /**
     * 创建新的文件引用（未绑定内容）
     *
     * @param filename    文件名
     * @param contentType MIME 类型
     * @param size        文件大小
     * @param owner       所有者
     * @return 新文件引用（contentHash 为 null）
     */
    public static FileReference create(String filename, String contentType, Long size, OwnerInfo owner) {
        return new FileReference(
                UUID.randomUUID().toString(),  // TODO: 升级为 UUID v7
                null,  // 待绑定
                filename,
                contentType,
                size,
                owner,
                AccessControl.defaultAccess(),
                AuditInfo.now()
        );
    }

    /**
     * 绑定内容（上传完成后）
     *
     * @param contentHash 物理文件的 contentHash
     * @return 绑定后的文件引用
     */
    public FileReference bindContent(String contentHash) {
        return new FileReference(
                fKey, contentHash, filename, contentType, size, owner, access, audit
        );
    }

    /**
     * 更新访问控制
     */
    public FileReference withAccess(AccessControl newAccess) {
        return new FileReference(
                fKey, contentHash, filename, contentType, size, owner, newAccess, audit.touch()
        );
    }

    /**
     * 重命名文件
     */
    public FileReference rename(String newFilename) {
        return new FileReference(
                fKey, contentHash, newFilename, contentType, size, owner, access, audit.touch()
        );
    }

    /**
     * 文件是否已绑定内容
     */
    public boolean isBound() {
        return contentHash != null;
    }
}
