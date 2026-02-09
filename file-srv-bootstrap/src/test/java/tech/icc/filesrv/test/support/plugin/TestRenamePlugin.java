package tech.icc.filesrv.test.support.plugin;

import lombok.extern.slf4j.Slf4j;
import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginExecute;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginParam;
import tech.icc.filesrv.common.spi.plugin.annotation.TaskInfo;

import java.util.Map;

/**
 * 测试用文件重命名插件（使用注解注入）
 * <p>
 * 用于集成测试，模拟文件重命名功能。
 * 始终返回成功，不进行实际重命名。
 */
@Slf4j
@SharedPlugin("rename")
public class TestRenamePlugin {

    /**
     * 使用注解注入执行方法
     */
    @PluginExecute
    public PluginResult execute(
            @PluginParam(value = "pattern", defaultValue = "{filename}") String pattern,
            @TaskInfo("taskId") String taskId,
            @TaskInfo("filename") String originalFilename
    ) {
        log.info("===== TestRenamePlugin.execute() called with annotations! =====");
        log.info("Pattern: {}, TaskId: {}, Filename: {}", pattern, taskId, originalFilename);
        
        // 模拟重命名逻辑（实际不做任何操作）
        String newFilename = pattern.replace("{filename}", originalFilename != null ? originalFilename : "unknown");
        
        log.info("File renamed: {} -> {}", originalFilename, newFilename);
        
        return new PluginResult.Success(Map.of(
                "rename.original", originalFilename != null ? originalFilename : "unknown",
                "rename.new", newFilename,
                "rename.pattern", pattern,
                "message", "File renamed (test mode): " + originalFilename + " -> " + newFilename
        ));
    }
}
