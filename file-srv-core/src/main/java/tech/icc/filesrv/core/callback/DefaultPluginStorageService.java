package tech.icc.filesrv.core.callback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;
import tech.icc.filesrv.common.spi.storage.StorageResult;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * 插件存储服务默认实现
 * <p>
 * 基于 {@link StorageAdapter} 实现，支持大文件分片上传和临时URL生成。
 * </p>
 */
@Slf4j
@Service
public class DefaultPluginStorageService implements PluginStorageService {

    private final StorageAdapter storageAdapter;

    /**
     * 分片上传阈值（默认5MB）
     * <p>
     * 文件大小超过此值时，使用分片上传策略
     * </p>
     */
    private static final long MULTIPART_THRESHOLD = 5 * 1024 * 1024; // 5MB

    /**
     * 每个分片的大小（默认5MB）
     */
    private static final long PART_SIZE = 5 * 1024 * 1024; // 5MB

    public DefaultPluginStorageService(StorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public String uploadLargeFile(InputStream inputStream, String fileName, String contentType, long fileSize) {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream cannot be null");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive");
        }

        try {
            log.info("Uploading large file: name={}, type={}, size={}", fileName, contentType, fileSize);

            // 根据文件大小选择上传策略
            String fkey;
            if (fileSize > MULTIPART_THRESHOLD) {
                fkey = uploadWithMultipart(inputStream, fileName, contentType, fileSize);
            } else {
                fkey = uploadDirect(inputStream, fileName, contentType, fileSize);
            }

            log.info("Upload completed: fkey={}", fkey);
            return fkey;
        } catch (Exception e) {
            log.error("Upload failed: name={}, size={}", fileName, fileSize, e);
            throw new StorageException("Failed to upload file: " + fileName, e);
        }
    }

    @Override
    public InputStream downloadFile(String fkey) {
        validateFkey(fkey);

        try {
            log.debug("Downloading file: fkey={}", fkey);
            Resource resource = storageAdapter.download(fkey);
            if (resource == null) {
                throw new FileNotFoundException(fkey);
            }
            return resource.getInputStream();
        } catch (IOException e) {
            log.error("Download failed (IO error): fkey={}", fkey, e);
            throw new StorageException("Failed to download file: " + fkey, e);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Download failed: fkey={}", fkey, e);
            throw new StorageException("Failed to download file: " + fkey, e);
        }
    }

    @Override
    public void deleteFile(String fkey) {
        validateFkey(fkey);

        try {
            log.info("Deleting file: fkey={}", fkey);
            storageAdapter.delete(fkey);
        } catch (Exception e) {
            log.error("Delete failed: fkey={}", fkey, e);
            throw new StorageException("Failed to delete file: " + fkey, e);
        }
    }

    @Override
    public String getTemporaryUrl(String fkey, Duration validity) {
        validateFkey(fkey);
        if (validity == null || validity.isNegative() || validity.isZero()) {
            throw new IllegalArgumentException("validity must be positive");
        }

        try {
            log.debug("Generating temporary URL: fkey={}, validity={}", fkey, validity);
            String url = storageAdapter.generatePresignedUrl(fkey, validity);
            if (url == null) {
                throw new StorageException("StorageAdapter returned null URL for fkey: " + fkey);
            }
            return url;
        } catch (Exception e) {
            log.error("Failed to generate temporary URL: fkey={}, validity={}", fkey, validity, e);
            throw new StorageException("Failed to generate temporary URL for: " + fkey, e);
        }
    }

    /**
     * 直接上传（小文件）
     */
    private String uploadDirect(InputStream inputStream, String fileName, String contentType, long fileSize) {
        // StorageAdapter.upload 返回 StorageResult，我们需要从中提取路径作为 fkey
        StorageResult result = storageAdapter.upload(fileName, inputStream, contentType);
        return result.path(); // 路径即为 fkey
    }

    /**
     * 分片上传（大文件）
     */
    private String uploadWithMultipart(InputStream inputStream, String fileName, String contentType, long fileSize) {
        // TODO: 实现真正的分片上传逻辑
        // 当前版本暂时使用直接上传，后续迭代补充
        log.warn("Multipart upload not fully implemented yet, falling back to direct upload");
        return uploadDirect(inputStream, fileName, contentType, fileSize);
    }

    private void validateFkey(String fkey) {
        if (fkey == null || fkey.trim().isEmpty()) {
            throw new IllegalArgumentException("fkey cannot be null or empty");
        }
    }
}
