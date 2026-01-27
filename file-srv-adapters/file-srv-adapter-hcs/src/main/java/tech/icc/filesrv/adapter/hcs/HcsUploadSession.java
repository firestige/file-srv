package tech.icc.filesrv.adapter.hcs;

import lombok.extern.slf4j.Slf4j;
import tech.icc.filesrv.core.domain.tasks.PartInfo;
import tech.icc.filesrv.core.infra.storage.UploadSession;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

/**
 * 华为云 OBS 分片上传会话实现
 * <p>
 * 封装 OBS SDK 的分片上传操作。
 */
@Slf4j
public class HcsUploadSession implements UploadSession {

    private final String bucket;
    private final String objectKey;
    private final String uploadId;
    
    // TODO: 注入 ObsClient
    // private final ObsClient obsClient;

    /**
     * 创建新的上传会话
     */
    public HcsUploadSession(String bucket, String objectKey, String contentType) {
        this.bucket = bucket;
        this.objectKey = objectKey;
        // TODO: 调用 initiateMultipartUpload 获取 uploadId
        this.uploadId = initUpload(contentType);
        log.info("Upload session created: bucket={}, key={}, uploadId={}", bucket, objectKey, uploadId);
    }

    /**
     * 恢复已有的上传会话
     */
    public HcsUploadSession(String bucket, String objectKey, String uploadId, boolean resume) {
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.uploadId = uploadId;
        log.debug("Upload session resumed: bucket={}, key={}, uploadId={}", bucket, objectKey, uploadId);
    }

    private String initUpload(String contentType) {
        // TODO: 实现 OBS initiateMultipartUpload
        // InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucket, objectKey);
        // request.setContentType(contentType);
        // InitiateMultipartUploadResult result = obsClient.initiateMultipartUpload(request);
        // return result.getUploadId();
        throw new UnsupportedOperationException("OBS initiate upload not implemented yet");
    }

    @Override
    public String getSessionId() {
        return uploadId;
    }

    @Override
    public String getPath() {
        return objectKey;
    }

    @Override
    public String uploadPart(int partNumber, InputStream data, long size) {
        log.debug("Uploading part: uploadId={}, partNumber={}, size={}", uploadId, partNumber, size);
        
        // TODO: 实现 OBS uploadPart
        // UploadPartRequest request = new UploadPartRequest(bucket, objectKey, uploadId, partNumber, data, size);
        // UploadPartResult result = obsClient.uploadPart(request);
        // return normalizeETag(result.getEtag());
        throw new UnsupportedOperationException("OBS upload part not implemented yet");
    }

    @Override
    public String complete(List<PartInfo> parts) {
        log.info("Completing upload: uploadId={}, parts={}", uploadId, parts.size());
        
        // 按 partNumber 排序
        List<PartInfo> sortedParts = parts.stream()
                .sorted(Comparator.comparingInt(PartInfo::partNumber))
                .toList();
        
        // TODO: 实现 OBS completeMultipartUpload
        // List<PartEtag> obsParts = sortedParts.stream()
        //         .map(p -> new PartEtag(p.etag(), p.partNumber()))
        //         .toList();
        // CompleteMultipartUploadRequest request = 
        //         new CompleteMultipartUploadRequest(bucket, objectKey, uploadId, obsParts);
        // obsClient.completeMultipartUpload(request);
        // return objectKey;
        throw new UnsupportedOperationException("OBS complete upload not implemented yet");
    }

    @Override
    public void abort() {
        log.info("Aborting upload: uploadId={}", uploadId);
        
        // TODO: 实现 OBS abortMultipartUpload
        // try {
        //     AbortMultipartUploadRequest request = 
        //             new AbortMultipartUploadRequest(bucket, objectKey, uploadId);
        //     obsClient.abortMultipartUpload(request);
        // } catch (ObsException e) {
        //     // 幂等处理：如果上传已不存在，忽略错误
        //     if (!"NoSuchUpload".equals(e.getErrorCode())) {
        //         throw e;
        //     }
        //     log.debug("Upload already aborted or completed: uploadId={}", uploadId);
        // }
        throw new UnsupportedOperationException("OBS abort upload not implemented yet");
    }

    @Override
    public void close() {
        // 资源释放（ObsClient 由外部管理，这里不关闭）
        log.debug("Upload session closed: uploadId={}", uploadId);
    }

    /**
     * 标准化 ETag（去除引号）
     */
    private String normalizeETag(String etag) {
        if (etag != null && etag.startsWith("\"") && etag.endsWith("\"")) {
            return etag.substring(1, etag.length() - 1);
        }
        return etag;
    }
}
