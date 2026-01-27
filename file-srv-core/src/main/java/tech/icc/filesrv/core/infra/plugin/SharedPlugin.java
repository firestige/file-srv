package tech.icc.filesrv.core.infra.plugin;

import tech.icc.filesrv.common.context.TaskContext;

/**
 * 共享插件接口
 * <p>
 * 定义文件处理插件的生命周期和执行契约。
 * 插件通过 Spring 自动发现机制注册到 {@link PluginRegistry}。
 */
public interface SharedPlugin {

    /**
     * 插件唯一标识
     * <p>
     * 用于在 callback 参数中引用此插件。
     *
     * @return 插件名称，如 "thumbnail", "watermark", "rename"
     */
    String name();

    /**
     * 初始化
     * <p>
     * 容器启动时调用，用于预加载资源。
     */
    default void init() {
    }

    /**
     * 执行插件逻辑
     * <p>
     * 从 TaskContext 读取参数，执行处理，将输出写回 TaskContext。
     * <ul>
     *   <li>通过 {@code context.getPluginParam(name(), "paramKey")} 读取插件参数</li>
     *   <li>通过 {@code context.getLocalFilePath()} 获取本地文件路径</li>
     *   <li>通过 {@code context.addDerivedFile()} 添加衍生文件</li>
     * </ul>
     *
     * @param context 任务上下文
     * @return 执行结果
     */
    PluginResult apply(TaskContext context);

    /**
     * 资源释放
     * <p>
     * 容器关闭时调用，用于清理资源。
     */
    default void release() {
    }

    /**
     * 插件执行优先级
     * <p>
     * 数值越小优先级越高，默认为 0。
     * 仅在未显式指定 callback 顺序时使用。
     *
     * @return 优先级
     */
    default int priority() {
        return 0;
    }
}
