package tech.icc.filesrv.core.infra.file;

import java.nio.file.Path;

/**
 * 本地文件管理器
 * <p>
 * 管理任务执行期间的本地临时文件。
 */
public interface LocalFileManager {

    /**
     * 准备本地文件供 Plugin 处理
     * <p>
     * - 如果有上传缓存，直接返回缓存路径
     * - 否则从存储层下载到本地临时目录
     *
     * @param storagePath 存储层路径
     * @param taskId      任务 ID
     * @return 本地文件路径
     */
    Path prepareLocalFile(String storagePath, String taskId);

    /**
     * 清理任务的本地临时文件
     *
     * @param taskId 任务 ID
     */
    void cleanup(String taskId);

    /**
     * 获取任务的临时目录（供 Plugin 写入衍生文件）
     *
     * @param taskId 任务 ID
     * @return 临时目录路径
     */
    Path getTempDirectory(String taskId);

    /**
     * 缓存上传的文件到本地
     * <p>
     * 用于分片上传完成后，保留本地副本供后续 Plugin 使用。
     *
     * @param taskId    任务 ID
     * @param localPath 本地文件路径
     */
    void cacheUploadedFile(String taskId, Path localPath);

    /**
     * 获取缓存的上传文件
     *
     * @param taskId 任务 ID
     * @return 缓存的本地文件路径，如果不存在则返回 null
     */
    Path getCachedFile(String taskId);
}
