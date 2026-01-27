package tech.icc.filesrv.core.application.service.dto;

/**
 * 应用层分片 ETag
 * <p>
 * 无验证注解，供 Service 层使用。
 *
 * @param partNumber 分片序号（1-based）
 * @param eTag       分片的 ETag（由存储层返回的校验值）
 */
public record PartETagDto(
        int partNumber,
        String eTag
) {}
