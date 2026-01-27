package tech.icc.filesrv.core.application.entrypoint;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.icc.filesrv.common.constants.SystemConstant;
import tech.icc.filesrv.common.context.Result;
import tech.icc.filesrv.core.application.entrypoint.model.PartUploadInfo;
import tech.icc.filesrv.core.application.entrypoint.model.TaskResponse;
import tech.icc.filesrv.core.application.entrypoint.model.UploadTaskRequest;
import tech.icc.filesrv.core.application.service.TaskService;

import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping(SystemConstant.TASKS_PATH)
public class TaskController {
    private final TaskService service;

    @PostMapping
    public Result<TaskResponse.Pending> createUploadTask(
            @RequestBody UploadTaskRequest request) {
        // TODO: 实现创建上传任务逻辑
        return Result.success(null);
    }

    @PutMapping("/{taskId}")
    public Result<PartUploadInfo> uploadPart(
            @PathVariable("taskId") String taskId,
            @RequestParam("partNumber") int partNumber,
            HttpServletRequest request) {
        // TODO: 实现分片上传逻辑
        return Result.success(null);
    }

    @PostMapping("/{taskId}")
    public Result<Void> completeUpload(
            @PathVariable("taskId") String taskId,
            @RequestBody List<PartUploadInfo> partUploadInfos) {
        // TODO: 实现完成上传逻辑
        return Result.success();
    }

    @PostMapping("/{taskId}/abort")
    public Result<Void> abortUpload(@PathVariable("taskId") String taskId) {
        // TODO: 实现中止上传逻辑
        return Result.success();
    }

    @GetMapping("/{taskId}")
    public Result<TaskResponse> getTaskDetail(@PathVariable("taskId") String taskId) {
        // TODO: 实现获取任务详情逻辑，根据状态返回不同的 TaskResponse 子类型
        return Result.success(null);
    }
}
