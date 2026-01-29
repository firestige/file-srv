package tech.icc.filesrv.core.application.entrypoint.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 分片 ETag 信息
 * <p>
 * 用于：
 * <ul>
 *   <li>分片上传响应：返回已上传分片的 ETag</li>
 *   <li>完成上传请求：客户端提交所有分片的 ETag 列表</li>
 * </ul>
 *
 * @param partNumber 分片序号（1-based，最大 10000）
 * @param eTag       分片的 ETag（由存储层返回的校验值）
 */
public record PartETag(
        @Min(value = 1, message = "分片序号最小为 1")
        @Max(value = 10000, message = "分片序号最大为 10000")
        int partNumber,

        @NotBlank(message = "ETag 不能为空")
        String eTag
) {}
