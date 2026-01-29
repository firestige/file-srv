package tech.icc.filesrv.core.application.entrypoint.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 文件元数据查询 API 请求
 * <p>
 * 包含 Bean Validation 注解，用于 API 入口校验。
 * 由 Assembler 转换为应用层查询条件。
 * <p>
 * 字段命名规则：
 * - 基础字段：精确匹配（如 name、creator、size）
 * - .prefix：前缀匹配
 * - .either：包含任一标签（OR）
 * - .both：包含所有标签（AND）
 * - .at/.before/.after：时间查询
 * - .ge/.gt/.le/.lt：数值范围查询
 */
@Data
public class MetaQueryRequest {

    // ==================== 文件名查询 ====================

    /**
     * 文件名精确匹配
     */
    @Size(max = 255, message = "文件名长度不能超过 255 字符")
    private String name;

    /**
     * 文件名前缀匹配
     */
    @JsonProperty("name.prefix")
    @Size(max = 255, message = "文件名前缀长度不能超过 255 字符")
    private String namePrefix;

    // ==================== 创建者查询 ====================

    /**
     * 创建者 ID 或名称精确匹配
     */
    @Size(max = 128, message = "创建者标识长度不能超过 128 字符")
    private String creator;

    /**
     * 创建者名称前缀匹配
     */
    @JsonProperty("creator.prefix")
    @Size(max = 128, message = "创建者名称前缀长度不能超过 128 字符")
    private String creatorPrefix;

    // ==================== 标签查询 ====================

    /**
     * 包含任一标签的文件（OR 查询，英文逗号分隔）
     */
    @JsonProperty("tag.either")
    @Size(max = 500, message = "标签字符串长度不能超过 500 字符")
    private String tagEither;

    /**
     * 包含所有标签的文件（AND 查询，英文逗号分隔）
     */
    @JsonProperty("tag.both")
    @Size(max = 500, message = "标签字符串长度不能超过 500 字符")
    private String tagBoth;

    // ==================== 创建时间查询 ====================

    /**
     * 创建时间等于指定日期
     */
    @JsonProperty("created.at")
    @PastOrPresent(message = "创建时间不能是未来时间")
    private LocalDate createdAt;

    /**
     * 创建时间早于指定日期（不包含）
     */
    @JsonProperty("created.before")
    @PastOrPresent(message = "创建时间不能是未来时间")
    private LocalDate createdBefore;

    /**
     * 创建时间晚于指定日期（不包含）
     */
    @JsonProperty("created.after")
    @PastOrPresent(message = "创建时间不能是未来时间")
    private LocalDate createdAfter;

    // ==================== 文件大小查询 ====================

    /**
     * 文件大小等于指定值（字节）
     */
    private Long size;

    /**
     * 文件大小大于等于指定值（字节）
     */
    @JsonProperty("size.ge")
    private Long sizeGe;

    /**
     * 文件大小大于指定值（字节）
     */
    @JsonProperty("size.gt")
    private Long sizeGt;

    /**
     * 文件大小小于等于指定值（字节）
     */
    @JsonProperty("size.le")
    private Long sizeLe;

    /**
     * 文件大小小于指定值（字节）
     */
    @JsonProperty("size.lt")
    private Long sizeLt;
}
