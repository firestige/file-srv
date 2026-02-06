package tech.icc.filesrv.common.spi.plugin;

import org.springframework.stereotype.Component;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginExecute;

import java.lang.annotation.*;

/**
 * 插件标记注解
 * <p>
 * 标记一个类为文件处理插件，Spring 会自动发现并注册到 PluginRegistry。
 * <p>
 * 使用示例：
 * <pre>
 * &#64;SharedPlugin("thumbnail")
 * public class ThumbnailPlugin {
 *     
 *     &#64;PluginExecute
 *     public PluginResult process(...) {
 *         // 插件逻辑
 *     }
 * }
 * </pre>
 *
 * @see PluginExecute
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface SharedPlugin {
    /**
     * 插件唯一标识
     * <p>
     * 用于在 CallbackConfig 中引用此插件
     *
     * @return 插件名称，例如 "thumbnail", "watermark", "rename"
     */
    String value();
}

