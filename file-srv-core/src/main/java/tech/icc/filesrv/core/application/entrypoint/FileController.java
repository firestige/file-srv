package tech.icc.filesrv.core.application.entrypoint;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.icc.filesrv.common.constants.SystemConstant;
import tech.icc.filesrv.common.context.Result;
import tech.icc.filesrv.common.exception.FileNotFoundException;
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.core.application.entrypoint.assembler.FileInfoAssembler;
import tech.icc.filesrv.core.application.entrypoint.model.FileMeta;
import tech.icc.filesrv.core.application.entrypoint.model.FileInfoResponse;
import tech.icc.filesrv.core.application.entrypoint.model.FileUploadRequest;
import tech.icc.filesrv.core.application.entrypoint.model.MetaQueryRequest;
import tech.icc.filesrv.core.application.entrypoint.support.FileResponseBuilder;
import tech.icc.filesrv.core.application.service.FileService;
import tech.icc.filesrv.core.application.service.dto.FileInfoDto;

import java.time.Duration;
import java.util.Optional;

/**
 * 文件管理控制器
 * <p>
 * 提供文件的基础 CRUD 操作，包括上传、下载、删除和元数据查询。
 * 适用于小文件的直接上传场景，大文件请使用 {@link TaskController} 的分片上传功能。
 */
@Slf4j
@Validated
@RestController
@RequestMapping(SystemConstant.FILE_PATH)
@RequiredArgsConstructor
public class FileController {

    /** 文件标识最大长度 */
    private static final int MAX_FILE_KEY_LENGTH = 128;

    /** 预签名 URL 默认有效期（秒） */
    private static final int DEFAULT_PRESIGN_EXPIRY_SECONDS = 3600;

    /** 预签名 URL 最小有效期（秒） */
    private static final int MIN_PRESIGN_EXPIRY_SECONDS = 60;

    /** 预签名 URL 最大有效期（秒），7 天 */
    private static final int MAX_PRESIGN_EXPIRY_SECONDS = 604800;

    private final FileService service;

    /**
     * 下载文件
     * <p>
     * 根据文件唯一标识下载文件内容，响应头包含文件名、类型和大小信息。
     *
     * @param fileKey 文件唯一标识（1-128 字符）
     * @return 文件资源流，404 如果文件不存在
     */
    @GetMapping("/{fkey}")
    public ResponseEntity<Resource> getFile(
            @PathVariable("fkey")
            @NotBlank(message = "文件标识不能为空")
            @Size(max = MAX_FILE_KEY_LENGTH, message = "文件标识长度不能超过 128 字符")
            String fileKey) {
        log.info("[Download] Start, fileKey={}", fileKey);
        
        return service.getFileInfo(fileKey)
                .map(dto -> buildDownloadResponse(fileKey, dto))
                .orElseThrow(() -> {
                    log.warn("[StaticResource] File not found, fileKey={}", fileKey);
                    return FileNotFoundException.withoutStack("文件不存在: " + fileKey);
                });
    }

    private ResponseEntity<Resource> buildDownloadResponse(String fileKey, FileInfoDto dto) {
        String filename = Optional.ofNullable(dto.identity())
                .map(FileIdentity::fileName)
                .orElse(fileKey);

        Resource resource = service.download(fileKey);

        log.info("[Download] Success, fileKey={}, filename={}", fileKey, filename);

        FileInfoResponse response = FileInfoAssembler.toResponse(dto);
        return FileResponseBuilder.forDownload()
                .fromFileInfo(response)
                .attachment(filename)
                .defaultCache()
                .build(resource);
    }

    /**
     * 获取文件元数据
     * <p>
     * 根据文件唯一标识获取文件的元数据信息（不包含存储引用）。
     *
     * @param fileKey 文件唯一标识（1-128 字符）
     * @return 文件元数据（FileMeta）
     */
    @GetMapping("/{fkey}/metadata")
    public Result<FileMeta> getFileMetadata(
            @PathVariable("fkey")
            @NotBlank(message = "文件标识不能为空")
            @Size(max = MAX_FILE_KEY_LENGTH, message = "文件标识长度不能超过 128 字符")
            String fileKey) {
        log.info("[GetMetadata] Start, fileKey={}", fileKey);
        
        FileInfoDto dto = service.getFileInfo(fileKey)
                .orElseThrow(() -> {
                    log.warn("[GetMetadata] File not found, fileKey={}", fileKey);
                    return FileNotFoundException.withoutStack("文件不存在: " + fileKey);
                });
        
        FileMeta response = FileInfoAssembler.toFileMeta(dto);
        
        log.info("[GetMetadata] Success, fileKey={}", fileKey);
        return Result.success(response);
    }

    /**
     * 删除文件
     * <p>
     * 根据文件唯一标识删除文件及其元数据。
     *
     * @param fileKey 文件唯一标识（1-128 字符）
     * @return 204 No Content
     */
    @DeleteMapping("/{fkey}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable("fkey")
            @NotBlank(message = "文件标识不能为空")
            @Size(max = MAX_FILE_KEY_LENGTH, message = "文件标识长度不能超过 128 字符")
            String fileKey) {
        log.info("[Delete] Start, fileKey={}", fileKey);
        service.delete(fileKey);
        log.info("[Delete] Success, fileKey={}", fileKey);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取预签名 URL
     * <p>
     * 生成临时访问 URL，允许无需认证直接访问文件。适用于：
     * <ul>
     *   <li>前端直接下载（绕过服务端代理）</li>
     *   <li>分享临时链接给第三方</li>
     *   <li>CDN 回源场景</li>
     * </ul>
     * <p>
     * 返回的 URL 中已包含签名和过期时间参数，无需额外处理。
     *
     * @param fileKey   文件唯一标识（1-128 字符）
     * @param expiresIn URL 有效期（秒），默认 3600，范围 60-604800（7天）
     * @return 预签名 URL（包含签名和过期时间参数）
     */
    @GetMapping("/{fkey}/presign")
    public Result<String> getPresignedUrl(
            @PathVariable("fkey")
            @NotBlank(message = "文件标识不能为空")
            @Size(max = MAX_FILE_KEY_LENGTH, message = "文件标识长度不能超过 128 字符")
            String fileKey,
            @RequestParam(value = "expiresIn", required = false)
            @Min(value = MIN_PRESIGN_EXPIRY_SECONDS, message = "有效期最小为 60 秒")
            @Max(value = MAX_PRESIGN_EXPIRY_SECONDS, message = "有效期最大为 604800 秒（7天）")
            Integer expiresIn) {

        int expiry = expiresIn != null ? expiresIn : DEFAULT_PRESIGN_EXPIRY_SECONDS;
        log.info("[Presign] Start, fileKey={}, expiry={}s", fileKey, expiry);

        String url = service.getPresignedUrl(fileKey, Duration.ofSeconds(expiry));

        log.info("[Presign] Success, fileKey={}", fileKey);
        return Result.success(url);
    }

    /**
     * 上传文件（简单上传）
     * <p>
     * 适用于小文件的直接上传，文件大小受服务器配置限制。
     * 大文件建议使用 {@link TaskController#createTask} 创建分片上传任务。
     *
     * @param request 文件元数据信息
     * @param file     上传的文件
     * @return 201 Created，响应体包含文件信息，Location 头指向新资源
     */
    @PostMapping("/upload")
    public ResponseEntity<Result<FileInfoResponse>> uploadFile(
            @ModelAttribute FileUploadRequest request,
            @RequestParam("file") MultipartFile file) {
        log.info("[Upload] Start, filename={}, contentType={}, size={}", 
                file.getOriginalFilename(), file.getContentType(), file.getSize());
        
        FileInfoDto inputDto = FileInfoAssembler.toDto(request);
        FileInfoDto resultDto = service.upload(inputDto, file);
        FileInfoResponse response = FileInfoAssembler.toResponse(resultDto);
        String resourceUri = SystemConstant.FILE_PATH + "/" + response.identity().fKey();
        
        log.info("[Upload] Success, fileKey={}, filename={}, size={}", 
                response.identity().fKey(), response.identity().fileName(), response.identity().fileSize());

        return FileResponseBuilder.forUpload()
                .fromFileInfo(response)
                .location(resourceUri)
                .build(Result.success(response));
    }

    /**
     * 查询文件元数据
     * <p>
     * 根据查询条件分页检索文件元数据，支持按文件名、类型、所有者等条件筛选。
     *
     * @param request  查询条件（所有字段可选）
     * @param pageable 分页参数，默认 page=0, size=20, 最大 size=100
     * @return 分页的文件元数据列表
     */
    @PostMapping("/metadata")
    public Result<Page<FileMeta>> queryMetadata(
            @Valid @RequestBody MetaQueryRequest request,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        // 限制最大分页大小，防止一次查询过多数据
        int maxPageSize = 100;
        if (pageable.getPageSize() > maxPageSize) {
            log.warn("[QueryMetadata] Page size {} exceeds max {}, using max", pageable.getPageSize(), maxPageSize);
            pageable = PageRequest.of(
                    pageable.getPageNumber(), maxPageSize, pageable.getSort());
        }

        log.info("[QueryMetadata] Start, request={}, page={}, size={}",
                request, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<FileInfoDto> dtoPage = service.queryMetadata(FileInfoAssembler.toCriteria(request), pageable);
        Page<FileMeta> responsePage = dtoPage.map(FileInfoAssembler::toFileMeta);
        
        log.info("[QueryMetadata] Success, totalElements={}, totalPages={}, currentSize={}",
                responsePage.getTotalElements(), responsePage.getTotalPages(), responsePage.getNumberOfElements());
        return Result.success(responsePage);
    }
}
