package tech.icc.filesrv.common.spi.plugin.annotation;

import java.lang.annotation.*;

/**
 * 插件输出注入注解
 * <p>
 * 标记在 {@link PluginExecute} 方法的参数上，自动注入前序插件的输出数据。
 * <p>
 * 插件间通过输出和读取实现数据传递：
 * <pre>
 * // Plugin A：输出数据
 * &#64;PluginExecute
 * public PluginResult analyze(...) {
 *     return PluginResult.success(Map.of(
 *         "imageWidth", 1920,
 *         "imageHeight", 1080
 *     ));
 * }
 *
 * // Plugin B：读取 Plugin A 的输出
 * &#64;PluginExecute
 * public PluginResult process(
 *     &#64;PluginOutput("imageWidth") Integer width,
 *     &#64;PluginOutput(value = "imageHeight", required = false) Integer height
 * ) {
 *     // 使用前序插件的输出数据
 * }
 * </pre>
 * <p>
 * 支持的参数类型：String, Integer, Long, Double, Float, Boolean及其基本类型
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginOutput {
    /**
     * 输出键名
     */
    String value();

    /**
     * 是否必需
     * <p>
     * 如果为 true 且输出不存在，则抛出异常
     */
    boolean required() default false;

    /**
     * 默认值（当输出不存在时使用）
     */
    String defaultValue() default "";
}
