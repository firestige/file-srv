package tech.icc.filesrv.common.exception.validation;

import lombok.Getter;
import tech.icc.filesrv.common.constants.ResultCode;

/**
 * 插件未找到异常
 */
@Getter
public class PluginNotFoundException extends ValidationException {
    /** 异常消息中显示的plugin name最大长度 */
    private static final int MAX_DISPLAY_LENGTH = 64;

    private final String pluginName;

    public PluginNotFoundException(String pluginName) {
        super(pluginName, ResultCode.PLUGIN_NOT_FOUND, formatMessage(pluginName));
        this.pluginName = pluginName;
    }

    @Override
    public String getSource() {
        return pluginName;
    }

    /**
     * 格式化异常消息，对超长任务ID进行截断
     */
    private static String formatMessage(String taskId) {
        if (taskId == null) {
            return "任务不存在: null";
        }

        String displayId = taskId.length() <= MAX_DISPLAY_LENGTH
                ? taskId
                : taskId.substring(0, MAX_DISPLAY_LENGTH) + "...(实际长度: " + taskId.length() + ")";

        return String.format("任务不存在: '%s'", displayId);
    }
}
