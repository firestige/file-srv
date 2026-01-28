/**
 * 存储适配器 SPI 契约
 * <p>
 * 定义与底层存储交互的标准接口，供 adapter 模块实现。
 * <ul>
 *   <li>{@link tech.icc.filesrv.common.spi.storage.StorageAdapter} - 存储适配器主接口</li>
 *   <li>{@link tech.icc.filesrv.common.spi.storage.StorageResult} - 上传结果</li>
 *   <li>{@link tech.icc.filesrv.common.spi.storage.UploadSession} - 分片上传会话</li>
 *   <li>{@link tech.icc.filesrv.common.spi.storage.PartETagInfo} - 分片 ETag 信息</li>
 * </ul>
 * <p>
 * 设计说明：此包未来可独立拆分为 file-srv-spi 模块。
 */
package tech.icc.filesrv.common.spi.storage;
