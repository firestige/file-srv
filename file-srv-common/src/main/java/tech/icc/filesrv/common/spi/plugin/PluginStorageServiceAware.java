package tech.icc.filesrv.common.spi.plugin;

/**
 * Plugin 存储服务感知接口
 * <p>
 * 插件如果需要使用存储服务（上传/下载大文件、生成临时URL等），
 * 可以实现此接口。执行框架会在调用 {@link SharedPlugin#apply} 前
 * 自动注入 PluginStorageService 实例。
 * </p>
 * 
 * <h3>使用示例</h3>
 * <pre>
 * public class VideoTranscodePlugin implements SharedPlugin, PluginStorageServiceAware {
 *     private PluginStorageService storageService;
 *     
 *     {@literal @}Override
 *     public void setPluginStorageService(Object service) {
 *         this.storageService = (PluginStorageService) service;
 *     }
 *     
 *     {@literal @}Override
 *     public PluginResult apply(TaskContext ctx) {
 *         // 转码处理...
 *         
 *         // 上传转码后的文件
 *         String fkey = storageService.uploadLargeFile(
 *             transcodedStream, "video_480p.mp4", "video/mp4", fileSize
 *         );
 *         
 *         // 添加衍生文件记录
 *         ctx.addDerivedFile(new DerivedFile(fkey, "TRANSCODED", ...));
 *         
 *         return PluginResult.Success.empty();
 *     }
 * }
 * </pre>
 *
 * @see SharedPlugin
 */
public interface PluginStorageServiceAware {

    /**
     * 设置插件存储服务
     * <p>
     * 由执行框架在 Plugin 执行前调用。
     * 插件应将服务实例保存到字段，供 {@link SharedPlugin#apply} 方法使用。
     * </p>
     *
     * @param service 存储服务实例（实际类型为 PluginStorageService，
     *                使用 Object 避免 file-srv-common 模块依赖 file-srv-core）
     */
    void setPluginStorageService(Object service);
}
