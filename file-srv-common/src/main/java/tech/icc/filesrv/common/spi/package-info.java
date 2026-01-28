/**
 * SPI (Service Provider Interface) 契约包
 * <p>
 * 定义 file-srv 系统的扩展点接口，供外部模块（adapters、plugins）实现。
 * <ul>
 *   <li>{@link tech.icc.filesrv.common.spi.storage} - 存储适配器契约</li>
 *   <li>{@link tech.icc.filesrv.common.spi.plugin} - 插件契约</li>
 * </ul>
 * <p>
 * 架构说明：
 * <pre>
 *   common (含 spi 契约)
 *      ↑
 *     core
 *      ↑
 *   adapters / plugins (只需依赖 common 即可实现 SPI)
 * </pre>
 * <p>
 * 当项目规模增长后，此包可独立拆分为 file-srv-spi 模块，
 * 届时只需修改 pom 依赖，import 路径无需变更。
 */
package tech.icc.filesrv.common.spi;
