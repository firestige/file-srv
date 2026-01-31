package tech.icc.filesrv.core.application.entrypoint.support;

import lombok.Getter;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tech.icc.filesrv.core.application.entrypoint.model.FileInfoResponse;
import tech.icc.filesrv.common.vo.audit.AuditInfo;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.common.vo.file.FileTags;
import tech.icc.filesrv.common.vo.file.StorageRef;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * S3-like 文件响应构建器
 * <p>
 * 统一管理文件下载响应的 HTTP Headers，遵循 S3 风格的元数据头规范。
 * 支持链式调用，简化 Controller 层代码。
 *
 * <h3>Header 规范</h3>
 * <ul>
 *   <li>标准头: Content-Type, Content-Length, Content-Disposition, ETag, Last-Modified</li>
 *   <li>自定义元数据: x-file-meta-* 前缀</li>
 *   <li>存储信息: x-file-storage-type, x-file-storage-location</li>
 * </ul>
 */
public class FileResponseBuilder {

    // ==================== Header 常量 ====================

    /** 自定义元数据头前缀 */
    public static final String HEADER_PREFIX_META = "x-file-meta-";

    /** 文件唯一标识 */
    public static final String HEADER_FILE_KEY = "x-file-key";

    /** 存储类型 */
    public static final String HEADER_STORAGE_TYPE = "x-file-storage-type";

    /** 存储位置 */
    public static final String HEADER_STORAGE_LOCATION = "x-file-storage-location";

    /** 创建者 ID */
    public static final String HEADER_CREATED_BY = "x-file-created-by";

    /** 创建者名称 */
    public static final String HEADER_CREATOR_NAME = "x-file-creator-name";

    /** 是否公开 */
    public static final String HEADER_PUBLIC = "x-file-public";

    /** 文件校验和 */
    public static final String HEADER_ETAG = "ETag";

    // ==================== 构建器实例 ====================

    @Getter
    private final HttpHeaders headers = new HttpHeaders();
    private MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
    private Long contentLength;
    private HttpStatus status = HttpStatus.OK;
    
    /**
     * 是否锁定 Content-Type（防止被文件类型覆盖）
     * 上传场景：响应体是 JSON，不应该被文件的 MIME 类型覆盖
     * 下载场景：响应体是文件流，应该使用文件的 MIME 类型
     */
    private boolean lockContentType = false;

    private FileResponseBuilder() {}

    /**
     * 创建下载响应构建器
     */
    public static FileResponseBuilder forDownload() {
        return new FileResponseBuilder();
    }

    /**
     * 创建上传成功响应构建器
     */
    public static FileResponseBuilder forUpload() {
        FileResponseBuilder builder = new FileResponseBuilder()
                .status(HttpStatus.CREATED);
        // 设置 JSON Content-Type 并锁定（防止被文件类型覆盖）
        builder.headers.setContentType(MediaType.APPLICATION_JSON);
        builder.lockContentType = true;  // 上传响应体是 JSON，不是文件内容
        return builder;
    }

    // ==================== 基础配置 ====================

    /**
     * 设置 HTTP 状态码
     */
    public FileResponseBuilder status(HttpStatus status) {
        this.status = status;
        return this;
    }

    /**
     * 从 FileInfoResponse 填充所有相关 Headers
     */
    public FileResponseBuilder fromFileInfo(FileInfoResponse fileInfo) {
        if (fileInfo == null) {
            return this;
        }

        Optional.ofNullable(fileInfo.identity()).ifPresent(this::applyIdentity);
        Optional.ofNullable(fileInfo.storageRef()).ifPresent(this::applyStorage);
        Optional.ofNullable(fileInfo.owner()).ifPresent(this::applyOwner);
        Optional.ofNullable(fileInfo.audit()).ifPresent(this::applyAudit);
        
        // 分别应用三个独立的 VO
        Optional.ofNullable(fileInfo.access()).ifPresent(this::applyAccessControl);
        Optional.ofNullable(fileInfo.fileTags()).ifPresent(this::applyTags);
        Optional.ofNullable(fileInfo.metadata()).ifPresent(this::applyMetadata);

        return this;
    }

    // ==================== 身份信息 ====================

    private void applyIdentity(FileIdentity identity) {
        Optional.ofNullable(identity.fKey()).ifPresent(k -> headers.set(HEADER_FILE_KEY, k));

        // 仅在未锁定时设置 Content-Type（下载场景使用文件类型，上传场景保持 JSON）
        if (!lockContentType) {
            Optional.ofNullable(identity.fileType()).ifPresent(type -> {
                this.contentType = MediaType.parseMediaType(type);
                headers.setContentType(this.contentType);
            });
        }

        Optional.ofNullable(identity.fileSize()).ifPresent(size -> {
            this.contentLength = size;
            headers.setContentLength(size);
        });
    }

    // ==================== 存储信息 ====================

    private void applyStorage(StorageRef storage) {
        Optional.ofNullable(storage.storageType())
                .ifPresent(t -> headers.set(HEADER_STORAGE_TYPE, t));
        Optional.ofNullable(storage.location())
                .ifPresent(l -> headers.set(HEADER_STORAGE_LOCATION, l));
        Optional.ofNullable(storage.eTag())
                .ifPresent(e -> {
                    headers.setETag("\"" + e + "\"");
                    headers.set(HEADER_ETAG, e);
                });
    }

    // ==================== 所有者信息 ====================

    private void applyOwner(OwnerInfo owner) {
        Optional.ofNullable(owner.createdBy())
                .ifPresent(c -> headers.set(HEADER_CREATED_BY, c));
        Optional.ofNullable(owner.creatorName())
                .ifPresent(n -> headers.set(HEADER_CREATOR_NAME, n));
    }

    // ==================== 审计信息 ====================

    private void applyAudit(AuditInfo audit) {
        Optional.ofNullable(audit.updatedAt()).ifPresent(time ->
                headers.setLastModified(time.toInstant()));
        // createdAt 可作为自定义头
        Optional.ofNullable(audit.createdAt()).ifPresent(time ->
                headers.set(HEADER_PREFIX_META + "created-at",
                        time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
    }

    // ==================== 访问控制 ====================

    private void applyAccessControl(AccessControl access) {
        Optional.ofNullable(access.isPublic())
                .ifPresent(p -> headers.set(HEADER_PUBLIC, String.valueOf(p)));
    }

    private void applyTags(FileTags fileTags) {
        Optional.ofNullable(fileTags.tags())
                .filter(t -> !t.isBlank())
                .ifPresent(t -> headers.set(HEADER_PREFIX_META + "tags", t));
    }

    private void applyMetadata(CustomMetadata metadata) {
        Optional.ofNullable(metadata.customMetadata()).ifPresent(meta ->
                meta.forEach((k, v) -> headers.set(HEADER_PREFIX_META + k, v)));
    }

    // ==================== 附加配置 ====================

    /**
     * 设置下载文件名（Content-Disposition: attachment）
     */
    public FileResponseBuilder attachment(String filename) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        headers.setContentDisposition(disposition);
        return this;
    }

    /**
     * 设置内联显示（Content-Disposition: inline）
     */
    public FileResponseBuilder inline(String filename) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        headers.setContentDisposition(disposition);
        return this;
    }

    /**
     * 设置缓存控制
     */
    public FileResponseBuilder cacheControl(CacheControl cacheControl) {
        headers.setCacheControl(cacheControl);
        return this;
    }

    /**
     * 设置默认缓存（私有，1小时）
     */
    public FileResponseBuilder defaultCache() {
        return cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate());
    }

    /**
     * 禁用缓存
     */
    public FileResponseBuilder noCache() {
        return cacheControl(CacheControl.noCache());
    }

    /**
     * 设置上传成功后的 Location 头
     */
    public FileResponseBuilder location(String resourceUri) {
        headers.set(HttpHeaders.LOCATION, resourceUri);
        return this;
    }

    /**
     * 添加自定义头
     */
    public FileResponseBuilder header(String name, String value) {
        headers.set(name, value);
        return this;
    }

    // ==================== 构建响应 ====================

    /**
     * 构建带 Resource 的下载响应
     */
    public ResponseEntity<Resource> build(Resource resource) {
        return ResponseEntity.status(status)
                .headers(headers)
                .body(resource);
    }

    /**
     * 构建带任意 Body 的响应
     */
    public <T> ResponseEntity<T> build(T body) {
        return ResponseEntity.status(status)
                .headers(headers)
                .body(body);
    }

    /**
     * 构建无 Body 的响应（如 204 No Content）
     */
    public ResponseEntity<Void> buildEmpty() {
        return ResponseEntity.status(status)
                .headers(headers)
                .build();
    }
}
