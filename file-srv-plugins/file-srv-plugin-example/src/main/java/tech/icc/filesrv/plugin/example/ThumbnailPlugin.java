package tech.icc.filesrv.plugin.example;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.context.TaskContextKeys;
import tech.icc.filesrv.common.vo.task.DerivedFile;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;

import java.util.UUID;

/**
 * 【类型 B: 衍生文件生成类插件】 - 缩略图生成
 * <p>
 * <b>插件类型说明：</b>
 * <p>
 * 衍生文件生成类插件用于基于原始文件创建新的关联文件，如缩略图、预览图、转码文件、加水印文件等。
 * 生成的文件作为 {@link DerivedFile} 关联到主文件的 FileReference。
 * <p>
 * <b>操作逻辑：</b>
 * <ol>
 *   <li>从 TaskContext 读取插件参数（如 thumbnail.width, thumbnail.height）</li>
 *   <li>从 TaskContext 获取本地文件路径 {@code KEY_LOCAL_FILE_PATH}</li>
 *   <li>读取原始文件，执行处理逻辑（如图片缩放、视频转码）</li>
 *   <li>将生成的衍生文件上传到存储层（通过注入的 StorageAdapter）</li>
 *   <li>通过 {@code ctx.addDerivedFile()} 记录衍生文件信息</li>
 *   <li>清理处理过程中的临时文件</li>
 *   <li>返回 {@link PluginResult.Success#empty()}，无需额外输出</li>
 * </ol>
 * <p>
 * <b>衍生文件存储路径约定：</b>
 * <pre>{@code
 * 原始文件: /ab/abcd1234/abcd1234567890.jpg
 * 缩略图:   /ab/abcd1234/abcd1234567890_thumbnail.webp
 * 预览图:   /ab/abcd1234/abcd1234567890_preview.jpg
 * }</pre>
 * <p>
 * <b>注意事项：</b>
 * <ul>
 *   <li>衍生文件独立存储，拥有自己的 fileId 和 path</li>
 *   <li>若处理失败，应返回 {@link PluginResult.Failure}，整个 callback 链会标记失败</li>
 *   <li>大文件处理可能耗时较长，建议实现进度上报（Future）</li>
 * </ul>
 * <p>
 * <b>参数配置示例：</b>
 * <pre>{@code
 * {
 *   "thumbnail.width": 200,
 *   "thumbnail.height": 200,
 *   "thumbnail.format": "webp",
 *   "thumbnail.quality": 80
 * }
 * }</pre>
 *
 * @see DerivedFile
 */
public class ThumbnailPlugin implements SharedPlugin {

    private static final String PLUGIN_NAME = "thumbnail";

    // 参数 Key
    private static final String PARAM_WIDTH = "width";
    private static final String PARAM_HEIGHT = "height";
    private static final String PARAM_FORMAT = "format";
    private static final String PARAM_QUALITY = "quality";

    // 默认值
    private static final int DEFAULT_WIDTH = 200;
    private static final int DEFAULT_HEIGHT = 200;
    private static final String DEFAULT_FORMAT = "webp";
    private static final int DEFAULT_QUALITY = 80;

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public PluginResult apply(TaskContext ctx) {
        // 1. 获取参数
        int width = ctx.getPluginInt(PLUGIN_NAME, PARAM_WIDTH).orElse(DEFAULT_WIDTH);
        int height = ctx.getPluginInt(PLUGIN_NAME, PARAM_HEIGHT).orElse(DEFAULT_HEIGHT);
        String format = ctx.getPluginString(PLUGIN_NAME, PARAM_FORMAT).orElse(DEFAULT_FORMAT);
        int quality = ctx.getPluginInt(PLUGIN_NAME, PARAM_QUALITY).orElse(DEFAULT_QUALITY);

        // 2. 获取源文件路径
        String localFilePath = ctx.getLocalFilePath()
                .orElse(null);
        if (localFilePath == null) {
            return PluginResult.Failure.of("Local file path not found in context");
        }

        String storagePath = ctx.getStoragePath()
                .orElse(null);
        if (storagePath == null) {
            return PluginResult.Failure.of("Storage path not found in context");
        }

        // 3. 检查是否为图片类型
        String contentType = ctx.getString(TaskContextKeys.KEY_CONTENT_TYPE).orElse("");
        if (!contentType.startsWith("image/")) {
            // 非图片文件，跳过处理
            return PluginResult.Skip.of("Not an image file, skipping thumbnail generation");
        }

        // 4. 生成缩略图（示例实现，实际需要图像处理库如 Thumbnailator）
        // Path thumbnailPath = generateThumbnail(localFilePath, width, height, format, quality);

        // 5. 上传缩略图到存储层
        String thumbnailStoragePath = buildDerivedPath(storagePath, "thumbnail", format);
        // storageAdapter.put(thumbnailStoragePath, Files.newInputStream(thumbnailPath), size, mimeType);

        // 6. 记录衍生文件
        String thumbnailFileId = UUID.randomUUID().toString();
        String thumbnailContentType = "image/" + format;
        // long thumbnailSize = Files.size(thumbnailPath);

        // 示例：记录衍生文件信息
        ctx.addDerivedFile(DerivedFile.builder()
                .type("THUMBNAIL")
                .fileId(thumbnailFileId)
                .path(thumbnailStoragePath)
                .contentType(thumbnailContentType)
                .size(0L)  // 实际应为生成文件的大小
                .build());

        // 7. 清理临时文件
        // Files.deleteIfExists(thumbnailPath);

        // 8. 返回成功
        return PluginResult.Success.empty();
    }

    /**
     * 构建衍生文件存储路径
     * <p>
     * 示例:
     * <ul>
     *   <li>原路径: /ab/abcd1234/abcd1234567890.jpg</li>
     *   <li>衍生路径: /ab/abcd1234/abcd1234567890_thumbnail.webp</li>
     * </ul>
     *
     * @param originalPath 原始文件存储路径
     * @param suffix       衍生文件后缀标识（如 thumbnail, preview）
     * @param format       输出格式（如 webp, jpg）
     * @return 衍生文件存储路径
     */
    private String buildDerivedPath(String originalPath, String suffix, String format) {
        int lastDot = originalPath.lastIndexOf('.');
        String basePath = lastDot > 0 ? originalPath.substring(0, lastDot) : originalPath;
        return basePath + "_" + suffix + "." + format;
    }

    // ==================== 以下为实际实现时需要的方法（示例省略）====================

    /*
    private Path generateThumbnail(String sourcePath, int width, int height,
                                   String format, int quality) throws IOException {
        Path tempFile = Files.createTempFile("thumbnail_", "." + format);

        // 使用 Thumbnailator 或其他图像处理库
        Thumbnails.of(sourcePath)
                .size(width, height)
                .outputFormat(format)
                .outputQuality(quality / 100.0)
                .toFile(tempFile.toFile());

        return tempFile;
    }
    */
}
