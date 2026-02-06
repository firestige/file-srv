package tech.icc.filesrv.common.spi.plugin.annotation;

import java.lang.annotation.*;

/**
 * 插件执行方法标记注解
 * <p>
 * 标记插件类中的执行方法。Runner 会通过反射查找此注解并调用方法。
 * <p>
 * 方法参数支持以下注解进行自动注入：
 * <ul>
 *   <li>{@link PluginParam} - 插件配置参数</li>
 *   <li>{@link LocalFile} - 本地文件路径</li>
 *   <li>{@link TaskInfo} - 任务执行信息</li>
 *   <li>{@link PluginOutput} - 前序插件输出</li>
 *   <li>{@code TaskContext} - 完整上下文对象（无需注解）</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * &#64;SharedPlugin("thumbnail")
 * public class ThumbnailPlugin {
 *     
 *     &#64;PluginExecute
 *     public PluginResult process(
 *         &#64;PluginParam("width") int width,
 *         &#64;PluginParam("height") int height,
 *         &#64;LocalFile Path localFile,
 *         TaskContext context
 *     ) {
 *         // 参数自动注入，直接使用
 *         return PluginResult.success();
 *     }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginExecute {
}
