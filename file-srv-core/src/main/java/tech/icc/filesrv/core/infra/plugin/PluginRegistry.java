package tech.icc.filesrv.core.infra.plugin;

import tech.icc.filesrv.common.spi.plugin.annotation.PluginInvoker;

import java.util.Set;

/**
 * 插件注册表
 * <p>
 * 管理所有已注册的插件调用器（支持新旧两种插件）。
 */
public interface PluginRegistry {

    /**
     * 根据名称获取插件调用器
     *
     * @param name 插件名称
     * @return 插件调用器
     * @throws PluginNotFoundException 插件不存在时抛出
     */
    PluginInvoker getPlugin(String name);

    /**
     * 检查插件是否存在
     *
     * @param name 插件名称
     * @return 是否存在
     */
    boolean hasPlugin(String name);

    /**
     * 获取所有注册的插件名称
     *
     * @return 插件名称集合
     */
    Set<String> getRegisteredPlugins();
}
