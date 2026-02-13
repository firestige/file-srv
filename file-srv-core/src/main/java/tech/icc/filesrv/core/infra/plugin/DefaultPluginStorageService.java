package tech.icc.filesrv.core.infra.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tech.icc.filesrv.common.constants.ResultCode;
import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.exception.FileServiceException;
import tech.icc.filesrv.common.exception.NotFoundException;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileTags;
import tech.icc.filesrv.common.spi.plugin.PluginStorageService;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;
import tech.icc.filesrv.common.spi.storage.StorageResult;
import tech.icc.filesrv.core.application.service.FileService;
import tech.icc.filesrv.core.domain.services.DeduplicationService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * 插件存储服务默认实现
 * <p>
 * 基于 {@link StorageAdapter} 实现，支持大文件分片上传和临时URL生成。
 * 衍生文件采用延迟激活机制：先创建 PENDING FileReference，在 callback chain 结束后批量激活。
 * </p>
 */
@Slf4j
@Service
public class DefaultPluginStorageService implements PluginStorageService {

    private final StorageAdapter storageAdapter;
    private final DeduplicationService deduplicationService;
    private final FileService fileService;

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

    public DefaultPluginStorageService(StorageAdapter storageAdapter,
                                        DeduplicationService deduplicationService,
                                        FileService fileService) {
        this.storageAdapter = storageAdapter;
        this.deduplicationService = deduplicationService;
        this.fileService = fileService;
    }

    @Override
    public String uploadLargeFile(TaskContext context, InputStream inputStream, 
                                  String fileName, String contentType, long fileSize) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream cannot be null");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive");
        }

        try {
            log.info("Uploading derived file: name={}, type={}, size={}", fileName, contentType, fileSize);

            // 1. 生成衍生文件的 fKey
            String fKey = generateDerivedFileKey();
            
            // 2. 计算 xxHash（使用临时缓冲区避免多次读取）
            ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) Math.min(fileSize, 10 * 1024 * 1024));
            String contentHash = deduplicationService.computeHashAndCopy(inputStream, buffer);
            byte[] content = buffer.toByteArray();
            log.debug("Content hash computed: fKey={}, hash={}", fKey, contentHash);
            
            // 3. 上传到存储（使用 buffer 内容）
            String storagePath = uploadDirect(content, fileName, contentType);
            String nodeId = "default"; // TODO: 从 StorageAdapter 获取
            log.debug("File uploaded to storage: fKey={}, path={}", fKey, storagePath);
            
            // 4. 创建 PENDING 状态的 FileReference
            OwnerInfo owner = new OwnerInfo(
                    context.executionInfo().getOwnerId().orElse(null),
                    context.executionInfo().getOwnerName().orElse(null)
            );
            fileService.createPendingFile(fKey, fileName, fileSize, contentType, 
                    owner, FileTags.empty(), CustomMetadata.empty());
            log.debug("Pending file reference created: fKey={}", fKey);
            
            // 5. 记录待激活信息（由 CallbackChainRunner 统一激活）
            context.pendingActivations().add(fKey, contentHash, storagePath, nodeId);
            log.info("Derived file upload completed (pending activation): fKey={}, hash={}", fKey, contentHash);
            
            return fKey;
            
        } catch (Exception e) {
            log.error("Upload failed: name={}, size={}", fileName, fileSize, e);
            throw new FileServiceException(ResultCode.INTERNAL_ERROR, "Failed to upload file: " + fileName, e);
        }
    }
    
    /**
     * 生成衍生文件的 fKey
     * <p>
     * 格式：UUID（32字符，不含连字符），符合数据库字段长度限制（36字符）
     */
    private String generateDerivedFileKey() {
        // 直接使用 UUID（去掉连字符后32字符），不加前缀避免超过字段长度
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public InputStream downloadFile(String fkey) {
        validateFkey(fkey);

        try {
            log.debug("Downloading file: fkey={}", fkey);
            Resource resource = storageAdapter.download(fkey);
            if (resource == null) {
                throw new NotFoundException.FileNotFoundException(fkey);
            }
            return resource.getInputStream();
        } catch (IOException e) {
            log.error("Download failed (IO error): fkey={}", fkey, e);
            throw new FileServiceException(ResultCode.INTERNAL_ERROR, "Failed to download file: " + fkey, e);
        } catch (NotFoundException.FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Download failed: fkey={}", fkey, e);
            throw new FileServiceException(ResultCode.INTERNAL_ERROR, "Failed to download file: " + fkey, e);
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
            throw new FileServiceException(ResultCode.INTERNAL_ERROR, "Failed to delete file: " + fkey, e);
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
                throw new FileServiceException(ResultCode.INTERNAL_ERROR, "StorageAdapter returned null URL for fkey: " + fkey, null);
            }
            return url;
        } catch (Exception e) {
            log.error("Failed to generate temporary URL: fkey={}, validity={}", fkey, validity, e);
            throw new FileServiceException(ResultCode.INTERNAL_ERROR, "Failed to generate temporary URL for: " + fkey, e);
        }
    }

    /**
     * 直接上传（小文件）
     */
    private String uploadDirect(byte[] content, String fileName, String contentType) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            StorageResult result = storageAdapter.upload(fileName, inputStream, contentType);
            return result.path(); // 路径即为 storage path
        }
    }

    private void validateFkey(String fkey) {
        if (fkey == null || fkey.trim().isEmpty()) {
            throw new IllegalArgumentException("fkey cannot be null or empty");
        }
    }
}
