package tech.icc.filesrv.core.domain.events;

import tech.icc.filesrv.common.vo.task.DerivedFile;

import java.time.Instant;
import java.util.List;

/**
 * 衍生文件添加事件
 * <p>
 * 当 Plugin 执行成功并创建衍生文件后发布此事件，用于自动维护文件关联关系。
 * 事件监听器会为每个衍生文件创建 FileRelation 记录，建立双向引用。
 * </p>
 *
 * @param taskId          任务 ID
 * @param sourceFkey      源文件 fKey（生成衍生文件的原始文件）
 * @param newDerivedFiles 新添加的衍生文件列表
 * @param timestamp       事件发生时间
 */
public record DerivedFilesAddedEvent(
        String taskId,
        String sourceFkey,
        List<DerivedFile> newDerivedFiles,
        Instant timestamp
) {
    /**
     * 创建事件
     *
     * @param taskId          任务 ID
     * @param sourceFkey      源文件 fKey
     * @param newDerivedFiles 新衍生文件列表
     * @return 事件实例
     */
    public static DerivedFilesAddedEvent of(String taskId, String sourceFkey, List<DerivedFile> newDerivedFiles) {
        return new DerivedFilesAddedEvent(taskId, sourceFkey, newDerivedFiles, Instant.now());
    }

    /**
     * 是否包含衍生文件
     */
    public boolean hasDerivedFiles() {
        return newDerivedFiles != null && !newDerivedFiles.isEmpty();
    }

    /**
     * 获取衍生文件数量
     */
    public int getDerivedFileCount() {
        return newDerivedFiles != null ? newDerivedFiles.size() : 0;
    }
}
