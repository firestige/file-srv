package tech.icc.filesrv.plugins.example;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.*;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginExecute;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * 缩略图生成插件
 * <p>
 * 配置示例：
 * <pre>
 * {
 *   "name": "thumbnail",
 *   "params": [
 *     {"key": "width", "value": "400"},
 *     {"key": "height", "value": "300"},
 *     {"key": "quality", "value": "85"}
 *   ]
 * }
 * </pre>
 */
@SharedPlugin
public class ThumbnailPlugin implements Plugin {

    @Override
    public String name() {
        return "thumbnail";
    }

    @PluginExecute
    public PluginResult execute(TaskContext context) {
        // 获取参数
        int width = context.getPluginParam("width").map(Integer::parseInt).orElse(400);
        int height = context.getPluginParam("height").map(Integer::parseInt).orElse(300);
        int quality = context.getPluginParam("quality").map(Integer::parseInt).orElse(80);
        Path localFile = context.getLocalFilePath();
        String taskId = context.getTaskId().orElse("");
        
        System.out.println("Generating thumbnail: taskId=" + taskId + ", width=" + width + 
                ", height=" + height + ", quality=" + quality + ", file=" + localFile);

        try {
            // 1. 生成缩略图（模拟）
            String thumbnailPath = generateThumbnail(localFile, width, height, quality);
            
            // 2. 注册衍生文件到 context
            context.addDerivedFile(
                    "thumbnail-" + taskId + ".jpg",
                    new File(thumbnailPath),
                    12345L
            );
            
            // 3. 返回输出数据（供后续插件使用）
            return PluginResult.success(Map.of(
                    "thumbnail.generated", true,
                    "thumbnail.width", width,
                    "thumbnail.height", height,
                    "thumbnail.path", thumbnailPath
            ));
            
        } catch (Exception e) {
            System.err.println("Failed to generate thumbnail: " + e.getMessage());
            return PluginResult.failure("Thumbnail generation failed: " + e.getMessage());
        }
    }

    private String generateThumbnail(Path source, int width, int height, int quality) {
        // 模拟缩略图生成
        System.out.println("Generating thumbnail from: " + source);
        return "/storage/thumbnails/" + source.getFileName() + "_thumb.jpg";
    }
}
