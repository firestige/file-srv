package tech.icc.filesrv.adapter.hcs;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.*;
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

    private final ObsClient obsClient;
    private final String bucket;
    private final String objectKey;
    private final String uploadId;

    /**
     * 创建新的上传会话
     */
    public HcsUploadSession(ObsClient obsClient, String bucket, String objectKey, String contentType) {
        this.obsClient = obsClient;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.uploadId = initUpload(contentType);
        log.info("Upload session created: bucket={}, key={}, uploadId={}", bucket, objectKey, uploadId);
    }

    /**
     * 恢复已有的上传会话
     * 
     * @param obsClient OBS 客户端
     * @param bucket    桶名称
     * @param objectKey 对象键
     * @param uploadId  上传 ID
     * @param resume    恢复标志（用于区分构造函数）
     */
    public HcsUploadSession(ObsClient obsClient, String bucket, String objectKey, String uploadId, boolean resume) {
        this.obsClient = obsClient;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.uploadId = uploadId;
        log.debug("Upload session resumed: bucket={}, key={}, uploadId={}", bucket, objectKey, uploadId);
    }

    private String initUpload(String contentType) {
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucket, objectKey);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        request.setMetadata(metadata);
        
        InitiateMultipartUploadResult result = obsClient.initiateMultipartUpload(request);
        return result.getUploadId();
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
        
        UploadPartRequest request = new UploadPartRequest(bucket, objectKey);
        request.setUploadId(uploadId);
        request.setPartNumber(partNumber);
        request.setInput(data);
        request.setPartSize(size);
        
        UploadPartResult result = obsClient.uploadPart(request);
        String etag = normalizeETag(result.getEtag());
        
        log.debug("Part uploaded: uploadId={}, partNumber={}, etag={}", uploadId, partNumber, etag);
        return etag;
    }

    @Override
    public String complete(List<PartInfo> parts) {
        log.info("Completing upload: uploadId={}, parts={}", uploadId, parts.size());
        
        // 按 partNumber 排序并转换为 OBS PartEtag
        List<PartEtag> obsParts = parts.stream()
                .sorted(Comparator.comparingInt(PartInfo::partNumber))
                .map(p -> new PartEtag(p.etag(), p.partNumber()))
                .toList();
        
        CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                bucket, objectKey, uploadId, obsParts);
        
        CompleteMultipartUploadResult result = obsClient.completeMultipartUpload(request);
        
        log.info("Upload completed: uploadId={}, path={}, etag={}", 
                uploadId, result.getObjectKey(), result.getEtag());
        
        return result.getObjectKey();
    }

    @Override
    public void abort() {
        log.info("Aborting upload: uploadId={}", uploadId);
        
        try {
            AbortMultipartUploadRequest request = new AbortMultipartUploadRequest(bucket, objectKey, uploadId);
            obsClient.abortMultipartUpload(request);
            log.info("Upload aborted: uploadId={}", uploadId);
        } catch (ObsException e) {
            // 幂等处理：如果上传已不存在，忽略错误
            if ("NoSuchUpload".equals(e.getErrorCode())) {
                log.debug("Upload already aborted or completed: uploadId={}", uploadId);
            } else {
                throw e;
            }
        }
    }

    @Override
    public void close() {
        // 资源释放（ObsClient 由外部管理，这里不关闭）
        log.debug("Upload session closed: uploadId={}", uploadId);
    }

    /**
     * 标准化 ETag（移除引号）
     */
    private String normalizeETag(String etag) {
        if (etag == null) {
            return null;
        }
        return etag.replace("\"", "");
    }
}
