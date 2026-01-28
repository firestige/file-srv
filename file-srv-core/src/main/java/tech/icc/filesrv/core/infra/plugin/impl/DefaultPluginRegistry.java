package tech.icc.filesrv.core.infra.plugin.impl;

import org.springframework.stereotype.Component;
import tech.icc.filesrv.common.exception.PluginNotFoundException;
import tech.icc.filesrv.core.infra.plugin.PluginRegistry;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认插件注册表实现
 * <p>
 * 通过 Spring 自动发现所有 SharedPlugin Bean 并注册。
 */
@Component
public class DefaultPluginRegistry implements PluginRegistry {

    private final Map<String, SharedPlugin> plugins = new ConcurrentHashMap<>();

    public DefaultPluginRegistry(List<SharedPlugin> pluginList) {
        for (SharedPlugin plugin : pluginList) {
            plugins.put(plugin.name(), plugin);
            plugin.init();
        }
    }

    @Override
    public SharedPlugin getPlugin(String name) {
        SharedPlugin plugin = plugins.get(name);
        if (plugin == null) {
            throw new PluginNotFoundException(name);
        }
        return plugin;
    }

    @Override
    public boolean hasPlugin(String name) {
        return plugins.containsKey(name);
    }

    @Override
    public Set<String> getRegisteredPlugins() {
        return Set.copyOf(plugins.keySet());
    }
}
