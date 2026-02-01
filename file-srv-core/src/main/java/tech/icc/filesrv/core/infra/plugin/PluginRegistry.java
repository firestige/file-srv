package tech.icc.filesrv.core.infra.plugin;

import tech.icc.filesrv.common.exception.validation.PluginNotFoundException;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;

import java.util.Set;

/**
 * 插件注册表
 * <p>
 * 管理所有已注册的 SharedPlugin 实例。
 */
public interface PluginRegistry {

    /**
     * 根据名称获取插件
     *
     * @param name 插件名称
     * @return 插件实例
     * @throws PluginNotFoundException 插件不存在时抛出
     */
    SharedPlugin getPlugin(String name);

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
