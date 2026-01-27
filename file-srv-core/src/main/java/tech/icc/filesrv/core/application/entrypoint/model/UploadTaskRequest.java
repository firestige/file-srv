package tech.icc.filesrv.core.application.entrypoint.model;

import lombok.Builder;

/**
 * 创建上传任务的请求参数
 */
@Builder
public record UploadTaskRequest(
        String filename,
        String contentType,
        Long size,
        String checksum,
        String location,
        String callbacks
) {}
