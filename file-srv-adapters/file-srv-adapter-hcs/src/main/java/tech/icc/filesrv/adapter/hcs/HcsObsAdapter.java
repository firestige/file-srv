package tech.icc.filesrv.adapter.hcs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import tech.icc.filesrv.core.infra.storage.StorageAdapter;
import tech.icc.filesrv.core.infra.storage.StorageResult;
import tech.icc.filesrv.core.infra.storage.UploadSession;

import java.io.InputStream;
import java.time.Duration;

/**
 * 华为云 OBS 存储适配器实现
 * <p>
 * TODO: 实现具体的 OBS SDK 调用
 */
@Slf4j
public class HcsObsAdapter implements StorageAdapter {

    private static final String ADAPTER_TYPE = "HCS_OBS";

    private final String bucket;
    
    // TODO: 注入 ObsClient
    // private final ObsClient obsClient;

    public HcsObsAdapter(String bucket) {
        this.bucket = bucket;
    }

    @Override
    public String getAdapterType() {
        return ADAPTER_TYPE;
    }

    @Override
    public StorageResult upload(String path, InputStream content, String contentType) {
        // TODO: 实现 OBS 上传
        throw new UnsupportedOperationException("HCS OBS upload not implemented yet");
    }

    @Override
    public Resource download(String path) {
        // TODO: 实现 OBS 下载
        throw new UnsupportedOperationException("HCS OBS download not implemented yet");
    }

    @Override
    public void delete(String path) {
        // TODO: 实现 OBS 删除
        throw new UnsupportedOperationException("HCS OBS delete not implemented yet");
    }

    @Override
    public boolean exists(String path) {
        // TODO: 实现 OBS 存在检查
        throw new UnsupportedOperationException("HCS OBS exists not implemented yet");
    }

    @Override
    public String generatePresignedUrl(String path, Duration expiry) {
        // TODO: 实现 OBS 预签名 URL
        throw new UnsupportedOperationException("HCS OBS presigned URL not implemented yet");
    }

    @Override
    public UploadSession beginUpload(String path, String contentType) {
        log.info("Beginning upload session: bucket={}, path={}, contentType={}", bucket, path, contentType);
        return new HcsUploadSession(bucket, path, contentType);
    }

    @Override
    public UploadSession resumeUpload(String path, String sessionId) {
        log.debug("Resuming upload session: bucket={}, path={}, sessionId={}", bucket, path, sessionId);
        return new HcsUploadSession(bucket, path, sessionId, true);
    }
}

