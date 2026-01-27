package tech.icc.filesrv.adapter.hcs;

import com.obs.services.ObsClient;
import com.obs.services.model.*;
import lombok.extern.slf4j.Slf4j;
import tech.icc.filesrv.core.infra.storage.StorageAdapter;
import tech.icc.filesrv.adapter.model.FileMetadata;
import tech.icc.filesrv.adapter.model.PartETag;
import tech.icc.filesrv.adapter.hcs.config.ObsProperties;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * 华为云 OBS 存储适配器实现
 */
@Slf4j
public class HcsObsAdapter implements StorageAdapter {

    private final ObsClient obsClient;
    private final String bucketName;
    private final Duration presignedUrlExpiration;

    public HcsObsAdapter(ObsClient obsClient, ObsProperties properties) {
        this.obsClient = obsClient;
        this.bucketName = properties.getBucketName();
        this.presignedUrlExpiration = properties.getPresignedUrlExpiration() != null
                ? properties.getPresignedUrlExpiration()
                : Duration.ofHours(1);
    }

    /**
     * 移除 ETag 字符串两端的引号
     * OBS SDK 返回的 ETag 通常带有引号，需要移除
     * 
     * @param etag 原始 ETag 字符串
     * @return 移除引号后的 ETag
     */
    private String normalizeETag(String etag) {
        if (etag == null) {
            return null;
        }
        // 移除开头和结尾的引号
        if (etag.startsWith("\"") && etag.endsWith("\"") && etag.length() > 1) {
            return etag.substring(1, etag.length() - 1);
        }
        return etag;
    }

    @Override
    public String uploadFile(InputStream in, FileMetadata meta) {
        try {
            // 使用 objectKey 或生成新的
            String objectKey = meta.getObjectKey();
            if (objectKey == null || objectKey.isBlank()) {
                objectKey = java.util.UUID.randomUUID().toString();
            }
            
            // 构建 PutObjectRequest
            PutObjectRequest request = new PutObjectRequest(bucketName, objectKey, in);
            
            // 设置对象元数据
            if (meta.getContentType() != null) {
                ObjectMetadata objectMetadata = new ObjectMetadata();
                objectMetadata.setContentType(meta.getContentType());
                
                // 添加自定义元数据
                if (meta.getFilename() != null) {
                    objectMetadata.addUserMetadata("filename", meta.getFilename());
                }
                if (meta.getCreator() != null) {
                    objectMetadata.addUserMetadata("creator", meta.getCreator());
                }
                if (meta.getTags() != null && !meta.getTags().isEmpty()) {
                    objectMetadata.addUserMetadata("tags", String.join(",", meta.getTags()));
                }
                
                request.setMetadata(objectMetadata);
            }
            
            // 上传文件
            PutObjectResult result = obsClient.putObject(request);
            
            log.info("File uploaded successfully to OBS: bucket={}, key={}, etag={}", 
                    bucketName, objectKey, normalizeETag(result.getEtag()));
            
            return objectKey;
            
        } catch (Exception e) {
            log.error("Failed to upload file to OBS", e);
            throw new RuntimeException("Failed to upload file to OBS: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream downloadFile(String fkey) {
        try {
            ObsObject obsObject = obsClient.getObject(bucketName, fkey);
            
            log.info("File downloaded successfully from OBS: bucket={}, key={}", bucketName, fkey);
            
            return obsObject.getObjectContent();
            
        } catch (Exception e) {
            log.error("Failed to download file from OBS: key={}", fkey, e);
            throw new RuntimeException("Failed to download file from OBS: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String fkey, Duration expiration) {
        try {
            long expirationSeconds = (expiration != null ? expiration : presignedUrlExpiration).getSeconds();
            
            TemporarySignatureRequest request = new TemporarySignatureRequest(HttpMethodEnum.GET, expirationSeconds);
            request.setBucketName(bucketName);
            request.setObjectKey(fkey);
            
            TemporarySignatureResponse response = obsClient.createTemporarySignature(request);
            String signedUrl = response.getSignedUrl();
            
            log.info("Presigned URL generated for OBS: key={}, expiration={}s", fkey, expirationSeconds);
            
            return signedUrl;
            
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for OBS: key={}", fkey, e);
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String fkey) {
        try {
            obsClient.deleteObject(bucketName, fkey);
            
            log.info("File deleted successfully from OBS: bucket={}, key={}", bucketName, fkey);
            
        } catch (Exception e) {
            log.error("Failed to delete file from OBS: key={}", fkey, e);
            throw new RuntimeException("Failed to delete file from OBS: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String fkey) {
        try {
            return obsClient.doesObjectExist(bucketName, fkey);
            
        } catch (Exception e) {
            log.error("Failed to check file existence in OBS: key={}", fkey, e);
            throw new RuntimeException("Failed to check file existence: " + e.getMessage(), e);
        }
    }

    // ==================== 分片上传方法（迭代2实现）====================

    @Override
    public String initiateMultipartUpload(FileMetadata meta) {
        try {
            // 使用 objectKey 或生成新的
            String objectKey = meta.getObjectKey();
            if (objectKey == null || objectKey.isBlank()) {
                objectKey = java.util.UUID.randomUUID().toString();
            }
            
            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucketName, objectKey);
            
            // 设置元数据
            if (meta.getContentType() != null) {
                ObjectMetadata objectMetadata = new ObjectMetadata();
                objectMetadata.setContentType(meta.getContentType());
                
                // 添加自定义元数据
                if (meta.getFilename() != null) {
                    objectMetadata.addUserMetadata("filename", meta.getFilename());
                }
                if (meta.getCreator() != null) {
                    objectMetadata.addUserMetadata("creator", meta.getCreator());
                }
                if (meta.getTags() != null && !meta.getTags().isEmpty()) {
                    objectMetadata.addUserMetadata("tags", String.join(",", meta.getTags()));
                }
                
                request.setMetadata(objectMetadata);
            }
            
            InitiateMultipartUploadResult result = obsClient.initiateMultipartUpload(request);
            String uploadId = result.getUploadId();
            
            log.info("Multipart upload initiated: bucket={}, key={}, uploadId={}", 
                    bucketName, objectKey, uploadId);
            
            return uploadId;
            
        } catch (Exception e) {
            log.error("Failed to initiate multipart upload", e);
            throw new RuntimeException("Failed to initiate multipart upload: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadPart(String objectKey, String uploadId, int partNumber, InputStream in, long partSize) {
        try {
            UploadPartRequest request = new UploadPartRequest();
            request.setBucketName(bucketName);
            request.setObjectKey(objectKey);
            request.setUploadId(uploadId);
            request.setPartNumber(partNumber);
            request.setInput(in);
            request.setPartSize(partSize);
            
            UploadPartResult result = obsClient.uploadPart(request);
            String etag = normalizeETag(result.getEtag());
            
            log.info("Part uploaded: bucket={}, key={}, uploadId={}, partNumber={}, etag={}", 
                    bucketName, objectKey, uploadId, partNumber, etag);
            
            return etag;
            
        } catch (Exception e) {
            log.error("Failed to upload part: uploadId={}, partNumber={}", uploadId, partNumber, e);
            throw new RuntimeException("Failed to upload part: " + e.getMessage(), e);
        }
    }

    @Override
    public void completeMultipartUpload(String objectKey, String uploadId, List<PartETag> parts) {
        try {
            // 转换 PartETag 为 OBS SDK 的 PartEtag
            List<com.obs.services.model.PartEtag> obsParts = parts.stream()
                    .map(p -> {
                        com.obs.services.model.PartEtag obsPart = new com.obs.services.model.PartEtag();
                        obsPart.setPartNumber(p.getPartNumber());
                        obsPart.seteTag(p.getETag());
                        return obsPart;
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                    bucketName, objectKey, uploadId, obsParts);
            
            CompleteMultipartUploadResult result = obsClient.completeMultipartUpload(request);
            
            log.info("Multipart upload completed: bucket={}, key={}, uploadId={}, etag={}", 
                    bucketName, objectKey, uploadId, normalizeETag(result.getEtag()));
            
        } catch (Exception e) {
            log.error("Failed to complete multipart upload: uploadId={}", uploadId, e);
            throw new RuntimeException("Failed to complete multipart upload: " + e.getMessage(), e);
        }
    }

    @Override
    public void abortMultipartUpload(String objectKey, String uploadId) {
        try {
            AbortMultipartUploadRequest request = new AbortMultipartUploadRequest(bucketName, objectKey, uploadId);
            obsClient.abortMultipartUpload(request);
            
            log.info("Multipart upload aborted: bucket={}, key={}, uploadId={}", bucketName, objectKey, uploadId);
            
        } catch (Exception e) {
            // 404/NoSuchUpload 视为幂等成功
            if (e.getMessage() != null && e.getMessage().contains("NoSuchUpload")) {
                log.info("Multipart upload already aborted or not found: uploadId={}", uploadId);
                return;
            }
            log.error("Failed to abort multipart upload: uploadId={}", uploadId, e);
            throw new RuntimeException("Failed to abort multipart upload: " + e.getMessage(), e);
        }
    }
}

