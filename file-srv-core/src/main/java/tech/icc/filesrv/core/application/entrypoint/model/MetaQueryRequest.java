package tech.icc.filesrv.core.application.entrypoint.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 文件元数据查询 API 请求
 * <p>
 * 包含 Bean Validation 注解，用于 API 入口校验。
 * 由 Assembler 转换为应用层查询条件。
 */
@Data
public class MetaQueryRequest {

    /**
     * 文件名（支持模糊匹配，前缀匹配）
     */
    @Size(max = 255, message = "文件名长度不能超过 255 字符")
    private String fileName;

    /**
     * 创建者 ID
     */
    @Size(max = 64, message = "创建者 ID 长度不能超过 64 字符")
    private String creator;

    /**
     * 内容类型（支持前缀匹配，如 image/*）
     */
    @Size(max = 128, message = "内容类型长度不能超过 128 字符")
    @Pattern(regexp = "^[a-zA-Z0-9*/-]+$", message = "内容类型格式不正确")
    private String contentType;

    /**
     * 创建时间起始（包含）
     */
    @PastOrPresent(message = "创建时间起始不能是未来时间")
    private LocalDateTime createdFrom;

    /**
     * 更新时间截止（包含）
     */
    @PastOrPresent(message = "更新时间截止不能是未来时间")
    private LocalDateTime updatedTo;

    /**
     * 标签集合（匹配包含任一标签的文件）
     */
    @Size(max = 10, message = "标签数量不能超过 10 个")
    private Set<@Size(max = 32, message = "单个标签长度不能超过 32 字符") String> tags;
}
