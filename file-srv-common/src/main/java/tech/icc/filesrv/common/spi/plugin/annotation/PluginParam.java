package tech.icc.filesrv.common.spi.plugin.annotation;

import java.lang.annotation.*;

/**
 * 插件参数注入注解
 * <p>
 * 标记在 {@link PluginExecute} 方法的参数上，自动注入当前插件的配置参数。
 * <p>
 * 参数来源于 {@code CallbackConfig.params}，由调用方在创建任务时指定。
 * <p>
 * 使用示例：
 * <pre>
 * &#64;PluginExecute
 * public PluginResult process(
 *     &#64;PluginParam("width") int width,                           // 必需参数
 *     &#64;PluginParam(value = "height", defaultValue = "300") int height,  // 带默认值
 *     &#64;PluginParam(value = "quality", required = false) Integer quality  // 可选参数
 * ) {
 *     // width, height, quality 自动从 CallbackConfig.params 中注入
 * }
 * </pre>
 * <p>
 * 支持的参数类型：
 * <ul>
 *   <li>String</li>
 *   <li>Integer / int</li>
 *   <li>Long / long</li>
 *   <li>Double / double</li>
 *   <li>Float / float</li>
 *   <li>Boolean / boolean</li>
 * </ul>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginParam {
    /**
     * 参数名（对应 CallbackConfig.params 中的 key）
     */
    String value();

    /**
     * 默认值（当参数不存在时使用）
     * <p>
     * 注意：默认值为字符串，会自动转换为参数类型
     */
    String defaultValue() default "";

    /**
     * 是否必需
     * <p>
     * 如果为 true 且参数不存在也无默认值，则抛出异常
     */
    boolean required() default false;
}
