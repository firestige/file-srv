package tech.icc.filesrv.test.support.plugin;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;

/**
 * 测试用缩略图生成插件
 * <p>
 * 用于集成测试，模拟缩略图生成功能。
 * 始终返回成功，不进行实际图片处理。
 */
public class TestThumbnailPlugin implements SharedPlugin {

    @Override
    public String name() {
        return "thumbnail";
    }

    @Override
    public PluginResult apply(TaskContext context) {
        // 读取插件参数（如果有）
        String width = context.getPluginString(name(), "width").orElse("200");
        String height = context.getPluginString(name(), "height").orElse("200");
        String quality = context.getPluginString(name(), "quality").orElse("80");
        String format = context.getPluginString(name(), "format").orElse("jpeg");
        
        // 模拟生成缩略图（实际不做任何操作）
        context.put("thumbnail.generated", "true");
        context.put("thumbnail.width", width);
        context.put("thumbnail.height", height);
        context.put("thumbnail.quality", quality);
        context.put("thumbnail.format", format);
        
        return PluginResult.Success.of("message", "Thumbnail generated (test mode): " + width + "x" + height);
    }

    @Override
    public int order() {
        return 10; // 较低优先级
    }
}
