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
     * 生成预签名下载 URL
     *
     * @param path   存储路径
     * @param expiry 有效期
     * @return 预签名 URL
     */
    String generatePresignedUrl(String path, Duration expiry);

    // ==================== 分片上传 ====================

    /**
     * 开始分片上传会话
     *
     * @param path        存储路径
     * @param contentType MIME 类型
     * @return 上传会话
     */
    UploadSession beginUpload(String path, String contentType);

    /**
     * 恢复已有的上传会话
     * <p>
     * 用于断点续传场景，通过 sessionId 恢复之前的上传会话。
     *
     * @param path      存储路径
     * @param sessionId 之前的会话 ID
     * @return 上传会话
     */
    default UploadSession resumeUpload(String path, String sessionId) {
        throw new UnsupportedOperationException("Resume upload not supported by " + getAdapterType());
    }
}
