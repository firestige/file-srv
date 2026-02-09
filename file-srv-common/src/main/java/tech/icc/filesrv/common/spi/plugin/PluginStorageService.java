package tech.icc.filesrv.common.spi.plugin;

import com.sun.source.util.Plugin;
import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.exception.FileServiceException;

import java.io.InputStream;
import java.time.Duration;

/**
 * 插件存储服务
 * <p>
 * 为 Callback 插件提供专用的存储操作接口，支持大文件上传、下载和临时URL生成。
 * 主要用于插件生成的衍生文件存储（如视频转码、图片处理等）。
 * </p>
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li><b>大文件支持</b>: 使用分片上传，支持10GB+文件</li>
 *   <li><b>临时URL</b>: 生成带签名的临时访问链接</li>
 *   <li><b>自动清理</b>: 支持设置文件过期时间</li>
 *   <li><b>异常安全</b>: 上传失败自动清理已上传分片</li>
 *   <li><b>延迟激活</b>: 文件先创建为 PENDING，统一在 callback 链结束后激活</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 插件中上传转码后的视频
 * String fkey = storageService.uploadLargeFile(
 *     context,  // TaskContext（用于记录待激活信息）
 *     transcodedStream,
 *     "video_transcoded_480p.mp4",
 *     "video/mp4",
 *     fileSize
 * );
 * 
 * // 生成临时下载链接（有效期1小时）
 * String downloadUrl = storageService.getTemporaryUrl(fkey, Duration.ofHours(1));
 * </pre>
 *
 * @see Plugin
 */
public interface PluginStorageService {

    /**
     * 上传大文件到存储系统
     * <p>
     * 自动使用分片上传策略，适用于大文件场景（如10GB视频转码）。
     * 失败时自动清理已上传的分片。
     * </p>
     * <p>
     * <b>延迟激活机制</b>：文件会先创建为 PENDING 状态的 file_reference，
     * 待激活信息会记录在 TaskContext 中，最终由 CallbackChainRunner 统一批量激活。
     * </p>
     *
     * @param context     任务上下文（用于记录待激活信息和获取 owner 信息）
     * @param inputStream 文件输入流（调用方负责关闭）
     * @param fileName 文件名（用于生成存储路径）
     * @param contentType MIME类型（如 "video/mp4"）
     * @param fileSize 文件大小（字节数），用于优化分片策略
     * @return 上传成功后的文件唯一标识（fKey）
     * @throws FileServiceException 上传失败（网络错误、存储服务不可用等）
     * @throws IllegalArgumentException context/inputStream 为 null 或 fileSize <= 0
     */
    String uploadLargeFile(TaskContext context, InputStream inputStream, String fileName, String contentType, long fileSize);

    /**
     * 下载文件流
     * <p>
     * 返回的 InputStream 必须由调用方关闭。
     * 如果文件不存在，抛出 FileNotFoundException。
     * </p>
     *
     * @param fkey 文件唯一标识
     * @return 文件输入流（需调用方关闭）
     * @throws FileNotFoundException 文件不存在
     * @throws FileServiceException 下载失败（网络错误、权限不足等）
     * @throws IllegalArgumentException fkey 为 null 或空字符串
     */
    InputStream downloadFile(String fkey);

    /**
     * 删除文件
     * <p>
     * 物理删除存储系统中的文件。
     * 如果文件不存在，操作幂等（不抛异常）。
     * </p>
     *
     * @param fkey 文件唯一标识
     * @throws FileServiceException 删除失败（权限不足、网络错误等）
     * @throws IllegalArgumentException fkey 为 null 或空字符串
     */
    void deleteFile(String fkey);

    /**
     * 生成临时访问URL
     * <p>
     * 返回带签名的临时下载链接，适用于：
     * <ul>
     *   <li>前端直接下载大文件（避免服务器流量）</li>
     *   <li>临时分享文件（自动过期）</li>
     *   <li>CDN加速场景</li>
     * </ul>
     * </p>
     *
     * @param fkey 文件唯一标识
     * @param validity 有效期（从当前时间起算）
     * @return 临时访问URL（包含签名和过期时间戳）
     * @throws FileNotFoundException 文件不存在
     * @throws FileServiceException 生成URL失败（存储服务不支持、网络错误等）
     * @throws IllegalArgumentException fkey 为 null 或空字符串，或 validity 为负数
     */
    String getTemporaryUrl(String fkey, Duration validity);

    /**
     * 生成临时访问URL（默认有效期）
     * <p>
     * 使用配置文件中的默认有效期（默认1小时）。
     * </p>
     *
     * @param fkey 文件唯一标识
     * @return 临时访问URL
     * @throws FileNotFoundException 文件不存在
     * @throws FileServiceException 生成URL失败
     * @throws IllegalArgumentException fkey 为 null 或空字符串
     */
    default String getTemporaryUrl(String fkey) {
        return getTemporaryUrl(fkey, Duration.ofHours(1));
    }
}
