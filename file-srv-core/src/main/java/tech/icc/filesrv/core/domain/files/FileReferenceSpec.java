package tech.icc.filesrv.core.domain.files;

/**
 * 文件引用查询规格
 * <p>
 * 用于构建复杂查询条件。
 *
 * @param ownerId     所有者 ID 过滤
 * @param contentType MIME 类型过滤
 * @param filenamePattern 文件名模式（支持通配符）
 */
public record FileReferenceSpec(
        String ownerId,
        String contentType,
        String filenamePattern
) {
    /**
     * 空规格（不过滤）
     */
    public static FileReferenceSpec empty() {
        return new FileReferenceSpec(null, null, null);
    }

    /**
     * 按所有者过滤
     */
    public static FileReferenceSpec byOwner(String ownerId) {
        return new FileReferenceSpec(ownerId, null, null);
    }
}
