package tech.icc.filesrv.core.application.entrypoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.icc.filesrv.common.constants.SystemConstant;
import tech.icc.filesrv.common.exception.AccessDeniedException;
import tech.icc.filesrv.common.exception.FileNotFoundException;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.FileIdentity;
import tech.icc.filesrv.core.application.entrypoint.assembler.FileInfoAssembler;
import tech.icc.filesrv.core.application.entrypoint.model.FileInfoResponse;
import tech.icc.filesrv.core.application.entrypoint.support.FileResponseBuilder;
import tech.icc.filesrv.core.application.service.FileService;
import tech.icc.filesrv.core.application.service.dto.FileInfoDto;

import java.util.Optional;

/**
 * 静态资源控制器
 * <p>
 * 提供公开文件的匿名访问端点，无需认证即可下载。
 * 仅允许访问元数据中 {@code public=true} 的文件。
 * <p>
 * 典型使用场景：
 * <ul>
 *   <li>公开图片的直接引用（img src）</li>
 *   <li>公开文档的下载链接</li>
 *   <li>CDN 回源</li>
 * </ul>
 *
 * @see FileController 需要认证的文件操作
 */
@Slf4j
@Validated
@RestController
@RequestMapping(SystemConstant.STATIC_RESOURCES_PATH)
@RequiredArgsConstructor
public class StaticResourceController {

    /** 文件标识最大长度 */
    private static final int MAX_FILE_KEY_LENGTH = 128;

    private final FileService service;

    /**
     * 下载公开文件
     * <p>
     * 匿名访问端点，仅允许下载 {@code public=true} 的文件。
     * 非公开文件返回 403 Forbidden。
     *
     * @param fileKey 文件唯一标识（1-128 字符）
     * @return 文件资源流
     * @throws FileNotFoundException 文件不存在
     * @throws AccessDeniedException 文件非公开，拒绝访问
     */
    @GetMapping("/{fkey}")
    public ResponseEntity<Resource> staticResource(
            @PathVariable("fkey")
            @NotBlank(message = "文件标识不能为空")
            @Size(max = MAX_FILE_KEY_LENGTH, message = "文件标识长度不能超过 128 字符")
            String fileKey) {
        log.info("[StaticResource] Start, fileKey={}", fileKey);

        FileInfoDto dto = service.getFileInfo(fileKey)
                .orElseThrow(() -> {
                    log.warn("[StaticResource] File not found, fileKey={}", fileKey);
                    return FileNotFoundException.withoutStack("文件不存在: " + fileKey);
                });

        // 检查是否为公开文件
        boolean isPublic = Optional.ofNullable(dto.access())
                .map(AccessControl::isPublic)
                .orElse(false);

        if (!isPublic) {
            log.warn("[StaticResource] Access denied, file is not public, fileKey={}", fileKey);
            throw AccessDeniedException.withoutStack("文件非公开，拒绝访问: " + fileKey);
        }

        return buildDownloadResponse(fileKey, dto);
    }

    private ResponseEntity<Resource> buildDownloadResponse(String fileKey, FileInfoDto dto) {
        String filename = Optional.ofNullable(dto.identity())
                .map(FileIdentity::fileName)
                .orElse(fileKey);

        Resource resource = service.download(fileKey);

        log.info("[StaticResource] Success, fileKey={}, filename={}", fileKey, filename);

        FileInfoResponse response = FileInfoAssembler.toResponse(dto);
        // 静态资源使用 inline 方式，便于浏览器直接显示
        return FileResponseBuilder.forDownload()
                .fromFileInfo(response)
                .inline(filename)
                .defaultCache()
                .build(resource);
    }
}
