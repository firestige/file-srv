package tech.icc.filesrv.core.infra.plugin.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import tech.icc.filesrv.common.exception.NotFoundException;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginInvoker;
import tech.icc.filesrv.common.spi.plugin.PluginMethodInvoker;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;
import tech.icc.filesrv.core.infra.plugin.PluginRegistry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认插件注册表实现
 * <p>
 * 支持两种插件注册方式：
 * <ul>
 *   <li>新式：标记 @SharedPlugin("name") 注解的类（自动创建 PluginMethodInvoker）</li>
 *   <li>旧式：实现 SharedPlugin 接口的 Bean（兼容，已废弃）</li>
 * </ul>
 * <p>
 * 插件发现流程：
 * <ol>
 *   <li>Spring 扫描所有标记 @SharedPlugin 的 Bean</li>
 *   <li>提取注解的 value() 作为插件名</li>
 *   <li>为每个插件创建 PluginMethodInvoker</li>
 *   <li>注册到 Map&lt;name, invoker&gt;</li>
 * </ol>
 */
@Component
public class DefaultPluginRegistry implements PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginRegistry.class);

    private final Map<String, PluginInvoker> plugins = new ConcurrentHashMap<>();

    public DefaultPluginRegistry(ApplicationContext context) {
        // 发现所有标记 @SharedPlugin 注解的 Bean
        Map<String, Object> annotatedBeans = context.getBeansWithAnnotation(SharedPlugin.class);

        for (Map.Entry<String, Object> entry : annotatedBeans.entrySet()) {
            String beanName = entry.getKey();
            Object pluginInstance = entry.getValue();

            // 获取注解值作为插件名
            SharedPlugin annotation = pluginInstance.getClass().getAnnotation(SharedPlugin.class);
            String pluginName = annotation.value();

            // 创建调用器
            try {
                PluginMethodInvoker invoker = new PluginMethodInvoker(pluginInstance, pluginName);
                plugins.put(pluginName, invoker);
                log.info("Registered plugin: {} (bean={})", pluginName, beanName);
            } catch (Exception e) {
                log.error("Failed to register plugin: {} (bean={})", pluginName, beanName, e);
                throw new IllegalStateException("Plugin registration failed: " + pluginName, e);
            }
        }

        log.info("Plugin registry initialized with {} plugins: {}", plugins.size(), plugins.keySet());
    }

    @Override
    public PluginInvoker getPlugin(String name) {
        PluginInvoker plugin = plugins.get(name);
        if (plugin == null) {
            throw new NotFoundException.PluginNotFoundException(name);
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
