package tech.icc.filesrv.core.application.entrypoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.core.application.entrypoint.model.FileInfo;
import tech.icc.filesrv.core.application.entrypoint.model.MetaQueryParams;
import tech.icc.filesrv.core.application.service.FileService;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 文件管理控制器
 * <p>
 * 提供文件的基础 CRUD 操作，包括上传、下载、删除和元数据查询。
 * 适用于小文件的直接上传场景，大文件请使用 {@link TaskController} 的分片上传功能。
 */
@Slf4j
@RestController
@RequestMapping(SystemConstant.FILE_PATH)
@RequiredArgsConstructor
public class FileController {
    private final FileService service;

    /**
     * 下载文件
     * <p>
     * 根据文件唯一标识下载文件内容，响应头包含文件名、类型和大小信息。
     *
     * @param fileKey 文件唯一标识
     * @return 文件资源流，404 如果文件不存在
     */
    @GetMapping("/{fkey}")
    public ResponseEntity<Resource> getFile(@PathVariable("fkey") String fileKey) {
        log.info("[Download] Start, fileKey={}", fileKey);
        
        FileInfo info = service.getFileInfo(fileKey);
        if (info == null) {
            log.warn("[Download] File not found, fileKey={}", fileKey);
            return ResponseEntity.notFound().build();
        }

        Resource resource = service.download(fileKey);
        FileIdentity identity = info.identity();
        
        String filename = Optional.ofNullable(identity.fileName()).orElse(fileKey);
        MediaType mediaType = MediaType.parseMediaType(
                Optional.ofNullable(identity.fileType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        log.info("[Download] Success, fileKey={}, filename={}, contentType={}, size={}", 
                fileKey, filename, mediaType, identity.fileSize());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        
        Optional.ofNullable(identity.fileSize()).ifPresent(builder::contentLength);
        
        return builder.body(resource);
    }

    /**
     * 删除文件
     * <p>
     * 根据文件唯一标识删除文件及其元数据。
     *
     * @param fileKey 文件唯一标识
     * @return 操作结果
     */
    @DeleteMapping("/{fkey}")
    public Result<Void> deleteFile(@PathVariable("fkey") String fileKey) {
        log.info("[Delete] Start, fileKey={}", fileKey);
        service.delete(fileKey);
        log.info("[Delete] Success, fileKey={}", fileKey);
        return Result.success();
    }

    /**
     * 上传文件（简单上传）
     * <p>
     * 适用于小文件的直接上传，文件大小受服务器配置限制。
     * 大文件建议使用 {@link TaskController#createUploadTask} 创建分片上传任务。
     *
     * @param fileInfo 文件元数据信息
     * @param file     上传的文件
     * @return 上传后的文件信息
     */
    @PostMapping("/upload")
    public Result<FileInfo> uploadFile(
            @ModelAttribute FileInfo fileInfo,
            @RequestParam("file") MultipartFile file) {
        log.info("[Upload] Start, filename={}, contentType={}, size={}", 
                file.getOriginalFilename(), file.getContentType(), file.getSize());
        FileInfo info = service.upload(fileInfo, file);
        log.info("[Upload] Success, fileKey={}, filename={}, size={}", 
                info.identity().fKey(), info.identity().fileName(), info.identity().fileSize());
        return Result.success(info);
    }

    /**
     * 查询文件元数据
     * <p>
     * 根据查询条件分页检索文件元数据，支持按文件名、类型、所有者等条件筛选。
     *
     * @param queryParams 查询条件
     * @param pageable    分页参数
     * @return 分页的文件信息列表
     */
    @PostMapping("/metadata")
    public Result<Page<FileInfo>> queryMetadata(@RequestBody MetaQueryParams queryParams, @PageableDefault() Pageable pageable) {
        log.info("[QueryMetadata] Start, queryParams={}, page={}, size={}", 
                queryParams, pageable.getPageNumber(), pageable.getPageSize());
        Page<FileInfo> result = service.queryMetadata(queryParams, pageable);
        log.info("[QueryMetadata] Success, totalElements={}, totalPages={}, currentSize={}", 
                result.getTotalElements(), result.getTotalPages(), result.getNumberOfElements());
        return Result.success(result);
    }
}
