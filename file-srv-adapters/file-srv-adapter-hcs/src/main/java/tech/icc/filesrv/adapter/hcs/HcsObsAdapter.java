package tech.icc.filesrv.adapter.hcs;

import com.obs.services.ObsClient;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.PutObjectResult;
import com.obs.services.model.TemporarySignatureRequest;
import com.obs.services.model.TemporarySignatureResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;
import tech.icc.filesrv.common.spi.storage.StorageResult;
import tech.icc.filesrv.common.spi.storage.UploadSession;

import java.io.InputStream;
import java.time.Duration;

/**
 * 华为云 OBS 存储适配器实现
 */
@Slf4j
public class HcsObsAdapter implements StorageAdapter {

    private static final String ADAPTER_TYPE = "HCS_OBS";

    private final ObsClient obsClient;
    private final String bucket;
    private final Duration defaultPresignedExpiry;

    public HcsObsAdapter(ObsClient obsClient, String bucket, Duration defaultPresignedExpiry) {
        this.obsClient = obsClient;
        this.bucket = bucket;
        this.defaultPresignedExpiry = defaultPresignedExpiry;
    }

    @Override
    public String getAdapterType() {
        return ADAPTER_TYPE;
    }

    @Override
    public StorageResult upload(String path, InputStream content, String contentType) {
        log.debug("Uploading to OBS: bucket={}, path={}, contentType={}", bucket, path, contentType);
        
        PutObjectRequest request = new PutObjectRequest(bucket, path, content);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        request.setMetadata(metadata);
        
        PutObjectResult result = obsClient.putObject(request);
        
        log.info("OBS upload completed: bucket={}, path={}, etag={}", bucket, path, result.getEtag());
        
        // size 由调用方提供，这里返回 null
        return StorageResult.of(path, normalizeETag(result.getEtag()), null);
    }

    @Override
    public Resource download(String path) {
        log.debug("Downloading from OBS: bucket={}, path={}", bucket, path);
        
        ObsObject obsObject = obsClient.getObject(bucket, path);
        InputStream inputStream = obsObject.getObjectContent();
        
        return new InputStreamResource(inputStream);
    }

    @Override
    public void delete(String path) {
        log.debug("Deleting from OBS: bucket={}, path={}", bucket, path);
        
        obsClient.deleteObject(bucket, path);
        
        log.info("OBS delete completed: bucket={}, path={}", bucket, path);
    }

    @Override
    public boolean exists(String path) {
        log.debug("Checking existence in OBS: bucket={}, path={}", bucket, path);
        
        return obsClient.doesObjectExist(bucket, path);
    }

    @Override
    public String generatePresignedUrl(String path, Duration expiry) {
        Duration actualExpiry = expiry != null ? expiry : defaultPresignedExpiry;
        log.debug("Generating presigned URL: bucket={}, path={}, expiry={}", bucket, path, actualExpiry);
        
        TemporarySignatureRequest request = new TemporarySignatureRequest(HttpMethodEnum.GET, actualExpiry.getSeconds());
        request.setBucketName(bucket);
        request.setObjectKey(path);
        
        TemporarySignatureResponse response = obsClient.createTemporarySignature(request);
        
        return response.getSignedUrl();
    }

    @Override
    public UploadSession beginUpload(String path, String contentType) {
        log.info("Beginning upload session: bucket={}, path={}, contentType={}", bucket, path, contentType);
        return new HcsUploadSession(obsClient, bucket, path, contentType);
    }

    @Override
    public UploadSession resumeUpload(String path, String sessionId) {
        log.debug("Resuming upload session: bucket={}, path={}, sessionId={}", bucket, path, sessionId);
        return new HcsUploadSession(obsClient, bucket, path, sessionId, true);
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

