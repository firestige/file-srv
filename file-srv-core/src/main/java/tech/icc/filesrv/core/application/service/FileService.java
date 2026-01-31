package tech.icc.filesrv.core.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tech.icc.filesrv.common.constants.ResultCode;
import tech.icc.filesrv.common.exception.DataCorruptedException;
import tech.icc.filesrv.common.exception.validation.FileNotFoundException;
import tech.icc.filesrv.common.exception.validation.FileNotReadyException;
import tech.icc.filesrv.common.exception.FileServiceException;
import tech.icc.filesrv.common.exception.validation.PayloadTooLargeException;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.common.vo.file.FileTags;
import tech.icc.filesrv.common.vo.file.StorageRef;
import tech.icc.filesrv.core.application.service.dto.FileInfoDto;
import tech.icc.filesrv.core.application.service.dto.MetaQueryCriteria;
import tech.icc.filesrv.core.domain.files.FileInfo;
import tech.icc.filesrv.core.domain.files.FileInfoRepository;
import tech.icc.filesrv.core.domain.files.FileReference;
import tech.icc.filesrv.core.domain.files.FileReferenceRepository;
import tech.icc.filesrv.core.domain.files.FileReferenceSpec;
import tech.icc.filesrv.core.domain.services.DeduplicationService;
import tech.icc.filesrv.core.domain.services.StorageRoutingService;
import tech.icc.filesrv.core.domain.storage.StorageCopy;
import tech.icc.filesrv.core.domain.storage.StorageNode;
import tech.icc.filesrv.core.domain.storage.StoragePolicy;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;
import tech.icc.filesrv.common.spi.storage.StorageResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/**
 * 文件应用服务
 * <p>
 * 编排领域对象完成文件相关用例，使用应用层 DTO 进行数据传输。
 * <p>
 * 上传流程:
 * <ol>
 *   <li>创建 FileReference (PENDING)</li>
 *   <li>计算 contentHash</li>
 *   <li>秒传检查：若存在相同 hash，增加引用计数</li>
 *   <li>若不存在：上传至存储，创建 FileInfo</li>
 *   <li>绑定 contentHash，更新状态为 ACTIVE</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /**
     * 同步上传最大文件大小：10MB
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final FileReferenceRepository fileReferenceRepository;
    private final FileInfoRepository fileInfoRepository;
    private final DeduplicationService deduplicationService;
    private final StorageRoutingService storageRoutingService;
    private final MeterRegistry meterRegistry;

    /**
     * 上传文件
     * <p>
     * 支持内容去重（秒传）：若已存在相同内容，直接增加引用计数。
     * <p>
     * 注意：此接口有 10MB SLA 限制，文件直接加载到内存处理。
     *
     * @param fileInfo 文件信息（包含 owner、access 等元数据）
     * @param file     上传的文件（不超过 10MB）
     * @return 保存后的文件信息
     */
    @Transactional
    public FileInfoDto upload(FileInfoDto fileInfo, MultipartFile file) {
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean instant = false;
        String status = "failure";
        
        log.debug("Starting upload: filename={}, size={}", file.getOriginalFilename(), file.getSize());

        // 0. 文件大小校验
        if (file.getSize() > MAX_FILE_SIZE) {
            counter("file.upload.rejected", "reason", "size_limit").increment();
            throw new PayloadTooLargeException(file.getSize(), MAX_FILE_SIZE);
        }

        // 1. 提取元数据
        OwnerInfo owner = Optional.ofNullable(fileInfo.owner()).orElse(OwnerInfo.system());
        AccessControl access = Optional.ofNullable(fileInfo.access()).orElse(AccessControl.defaultAccess());
        FileTags fileTags = Optional.ofNullable(fileInfo.fileTags()).orElse(FileTags.empty());
        CustomMetadata metadata = Optional.ofNullable(fileInfo.metadata()).orElse(CustomMetadata.empty());
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long size = file.getSize();

        // 2. 创建文件引用（PENDING 状态）
        FileReference reference = FileReference.create(filename, contentType, size, owner);
        reference = fileReferenceRepository.save(reference);
        log.debug("Created file reference: fKey={}", reference.fKey());

        try {
            // 3. 读取到内存并计算哈希（10MB 以内，内存方案最优）
            byte[] content = file.getBytes();
            String contentHash = deduplicationService.computeHash(content);
            log.debug("Computed content hash: {}", contentHash);

            // 4. 秒传检查
            Optional<FileInfo> existingFile = deduplicationService.findByContentHash(contentHash);
            FileInfo physicalFile;

            if (existingFile.isPresent()) {
                // 秒传：增加引用计数
                log.info("Instant upload detected: contentHash={}", contentHash);
                physicalFile = deduplicationService.incrementReference(contentHash);
                instant = true;
            } else {
                // 需要实际上传：从内存上传
                physicalFile = uploadToStorage(contentHash, contentType, size, content);
            }

            // 5. 绑定 contentHash 到引用
            reference = reference.bindContent(contentHash);
            reference = reference.withAccess(access);
            reference = fileReferenceRepository.save(reference);

            status = "success";
            log.info("Upload completed: fKey={}, contentHash={}, instant={}",
                    reference.fKey(), contentHash, instant);

            return toDto(reference, physicalFile);

        } catch (IOException e) {
            status = "io_error";
            log.error("Upload IO error: fKey={}", reference.fKey(), e);
            cleanupFailedUpload(reference.fKey());
            throw new FileServiceException(ResultCode.INTERNAL_ERROR, "File upload failed", e);
            
        } catch (RuntimeException e) {
            // 捕获所有运行时异常（包括超时、数据库异常等）
            status = "runtime_error";
            log.error("Upload runtime error: fKey={}", reference.fKey(), e);
            cleanupFailedUpload(reference.fKey());
            throw e;
            
        } finally {
            // Metric 埋点
            sample.stop(timer("file.upload.duration", "status", status, "instant", String.valueOf(instant)));
            counter("file.upload.total", "status", status, "instant", String.valueOf(instant)).increment();
            meterRegistry.summary("file.upload.size.bytes").record(size);
        }
    }

    /**
     * 从内存上传到存储
     */
    private FileInfo uploadToStorage(String contentHash, String contentType, long size,
                                     byte[] content) throws IOException {
        // 选择存储节点
        StoragePolicy policy = StoragePolicy.defaultPolicy();
        StorageNode node = storageRoutingService.selectNode(policy);
        StorageAdapter adapter = storageRoutingService.getAdapter(node.nodeId());

        // 构建存储路径
        String storagePath = storageRoutingService.buildStoragePath(contentHash, contentType);

        // 从内存上传
        StorageResult result;
        try (InputStream uploadStream = new ByteArrayInputStream(content)) {
            result = adapter.upload(storagePath, uploadStream, contentType);
        }
        log.debug("File uploaded to storage: path={}", result.path());

        // 创建存储副本
        StorageCopy copy = StorageCopy.create(node.nodeId(), result.path());

        // 创建并激活 FileInfo
        FileInfo fileInfo = FileInfo.createPending(contentHash, size, contentType);
        fileInfo = fileInfo.activate(copy);
        return fileInfoRepository.save(fileInfo);
    }

    /**
     * 下载文件
     *
     * @param fileKey 文件唯一标识 (fKey)
     * @return 文件资源
     */
    @Transactional(readOnly = true)
    public Resource download(String fileKey) {
        log.debug("Downloading file: fKey={}", fileKey);

        StorageAccess access = resolveStorageAccess(fileKey);
        return access.adapter().download(access.copy().path());
    }

    /**
     * 根据文件标识获取文件信息
     *
     * @param fileKey 文件唯一标识 (fKey)
     * @return 文件信息，不存在时返回 empty
     */
    @Transactional(readOnly = true)
    public Optional<FileInfoDto> getFileInfo(String fileKey) {
        return fileReferenceRepository.findByFKey(fileKey)
                .flatMap(ref -> {
                    if (!ref.isBound()) {
                        return Optional.of(toDto(ref, null));
                    }
                    return fileInfoRepository.findByContentHash(ref.contentHash())
                            .map(info -> toDto(ref, info));
                });
    }

    /**
     * 删除文件
     * <p>
     * 删除用户的文件引用，并减少物理文件的引用计数。
     * 当引用计数归零时，由后台 GC 任务清理物理文件。
     *
     * @param fileKey 文件唯一标识 (fKey)
     */
    @Transactional
    public void delete(String fileKey) {
        log.debug("Deleting file: fKey={}", fileKey);

        FileReference reference = fileReferenceRepository.findByFKey(fileKey)
                .orElseThrow(() -> new FileNotFoundException(fileKey));

        // 减少引用计数
        if (reference.isBound()) {
            boolean canGC = deduplicationService.decrementReference(reference.contentHash());
            log.debug("Reference count decremented: contentHash={}, canGC={}",
                    reference.contentHash(), canGC);
        }

        // 删除文件引用
        fileReferenceRepository.deleteByFKey(fileKey);
        log.info("File deleted: fKey={}", fileKey);
    }

    /**
     * 获取预签名 URL
     *
     * @param fileKey 文件唯一标识 (fKey)
     * @param expire  有效期
     * @return 预签名 URL
     */
    @Transactional(readOnly = true)
    public String getPresignedUrl(String fileKey, Duration expire) {
        log.debug("Generating presigned URL: fKey={}, expire={}", fileKey, expire);

        StorageAccess access = resolveStorageAccess(fileKey);
        return access.adapter().generatePresignedUrl(access.copy().path(), expire);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 清理失败的上传
     */
    private void cleanupFailedUpload(String fKey) {
        try {
            fileReferenceRepository.deleteByFKey(fKey);
            log.debug("Cleaned up failed upload: fKey={}", fKey);
        } catch (Exception e) {
            log.error("Failed to cleanup upload: fKey={}", fKey, e);
        }
    }

    /**
     * 获取或创建 Counter
     */
    private Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    /**
     * 获取或创建 Timer
     */
    private Timer timer(String name, String... tags) {
        return Timer.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    /**
     * 解析文件的存储访问信息
     *
     * @param fileKey 文件唯一标识 (fKey)
     * @return 存储访问信息（包含副本和适配器）
     * @throws FileNotFoundException   文件不存在
     * @throws FileNotReadyException   文件未就绪（PENDING 状态）
     * @throws DataCorruptedException  数据一致性异常（物理文件丢失或无可用副本）
     */
    private StorageAccess resolveStorageAccess(String fileKey) {
        FileReference reference = fileReferenceRepository.findByFKey(fileKey)
                .orElseThrow(() -> new FileNotFoundException(fileKey));

        if (!reference.isBound()) {
            throw new FileNotReadyException(fileKey);
        }

        FileInfo fileInfo = fileInfoRepository.findByContentHash(reference.contentHash())
                .orElseThrow(() -> DataCorruptedException.withoutStack("Physical file missing: " + reference.contentHash()));

        StorageCopy primaryCopy = fileInfo.getPrimaryCopy()
                .orElseThrow(() -> DataCorruptedException.withoutStack("No available copy: " + reference.contentHash()));

        StorageAdapter adapter = storageRoutingService.getAdapter(primaryCopy.nodeId());

        return new StorageAccess(primaryCopy, adapter);
    }

    /**
     * 存储访问信息
     */
    private record StorageAccess(StorageCopy copy, StorageAdapter adapter) {
    }

    /**
     * 查询文件元数据
     *
     * @param criteria 查询条件
     * @param pageable 分页参数
     * @return 分页的文件信息
     */
    @Transactional(readOnly = true)
    public Page<FileInfoDto> queryMetadata(MetaQueryCriteria criteria, Pageable pageable) {
        FileReferenceSpec spec = toSpec(criteria);
        return fileReferenceRepository.findAll(spec, pageable)
                .map(ref -> {
                    if (!ref.isBound()) {
                        return toDto(ref, null);
                    }
                    FileInfo info = fileInfoRepository.findByContentHash(ref.contentHash())
                            .orElse(null);
                    return toDto(ref, info);
                });
    }

    // ==================== DTO 转换 ====================

    /**
     * 领域对象转 DTO
     */
    private FileInfoDto toDto(FileReference ref, FileInfo info) {
        FileIdentity identity = FileIdentity.builder()
                .fKey(ref.fKey())
                .fileName(ref.filename())
                .fileType(ref.contentType())
                .fileSize(ref.size())
                .eTag(ref.eTag())
                .build();

        StorageRef storageRef = null;
        if (info != null && !info.copies().isEmpty()) {
            StorageCopy copy = info.getPrimaryCopy().orElse(info.copies().get(0));
            storageRef = StorageRef.builder()
                    .storageType(copy.nodeId())  // Phase 1: nodeId 即 type
                    .path(copy.path())
                    .eTag(info.contentHash())
                    .build();
        }

        return FileInfoDto.builder()
                .identity(identity)
                .storageRef(storageRef)
                .owner(ref.owner())
                .audit(ref.audit())
                .access(ref.access())
                .build();
    }

    /**
     * 查询条件转规约
     */
    private FileReferenceSpec toSpec(MetaQueryCriteria criteria) {
        return new FileReferenceSpec(
                criteria.creator(),
                criteria.name(),
                criteria.contentType(),
                criteria.createdAfter().atStartOfDay(),
                criteria.createdBefore().atStartOfDay()
        );
    }
}
