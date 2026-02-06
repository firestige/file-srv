package tech.icc.filesrv.common.spi.plugin.annotation;

import java.lang.annotation.*;

/**
 * 任务信息注入注解
 * <p>
 * 标记在 {@link PluginExecute} 方法的参数上，自动注入任务执行信息（只读）。
 * <p>
 * 使用示例：
 * <pre>
 * &#64;PluginExecute
 * public PluginResult process(
 *     &#64;TaskInfo("taskId") String taskId,
 *     &#64;TaskInfo("fileHash") String fileHash,
 *     &#64;TaskInfo("fileSize") Long fileSize
 * ) {
 *     // 任务信息自动注入
 * }
 * </pre>
 * <p>
 * 支持的信息类型：
 * <ul>
 *   <li>{@code taskId} - 任务ID（String）</li>
 *   <li>{@code fileHash} - 文件哈希（String）</li>
 *   <li>{@code contentType} - MIME类型（String）</li>
 *   <li>{@code fileSize} - 文件大小（Long）</li>
 *   <li>{@code filename} - 文件名（String）</li>
 *   <li>{@code storagePath} - 存储路径（String）</li>
 * </ul>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskInfo {
    /**
     * 任务信息字段名
     * <p>
     * 可选值：taskId, fileHash, contentType, fileSize, filename, storagePath
     */
    String value();
}
