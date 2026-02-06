package tech.icc.filesrv.common.spi.plugin.annotation;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.PluginResult;

/**
 * 插件调用器接口
 * <p>
 * 抽象插件的调用方式，支持新旧两种插件实现：
 * <ul>
 *   <li>新式：使用 @SharedPlugin + @PluginExecute 注解的类（PluginMethodInvoker）</li>
 *   <li>旧式：实现 SharedPlugin 接口的类（LegacyPluginInvoker，已废弃）</li>
 * </ul>
 */
public interface PluginInvoker {
    
    /**
     * 获取插件名称
     */
    String getPluginName();
    
    /**
     * 执行插件
     *
     * @param context 任务上下文
     * @return 执行结果
     * @throws Exception 执行失败
     */
    PluginResult invoke(TaskContext context) throws Exception;
    
    /**
     * 获取插件实例（用于Aware注入等）
     */
    Object getPluginInstance();
}
