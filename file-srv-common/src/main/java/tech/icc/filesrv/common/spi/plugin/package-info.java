/**
 * 插件 SPI 契约
 * <p>
 * 定义文件处理插件的标准接口，供 plugin 模块实现。
 * <ul>
 *   <li>{@link tech.icc.filesrv.common.spi.plugin.SharedPlugin} - 插件主接口</li>
 *   <li>{@link tech.icc.filesrv.common.spi.plugin.PluginResult} - 执行结果（Success/Failure/Skip）</li>
 * </ul>
 * <p>
 * 设计说明：此包未来可独立拆分为 file-srv-spi 模块。
 */
package tech.icc.filesrv.common.spi.plugin;
