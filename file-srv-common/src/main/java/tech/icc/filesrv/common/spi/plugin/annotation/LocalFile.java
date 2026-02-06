package tech.icc.filesrv.common.spi.plugin.annotation;

import java.lang.annotation.*;

/**
 * 本地文件路径注入注解
 * <p>
 * 标记在 {@link PluginExecute} 方法的参数上，自动注入本地临时文件路径。
 * <p>
 * 使用示例：
 * <pre>
 * &#64;PluginExecute
 * public PluginResult process(
 *     &#64;LocalFile String localPath,     // 字符串路径
 *     &#64;LocalFile File localFile,       // File 对象
 *     &#64;LocalFile Path localPath        // Path 对象
 * ) {
 *     // 处理本地文件...
 * }
 * </pre>
 * <p>
 * 支持的参数类型：
 * <ul>
 *   <li>{@code String} - 文件路径字符串</li>
 *   <li>{@code java.io.File} - File 对象</li>
 *   <li>{@code java.nio.file.Path} - Path 对象</li>
 * </ul>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LocalFile {
}
