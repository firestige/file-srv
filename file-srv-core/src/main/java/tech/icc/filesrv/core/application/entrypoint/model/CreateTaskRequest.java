package tech.icc.filesrv.core.application.entrypoint.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tech.icc.filesrv.common.vo.task.CallbackConfig;

import java.util.List;
import java.util.Map;

/**
 * 创建上传任务请求
 * <p>
 * 使用扁平字段结构以支持 JSON 反序列化。
 * FileRequest 的字段被展开到此类中。
 */
public class CreateTaskRequest {

    /** 文件名（必填） */
    @NotBlank(message = "文件名不能为空")
    private String filename;

    /** MIME 类型（必填） */
    @NotBlank(message = "内容类型不能为空")
    private String contentType;

    /** 文件大小（必填） */
    @NotNull(message = "文件大小不能为空")
    private Long size;

    /** 文件内容 Hash（必填，客户端计算的 SHA-256）*/
    @NotBlank(message = "文件hash不能为空")
    private String contentHash;

    /** 期望的 ETag/校验和（可选） */
    private String eTag;

    /** 创建者 ID（必填） */
    @NotBlank(message = "创建者不能为空")
    private String createdBy;

    /** 创建者名称（可选） */
    private String creatorName;

    /** 是否公开（可选，默认 false） */
    private Boolean isPublic;

    /** 文件标签（逗号分隔，可选） */
    private String tags;

    /** 自定义元数据（可选） */
    private Map<String, String> customMetadata;

    /** 回调配置列表（可选） */
    @Valid
    private List<CallbackConfig> callbacks;

    // ==================== Getters and Setters ====================

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getETag() {
        return eTag;
    }

    public void setETag(String eTag) {
        this.eTag = eTag;
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
     * 获取是否公开标志
     * （绑定 JSON 中的 "public" 字段）
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

    public List<CallbackConfig> getCallbacks() {
        return callbacks;
    }

    public void setCallbacks(List<CallbackConfig> callbacks) {
        this.callbacks = callbacks;
    }
}
