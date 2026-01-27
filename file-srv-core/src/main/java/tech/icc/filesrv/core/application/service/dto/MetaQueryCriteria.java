package tech.icc.filesrv.core.application.service.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 应用层文件元数据查询条件
 * <p>
 * 纯业务查询条件，无验证注解，供 Service 层使用。
 * 与 API 层 DTO 的区别：不包含 Bean Validation 注解。
 *
 * @param fileName    文件名（支持模糊匹配）
 * @param creator     创建者 ID
 * @param contentType 内容类型（支持前缀匹配，如 image/*）
 * @param createdFrom 创建时间起始（包含）
 * @param updatedTo   更新时间截止（包含）
 * @param tags        标签集合（匹配包含任一标签的文件）
 */
@Builder
public record MetaQueryCriteria(
        String fileName,
        String creator,
        String contentType,
        LocalDateTime createdFrom,
        LocalDateTime updatedTo,
        Set<String> tags
) {}
