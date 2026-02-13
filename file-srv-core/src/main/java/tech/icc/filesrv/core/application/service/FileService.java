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
import tech.icc.filesrv.common.context.PendingActivationsContext;
import tech.icc.filesrv.common.exception.DataCorruptedException;
import tech.icc.filesrv.common.exception.NotFoundException;
import tech.icc.filesrv.common.exception.validation.FileNotReadyException;
import tech.icc.filesrv.common.exception.FileServiceException;
import tech.icc.filesrv.common.exception.validation.PayloadTooLargeException;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.common.vo.file.FileMetadataUpdate;
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
import java.util.Map;
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

        // 1. 提取元数据（fileName 和 fileType 从请求参数获取，已通过校验）
        OwnerInfo owner = Optional.ofNullable(fileInfo.owner()).orElse(OwnerInfo.system());
        AccessControl access = Optional.ofNullable(fileInfo.access()).orElse(AccessControl.defaultAccess());
        FileTags fileTags = Optional.ofNullable(fileInfo.fileTags()).orElse(FileTags.empty());
        CustomMetadata metadata = Optional.ofNullable(fileInfo.metadata()).orElse(CustomMetadata.empty());
        
        // 使用请求参数中的 fileName 和 fileType（符合 API 规范）
        String filename = fileInfo.identity().fileName();
        String contentType = fileInfo.identity().fileType();
        long size = file.getSize();

        // 2. 创建文件引用（PENDING 状态），包含 tags 和 customMetadata
        FileReference reference = FileReference.create(filename, contentType, size, owner, fileTags, metadata);
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
     * 创建 PENDING 状态的文件引用
     * <p>
     * 用于异步上传任务开始时预先创建文件元数据。
     * 文件此时还未上传，状态为 PENDING（未绑定 contentHash）。
     *
     * @param fKey        文件唯一标识（由调用方生成，通常是任务的 fKey）
     * @param filename    文件名
     * @param size        文件大小
     * @param contentType MIME 类型
     * @param owner       所有者信息
     * @param tags        文件标签
     * @param metadata    自定义元数据
     * @return 创建的文件引用
     */
    @Transactional
    public FileReference createPendingFile(String fKey, String filename, Long size, 
                                           String contentType, OwnerInfo owner, 
                                           FileTags tags, CustomMetadata metadata) {
        log.debug("Creating pending file: fKey={}, filename={}", fKey, filename);
        
        // 创建 PENDING 状态的 FileReference（未绑定 contentHash）
        FileReference reference = FileReference.create(filename, contentType, size, owner, tags, metadata);
        
        // 使用指定的 fKey（而不是自动生成）
        reference = new FileReference(
                fKey,
                null,  // contentHash 待绑定
                reference.filename(),
                reference.contentType(),
                reference.size(),
                null,  // eTag 待绑定
                reference.owner(),
                reference.access(),
                reference.tags(),
                reference.metadata(),
                reference.audit()
        );
        
        reference = fileReferenceRepository.save(reference);
        log.info("Pending file created: fKey={}", fKey);
        
        return reference;
    }

    /**
     * 激活文件（上传完成后）
     * <p>
     * 将 PENDING 状态的 FileReference 绑定到物理文件（FileInfo），
     * 并创建存储副本信息。支持去重：若 contentHash 已存在则复用。
     *
     * @param fKey        文件唯一标识
     * @param contentHash 内容哈希
     * @param storagePath 存储路径
     * @param nodeId      存储节点 ID
     * @return 激活后的文件引用
     */
    @Transactional
    public FileReference activateFile(String fKey, String contentHash, 
                                      String storagePath, String nodeId) {
        log.debug("Activating file: fKey={}, contentHash={}", fKey, contentHash);
        
        // 1. 获取 PENDING 的 FileReference
        FileReference reference = fileReferenceRepository.findByFKey(fKey)
                .orElseThrow(() -> new NotFoundException.FileNotFoundException(fKey));
        
        if (reference.isBound()) {
            log.warn("File already activated: fKey={}", fKey);
            return reference;
        }
        
        // 2. 查找或创建 FileInfo
        FileInfo fileInfo;
        Optional<FileInfo> existingInfo = fileInfoRepository.findByContentHash(contentHash);
        if (existingInfo.isPresent()) {
            // 去重：已存在相同 contentHash，增加引用计数
            log.info("File deduplication: contentHash={}", contentHash);
            fileInfo = deduplicationService.incrementReference(contentHash);
        } else {
            // 新文件：创建 FileInfo 并添加存储副本
            FileInfo newInfo = FileInfo.createPending(
                    contentHash, 
                    reference.size(), 
                    reference.contentType()
            );
            StorageCopy copy = StorageCopy.create(nodeId, storagePath);
            newInfo = newInfo.activate(copy);
            fileInfo = fileInfoRepository.save(newInfo);
        }
        
        // 3. 绑定 contentHash 到 FileReference
        reference = reference.bindContent(contentHash, contentHash);  // 使用 contentHash 作为 eTag
        reference = fileReferenceRepository.save(reference);
        
        log.info("File activated: fKey={}, contentHash={}", fKey, contentHash);
        
        return reference;
    }

    /**
     * 批量激活文件
     * <p>
     * 用于批量处理衍生文件的激活，减少数据库交互次数，提高性能。
     * 采用延迟激活机制：衍生文件先创建 PENDING 状态，在 callback chain 结束后统一激活。
     *
     * @param activations 待激活信息 Map：fKey -> PendingActivation(contentHash, storagePath, nodeId)
     * @throws IllegalArgumentException 如果 activations 为空或包含无效数据
     */
    @Transactional
    public void activateFilesInBatch(Map<String, PendingActivationsContext.PendingActivation> activations) {
        if (activations == null || activations.isEmpty()) {
            log.debug("No pending activations to process");
            return;
        }
        
        log.info("Batch activating {} files", activations.size());
        int successCount = 0;
        int failureCount = 0;
        
        for (var entry : activations.entrySet()) {
            String fKey = entry.getKey();
            var activation = entry.getValue();
            
            try {
                activateFile(fKey, activation.contentHash(), activation.storagePath(), activation.nodeId());
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to activate file in batch: fKey={}, hash={}", fKey, activation.contentHash(), e);
                // 继续处理其他文件，不中断整个批次
            }
        }
        
        log.info("Batch activation completed: success={}, failure={}", successCount, failureCount);
        
        if (failureCount > 0) {
            throw new FileServiceException(
                    ResultCode.INTERNAL_ERROR,
                    String.format("Batch activation partially failed: %d/%d files activated successfully", 
                            successCount, activations.size()),
                    null
            );
        }
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
                .orElseThrow(() -> new NotFoundException.FileNotFoundException(fileKey));

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
     * @throws NotFoundException.FileNotFoundException   文件不存在
     * @throws FileNotReadyException   文件未就绪（PENDING 状态）
     * @throws DataCorruptedException  数据一致性异常（物理文件丢失或无可用副本）
     */
    private StorageAccess resolveStorageAccess(String fileKey) {
        FileReference reference = fileReferenceRepository.findByFKey(fileKey)
                .orElseThrow(() -> new NotFoundException.FileNotFoundException(fileKey));

        if (!reference.isBound()) {
            throw new FileNotReadyException(fileKey);
        }

        FileInfo fileInfo = fileInfoRepository.findByContentHash(reference.contentHash())
                .orElseThrow(() -> new DataCorruptedException("Physical file missing: " + reference.contentHash()));

        StorageCopy primaryCopy = fileInfo.getPrimaryCopy()
                .orElseThrow(() -> new DataCorruptedException("No available copy: " + reference.contentHash()));

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
                .fileTags(ref.tags())
                .metadata(ref.metadata())
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

    /**
     * 应用元数据更新到文件引用
     * <p>
     * 供 Callback 执行器调用，将 Plugin 的元数据变更持久化到数据库。
     *
     * @param fKey   文件唯一标识
     * @param update 元数据更新
     * @return 更新后的文件引用
     */
    @Transactional
    public FileReference applyMetadataUpdate(String fKey, FileMetadataUpdate update) {
        if (update == null || !update.hasUpdates()) {
            log.debug("No metadata updates for fKey={}", fKey);
            return fileReferenceRepository.findByFKey(fKey)
                    .orElseThrow(() -> new NotFoundException.FileNotFoundException(fKey));
        }

        log.info("Applying metadata updates: fKey={}, update={}", fKey, update);

        FileReference ref = fileReferenceRepository.findByFKey(fKey)
                .orElseThrow(() -> new NotFoundException.FileNotFoundException(fKey));

        // 应用更新
        if (update.filename() != null) {
            ref = ref.rename(update.filename());
        }
        if (update.contentType() != null) {
            ref = ref.withContentType(update.contentType());
        }
        if (update.tags() != null) {
            ref = ref.withTags(update.tags());
        }
        if (update.customMetadata() != null) {
            ref = ref.withMetadata(update.customMetadata());
        }

        ref = fileReferenceRepository.save(ref);
        log.info("Metadata updated: fKey={}", fKey);

        return ref;
    }
}
