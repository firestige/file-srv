package tech.icc.filesrv.common.vo.file;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件元数据更新（用于 Plugin Callback 场景）
 * <p>
 * 使用 Builder 模式提供类型安全的元数据更新 API
 */
public record FileMetadataUpdate(String filename, String contentType, FileTags tags, CustomMetadata customMetadata) {
    /**
     * 检查是否有任何更新
     */
    public boolean hasUpdates() {
        return filename != null || contentType != null || tags != null || customMetadata != null;
    }

    public static FileMetadataUpdateBuilder builder() {
        return new FileMetadataUpdateBuilder();
    }

    /**
     * Builder 扩展：设置标签（空格分隔字符串）
     */
    public static class FileMetadataUpdateBuilder {
        private String filename;
        private String contentType;
        private FileTags tags;
        private CustomMetadata customMetadata;

        public FileMetadataUpdateBuilder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public FileMetadataUpdateBuilder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        /**
         * 设置标签（空格分隔）
         */
        public FileMetadataUpdateBuilder tags(String tagsString) {
            this.tags = tagsString != null ? new FileTags(tagsString) : null;
            return this;
        }

        /**
         * 设置自定义元数据（Map）
         */
        public FileMetadataUpdateBuilder customMetadata(Map<String, String> metadata) {
            this.customMetadata = metadata != null ? new CustomMetadata(metadata) : null;
            return this;
        }

        /**
         * 合并自定义元数据（追加或覆盖键值对）
         */
        public FileMetadataUpdateBuilder mergeMetadata(String key, String value) {
            if (this.customMetadata == null) {
                this.customMetadata = CustomMetadata.of(Map.of(key, value));
            } else {
                // CustomMetadata 是不可变的，需要创建新 Map
                var newMap = new HashMap<>(this.customMetadata.customMetadata());
                newMap.put(key, value);
                this.customMetadata = CustomMetadata.of(newMap);
            }
            return this;
        }

        public FileMetadataUpdate build() {
            return new FileMetadataUpdate(filename, contentType, tags, customMetadata);
        }
    }
}
