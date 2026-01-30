package tech.icc.filesrv.core.application.service.dto;

import lombok.Builder;

import java.time.LocalDate;

/**
 * 应用层文件元数据查询条件
 * <p>
 * 纯业务查询条件，无验证注解，供 Service 层使用。
 * 与 API 层 DTO 的区别：不包含 Bean Validation 注解。
 *
 * @param name          文件名精确匹配
 * @param namePrefix    文件名前缀匹配
 * @param creator       创建者 ID 或名称精确匹配
 * @param creatorPrefix 创建者名称前缀匹配
 * @param tagEither     包含任一标签（OR 查询，逗号分隔）
 * @param tagBoth       包含所有标签（AND 查询，逗号分隔）
 * @param createdAt     创建时间等于指定日期
 * @param createdBefore 创建时间早于指定日期
 * @param createdAfter  创建时间晚于指定日期
 * @param size          文件大小等于指定值
 * @param sizeGe        文件大小大于等于
 * @param sizeGt        文件大小大于
 * @param sizeLe        文件大小小于等于
 * @param sizeLt        文件大小小于
 */
@Builder
public record MetaQueryCriteria(
        String name,
        String namePrefix,
        String creator,
        String creatorPrefix,
        String tagEither,
        String tagBoth,
        LocalDate createdAt,
        LocalDate createdBefore,
        LocalDate createdAfter,
        Long size,
        Long sizeGe,
        Long sizeGt,
        Long sizeLe,
        Long sizeLt
) {
    public static MetaQueryCriteria empty() {
        return MetaQueryCriteria.builder().build();
    }
}
