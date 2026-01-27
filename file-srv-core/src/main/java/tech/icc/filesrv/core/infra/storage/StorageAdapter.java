package tech.icc.filesrv.core.infra.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.time.Duration;

/**
 * 存储适配器接口
 * <p>
 * 基础设施层接口，定义与底层存储交互的契约。
 * 具体实现在 file-srv-adapters 模块。
 */
public interface StorageAdapter {

    /**
     * 获取适配器类型标识
     *
     * @return 类型标识，如 "HCS_OBS", "AWS_S3", "LOCAL"
     */
    String getAdapterType();

    /**
     * 上传文件
     *
     * @param path        存储路径
     * @param content     文件内容流
     * @param contentType MIME 类型
     * @return 上传结果
     */
    StorageResult upload(String path, InputStream content, String contentType);

    /**
     * 下载文件
     *
     * @param path 存储路径
     * @return 文件资源
     */
    Resource download(String path);

    /**
     * 删除文件
     *
     * @param path 存储路径
     */
    void delete(String path);

    /**
     * 检查文件是否存在
     *
     * @param path 存储路径
     * @return 是否存在
     */
    boolean exists(String path);

    /**
     * 生成预签名 URL
     *
     * @param path   存储路径
     * @param expiry 有效期
     * @return 预签名 URL
     */
    String generatePresignedUrl(String path, Duration expiry);
}
