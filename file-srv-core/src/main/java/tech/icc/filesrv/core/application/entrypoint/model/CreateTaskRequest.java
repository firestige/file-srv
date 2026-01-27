package tech.icc.filesrv.core.application.entrypoint.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tech.icc.filesrv.common.vo.task.FileRequest;

/**
 * 创建上传任务请求
 * <p>
 * 复用 {@link FileRequest} VO，仅添加任务特有的 callbacks 配置。
 *
 * @param file      文件请求信息（必填）
 * @param callbacks 回调配置（可选），JSON 格式的回调规则
 */
public record CreateTaskRequest(
        @JsonUnwrapped
        @Valid
        @NotNull(message = "文件信息不能为空")
        FileRequest file,

        String callbacks
) {}
