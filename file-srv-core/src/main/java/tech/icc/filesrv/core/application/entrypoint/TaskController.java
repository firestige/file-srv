package tech.icc.filesrv.core.application.entrypoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.icc.filesrv.common.constants.SystemConstant;
import tech.icc.filesrv.common.context.Result;
import tech.icc.filesrv.core.application.entrypoint.assembler.TaskInfoAssembler;
import tech.icc.filesrv.core.application.entrypoint.model.CreateTaskRequest;
import tech.icc.filesrv.core.application.entrypoint.model.PartETag;
import tech.icc.filesrv.core.application.entrypoint.model.TaskResponse;
import tech.icc.filesrv.core.application.service.TaskService;
import tech.icc.filesrv.core.application.service.dto.PartETagDto;
import tech.icc.filesrv.core.application.service.dto.TaskInfoDto;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * 任务管理控制器
 * <p>
 * 提供异步上传任务的完整生命周期管理：
 * <ol>
 *   <li>创建任务 - 获取预签名上传 URL</li>
 *   <li>分片上传 - 逐个上传文件分片</li>
 *   <li>完成上传 - 合并分片并触发 callback</li>
 *   <li>状态查询 - 轮询任务状态</li>
 * </ol>
 * <p>
 * 适用于大文件上传场景，小文件请使用 {@link FileController#uploadFile} 直接上传。
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(SystemConstant.TASKS_PATH)
public class TaskController {

    /** 任务标识最大长度 */
    private static final int MAX_TASK_ID_LENGTH = 64;

    /** 最大分片数量（S3 限制） */
    private static final int MAX_PART_NUMBER = 10000;

    private final TaskService service;

    /**
     * 创建上传任务
     * <p>
     * 初始化一个异步上传任务，返回任务信息和预签名上传 URL。
     * 客户端使用返回的 URL 直接上传分片到存储层。
     *
     * @param request 创建任务请求
     * @return 201 Created，Location 头指向任务资源
     */
    @PostMapping
    public ResponseEntity<Result<TaskResponse.Pending>> createTask(
            @Valid @RequestBody CreateTaskRequest request) {

        log.info("[CreateTask] Start, filename={}, size={}, contentType={}",
                request.file().filename(), request.file().size(), request.file().contentType());

        TaskInfoDto.Pending dto = service.createTask(request.file(), request.callbacks());
        TaskResponse.Pending response = (TaskResponse.Pending) TaskInfoAssembler.toResponse(dto);
        String location = SystemConstant.TASKS_PATH + "/" + response.summary().taskId();

        log.info("[CreateTask] Success, taskId={}, uploadId={}",
                response.summary().taskId(), response.summary().uploadId());

        return ResponseEntity.created(URI.create(location))
                .body(Result.success(response));
    }

    /**
     * 上传分片
     * <p>
     * 将文件的一个分片上传到任务。分片序号从 1 开始，最大 10000。
     * 客户端需在请求头中提供 Content-Length。
     *
     * @param taskId        任务标识
     * @param partNumber    分片序号（1-10000）
     * @param contentLength 分片大小（字节）
     * @param request       HTTP 请求（用于获取输入流）
     * @return 分片 ETag
     */
    @PutMapping("/{taskId}/parts/{partNumber}")
    public Result<PartETag> uploadPart(
            @PathVariable("taskId")
            @NotBlank(message = "任务标识不能为空")
            @Size(max = MAX_TASK_ID_LENGTH, message = "任务标识长度不能超过 64 字符")
            String taskId,

            @PathVariable("partNumber")
            @Min(value = 1, message = "分片序号最小为 1")
            @Max(value = MAX_PART_NUMBER, message = "分片序号最大为 10000")
            int partNumber,

            @RequestHeader("Content-Length") long contentLength,
            HttpServletRequest request) throws IOException {

        log.info("[UploadPart] Start, taskId={}, partNumber={}, contentLength={}",
                taskId, partNumber, contentLength);

        PartETagDto dto = service.uploadPart(taskId, partNumber, request.getInputStream(), contentLength);
        PartETag response = TaskInfoAssembler.toResponse(dto);

        log.info("[UploadPart] Success, taskId={}, partNumber={}, eTag={}",
                taskId, partNumber, response.eTag());

        return Result.success(response);
    }

    /**
     * 完成上传
     * <p>
     * 通知服务端所有分片已上传完成，触发分片合并和 callback 处理。
     * 返回 202 Accepted 表示请求已接受，callback 将异步执行。
     * 客户端应通过 {@link #getTask} 轮询任务状态。
     *
     * @param taskId 任务标识
     * @param parts  已上传分片的 ETag 列表
     * @return 202 Accepted
     */
    @PostMapping("/{taskId}/complete")
    public ResponseEntity<Result<Void>> completeUpload(
            @PathVariable("taskId")
            @NotBlank(message = "任务标识不能为空")
            @Size(max = MAX_TASK_ID_LENGTH, message = "任务标识长度不能超过 64 字符")
            String taskId,

            @Valid @RequestBody List<PartETag> parts) {

        log.info("[CompleteUpload] Start, taskId={}, partsCount={}", taskId, parts.size());

        service.completeUpload(taskId, TaskInfoAssembler.toDtoList(parts));

        log.info("[CompleteUpload] Accepted, taskId={}", taskId);

        // 202: 请求已接受，callback 异步处理中
        return ResponseEntity.accepted().body(Result.success());
    }

    /**
     * 中止上传
     * <p>
     * 取消正在进行的上传任务，释放已上传的分片资源。
     * 仅 PENDING 和 IN_PROGRESS 状态的任务可以中止。
     *
     * @param taskId 任务标识
     * @param reason 中止原因（可选）
     * @return 200 OK
     */
    @PostMapping("/{taskId}/abort")
    public Result<Void> abortUpload(
            @PathVariable("taskId")
            @NotBlank(message = "任务标识不能为空")
            @Size(max = MAX_TASK_ID_LENGTH, message = "任务标识长度不能超过 64 字符")
            String taskId,

            @RequestParam(value = "reason", required = false) String reason) {

        log.info("[AbortUpload] Start, taskId={}, reason={}", taskId, reason);

        service.abortUpload(taskId, reason);

        log.info("[AbortUpload] Success, taskId={}", taskId);

        return Result.success();
    }

    /**
     * 获取任务详情
     * <p>
     * 查询任务当前状态，用于客户端轮询。返回的响应类型取决于任务状态：
     * <ul>
     *   <li>PENDING - 包含上传 URL</li>
     *   <li>IN_PROGRESS - 包含上传进度</li>
     *   <li>PROCESSING - 包含进度信息</li>
     *   <li>COMPLETED - 包含文件信息和衍生文件</li>
     *   <li>FAILED - 包含错误详情</li>
     *   <li>ABORTED - 包含中止信息</li>
     * </ul>
     *
     * @param taskId 任务标识
     * @return 任务详情
     */
    @GetMapping("/{taskId}")
    public Result<TaskResponse> getTask(
            @PathVariable("taskId")
            @NotBlank(message = "任务标识不能为空")
            @Size(max = MAX_TASK_ID_LENGTH, message = "任务标识长度不能超过 64 字符")
            String taskId) {

        log.debug("[GetTask] Query, taskId={}", taskId);

        TaskInfoDto dto = service.getTask(taskId);
        TaskResponse response = TaskInfoAssembler.toResponse(dto);

        log.debug("[GetTask] Result, taskId={}, status={}", taskId, response.getClass().getSimpleName());

        return Result.success(response);
    }
}
