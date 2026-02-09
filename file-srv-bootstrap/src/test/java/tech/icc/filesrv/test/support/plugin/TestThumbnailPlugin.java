package tech.icc.filesrv.test.support.plugin;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginExecute;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginParam;

import java.util.Map;

/**
 * 测试用缩略图生成插件（使用注解注入）
 * <p>
 * 用于集成测试，模拟缩略图生成功能。
 * 始终返回成功，不进行实际图片处理。
 */
@SharedPlugin("thumbnail")
public class TestThumbnailPlugin {

    /**
     * 使用注解注入执行方法
     */
    @PluginExecute
    public PluginResult execute(
            @PluginParam(value = "width", defaultValue = "200") Integer width,
            @PluginParam(value = "height", defaultValue = "200") Integer height,
            @PluginParam(value = "quality", defaultValue = "80") Integer quality,
            @PluginParam(value = "format", defaultValue = "jpeg") String format
    ) {
        // 模拟生成缩略图（实际不做任何操作）
        return new PluginResult.Success(Map.of(
                "thumbnail.generated", "true",
                "thumbnail.width", width.toString(),
                "thumbnail.height", height.toString(),
                "thumbnail.quality", quality.toString(),
                "thumbnail.format", format,
                "message", "Thumbnail generated (test mode): " + width + "x" + height
        ));
    }
}
