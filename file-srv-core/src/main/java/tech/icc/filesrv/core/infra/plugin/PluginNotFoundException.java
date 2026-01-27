package tech.icc.filesrv.core.infra.plugin;

/**
 * 插件未找到异常
 */
public class PluginNotFoundException extends RuntimeException {

    private final String pluginName;

    public PluginNotFoundException(String pluginName) {
        super("Plugin not found: " + pluginName);
        this.pluginName = pluginName;
    }

    public String getPluginName() {
        return pluginName;
    }
}
