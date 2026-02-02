package tech.icc.filesrv.common.domain.events;

import tech.icc.filesrv.common.vo.task.DerivedFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 任务完成事件
 * <p>
 * 当上传任务及所有 callback 执行成功后发布到 Kafka。
 *
 * @param taskId         任务 ID
 * @param fKey           用户文件标识
 * @param storagePath    存储路径
 * @param contentHash    文件内容哈希
 * @param fileSize       文件大小
 * @param contentType    MIME 类型
 * @param filename       原始文件名
 * @param derivedFiles   衍生文件列表（缩略图等）
 * @param pluginOutputs  各 Plugin 的输出
 * @param completedAt    完成时间
 */
public record TaskCompletedEvent(
        String taskId,
        String fKey,
        String storagePath,
        String contentHash,
        Long fileSize,
        String contentType,
        String filename,
        List<DerivedFile> derivedFiles,
        Map<String, Object> pluginOutputs,
        Instant completedAt
) {

    /**
     * 创建完成事件
     */
    public static TaskCompletedEvent of(
            String taskId,
            String fKey,
            String storagePath,
            String contentHash,
            Long fileSize,
            String contentType,
            String filename,
            List<DerivedFile> derivedFiles,
            Map<String, Object> pluginOutputs
    ) {
        return new TaskCompletedEvent(
                taskId,
                fKey,
                storagePath,
                contentHash,
                fileSize,
                contentType,
                filename,
                derivedFiles != null ? List.copyOf(derivedFiles) : List.of(),
                pluginOutputs != null ? Map.copyOf(pluginOutputs) : Map.of(),
                Instant.now()
        );
    }
}
