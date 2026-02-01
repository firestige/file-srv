package tech.icc.filesrv.test.support.plugin;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;

/**
 * 测试用文件重命名插件
 * <p>
 * 用于集成测试，模拟文件重命名功能。
 * 始终返回成功，不进行实际重命名。
 */
public class TestRenamePlugin implements SharedPlugin {

    @Override
    public String name() {
        return "rename";
    }

    @Override
    public PluginResult apply(TaskContext context) {
        // 读取重命名模式参数
        String pattern = context.getPluginString(name(), "pattern")
                .orElse("{filename}");
        
        // 模拟重命名逻辑（实际不做任何操作）
        String originalFilename = context.getString(TaskContext.KEY_FILENAME).orElse("unknown");
        String newFilename = pattern.replace("{filename}", originalFilename);
        
        context.put("rename.original", originalFilename);
        context.put("rename.new", newFilename);
        context.put("rename.pattern", pattern);
        
        return PluginResult.Success.of("message", "File renamed (test mode): " + originalFilename + " -> " + newFilename);
    }

    @Override
    public int order() {
        return 5; // 中等优先级
    }
}
