package tech.icc.filesrv.test.support.plugin;

import lombok.extern.slf4j.Slf4j;
import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;

/**
 * 测试用文件重命名插件
 * <p>
 * 用于集成测试，模拟文件重命名功能。
 * 始终返回成功，不进行实际重命名。
 */
@Slf4j
public class TestRenamePlugin implements SharedPlugin {

    @Override
    public String name() {
        return "rename";
    }

    @Override
    public PluginResult apply(TaskContext context) {
        log.info("===== TestRenamePlugin.apply() called! =====");
        
        // 调试：打印 context 中所有的键
        log.info("Context keys: {}", context.getAvailableKeys());
        log.info("Context data for 'rename': {}", context.get("rename"));
        log.info("Context data for 'plugin_rename': {}", context.get("plugin_rename"));
        
        // 读取重命名模式参数
        String pattern = context.getPluginString(name(), "pattern")
                .orElse("{filename}");
        
        log.info("Got pattern from getPluginString: {}", pattern);
        
        // 模拟重命名逻辑（实际不做任何操作）
        String originalFilename = context.getString(TaskContext.KEY_FILENAME).orElse("unknown");
        String newFilename = pattern.replace("{filename}", originalFilename);
        
        log.info("Putting rename.original={}, rename.new={}, rename.pattern={}", originalFilename, newFilename, pattern);
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
