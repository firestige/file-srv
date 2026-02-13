package tech.icc.filesrv.core.application.entrypoint.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import tech.icc.filesrv.core.application.entrypoint.validation.UniqueFKey;

import java.util.Map;

/**
 * 文件上传请求
 * <p>
 * multipart/form-data 使用扁平字段绑定（不使用 VO 组合）。
 * 文件内容从 MultipartFile 获取。
 */
public class FileUploadRequest {

        /**
         * 用户自定义文件标识（可选）
         * <p>
         * 若不提供则自动生成 UUID。若提供则必须：
         * <ul>
         *   <li>长度 8-64 字符</li>
         *   <li>只包含字母、数字、连字符、下划线</li>
         *   <li>全局唯一（不与已有文件冲突）</li>
         * </ul>
         */
        @Pattern(regexp = "^[a-zA-Z0-9_-]{8,64}$",
                 message = "fKey 长度必须为 8-64 字符，只能包含字母数字连字符下划线")
        @UniqueFKey
        private String fKey;

        /** 文件名称（必填，由用户指定或前端处理后传入） */
        @NotBlank(message = "文件名不能为空")
        private String fileName;

        /** 文件 MIME 类型（必填） */
        @NotBlank(message = "文件类型不能为空")
        private String fileType;

        /** 创建者 ID（必填） */
        @NotBlank(message = "创建者不能为空")
        private String createdBy;

        /** 创建者名称（可选） */
        private String creatorName;

        /** 是否公开（可选，默认 false） */
        private Boolean isPublic;

        /** 文件标签（逗号分隔） */
        private String tags;

        /** 自定义元数据（key-value） */
        private Map<String, String> customMetadata;

        public String getFKey() {
                return fKey;
        }

        public void setFKey(String fKey) {
                this.fKey = fKey;
        }

        public String getFileName() {
                return fileName;
        }

        public void setFileName(String fileName) {
                this.fileName = fileName;
        }

        public String getFileType() {
                return fileType;
        }

        public void setFileType(String fileType) {
                this.fileType = fileType;
        }

        public String getCreatedBy() {
                return createdBy;
        }

        public void setCreatedBy(String createdBy) {
                this.createdBy = createdBy;
        }

        public String getCreatorName() {
                return creatorName;
        }

        public void setCreatorName(String creatorName) {
                this.creatorName = creatorName;
        }

        /**
         * 绑定 multipart/form-data 的 "public" 字段
         */
        public Boolean getPublic() {
                return isPublic;
        }

        public void setPublic(Boolean isPublic) {
                this.isPublic = isPublic;
        }

        public String getTags() {
                return tags;
        }

        public void setTags(String tags) {
                this.tags = tags;
        }

        public Map<String, String> getCustomMetadata() {
                return customMetadata;
        }

        public void setCustomMetadata(Map<String, String> customMetadata) {
                this.customMetadata = customMetadata;
        }
}
