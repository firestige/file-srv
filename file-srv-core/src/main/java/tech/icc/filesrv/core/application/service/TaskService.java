package tech.icc.filesrv.core.application.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tech.icc.filesrv.common.vo.task.FileRequest;
import tech.icc.filesrv.common.vo.task.TaskSummary;
import tech.icc.filesrv.core.application.entrypoint.model.PartETag;
import tech.icc.filesrv.core.application.entrypoint.model.TaskResponse;
import tech.icc.filesrv.core.domain.tasks.TaskStatus;

import java.io.InputStream;
import java.util.List;

/**
 * 任务服务
 * <p>
 * 处理异步上传任务的完整生命周期：创建 → 分片上传 → 完成/中止 → 状态查询
 */
@Service
public class TaskService {

    // ==================== 命令操作 ====================

    /**
     * 创建上传任务
     *
     * @param request   文件请求信息
     * @param callbacks 回调配置（可选）
     * @return Pending 状态响应，包含预签名上传 URL
     */
    public TaskResponse.Pending createTask(FileRequest request, String callbacks) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * 上传分片
     *
     * @param taskId        任务标识
     * @param partNumber    分片序号（1-based）
     * @param content       分片内容流
     * @return 分片 ETag
     */
    public PartETag uploadPart(String taskId, int partNumber, InputStream content) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * 完成上传，触发 callback 处理
     *
     * @param taskId 任务标识
     * @param parts  已上传分片的 ETag 列表
     */
    public void completeUpload(String taskId, List<PartETag> parts) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * 中止上传
     *
     * @param taskId 任务标识
     * @param reason 中止原因（可选）
     */
    public void abortUpload(String taskId, String reason) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    // ==================== 查询操作 ====================

    /**
     * 获取任务详情（轮询接口）
     *
     * @param taskId 任务标识
     * @return 对应状态的 TaskResponse
     */
    public TaskResponse getTask(String taskId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * 查询任务列表（管理接口）
     *
     * @param status   状态过滤（可选，null 表示全部）
     * @param pageable 分页参数
     * @return 任务摘要分页列表
     */
    public Page<TaskSummary> listTasks(TaskStatus status, Pageable pageable) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
