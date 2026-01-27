package tech.icc.filesrv.plugin.example;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.core.infra.plugin.PluginResult;
import tech.icc.filesrv.core.infra.plugin.SharedPlugin;

/**
 * 示例插件 - 文件重命名
 */
public class RenamePlugin implements SharedPlugin {

    private static final String PLUGIN_NAME = "rename";

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public PluginResult apply(TaskContext ctx) {
        // 获取重命名参数
        String pattern = ctx.getPluginString(PLUGIN_NAME, "pattern")
                .orElse("{filename}");

        // TODO: 实现重命名逻辑
        String originalName = ctx.getString(TaskContext.KEY_FILENAME).orElse("unknown");
        String newName = pattern
                .replace("{filename}", originalName)
                .replace("{uuid}", java.util.UUID.randomUUID().toString());

        // 记录新文件名到 context
        ctx.put("renamedFilename", newName);

        return PluginResult.Success.of("renamedFilename", newName);
    }
}
