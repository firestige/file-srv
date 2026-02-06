package tech.icc.filesrv.plugins.example;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.*;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginExecute;

import java.util.Map;

/**
 * 文件重命名插件
 * <p>
 * 配置示例：
 * <pre>
 * {
 *   "name": "rename",
 *   "params": [
 *     {"key": "pattern", "value": "processed_{filename}"}
 *   ]
 * }
 * </pre>
 */
@SharedPlugin
public class RenamePlugin implements Plugin {

    @Override
    public String name() {
        return "rename";
    }

    @PluginExecute
    public PluginResult execute(TaskContext context) {
        String pattern = context.getPluginParam("pattern").orElse("{filename}");
        String taskId = context.getTaskId().orElse("");
        
        System.out.println("Renaming file: taskId=" + taskId + ", pattern=" + pattern);

        try {
            String currentFilename = "original.jpg"; // 模拟
            String newFilename = applyPattern(pattern, currentFilename);
            
            System.out.println("File renamed: " + currentFilename + " -> " + newFilename);
            
            return PluginResult.success(Map.of(
                    "rename.oldName", currentFilename,
                    "rename.newName", newFilename,
                    "rename.pattern", pattern
            ));
            
        } catch (Exception e) {
            System.err.println("Failed to rename file: " + e.getMessage());
            return PluginResult.failure("Rename failed: " + e.getMessage());
        }
    }

    private String applyPattern(String pattern, String currentFilename) {
        return pattern
                .replace("{filename}", removeExtension(currentFilename))
                .replace("{ext}", getExtension(currentFilename));
    }

    private String removeExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex) : "";
    }
}
