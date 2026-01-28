package tech.icc.filesrv.common.spi.storage;

import java.io.InputStream;
import java.util.List;

/**
 * 分片上传会话
 * <p>
 * 封装 S3/OBS 等存储的分片上传细节，简化调用方使用。
 * <ul>
 *   <li>屏蔽不同存储的分片 API 差异</li>
 *   <li>支持会话持久化与恢复</li>
 *   <li>实现 AutoCloseable 以便资源释放</li>
 * </ul>
 */
public interface UploadSession extends AutoCloseable {

    /**
     * 获取会话 ID
     * <p>
     * 用于持久化和恢复。对于 S3/OBS 实现，这通常是 uploadId。
     *
     * @return 会话 ID
     */
    String getSessionId();

    /**
     * 获取目标存储路径
     *
     * @return 存储路径
     */
    String getPath();

    /**
     * 上传单个分片
     *
     * @param partNumber 分片序号 (1-based)
     * @param data       分片数据流
     * @param size       分片大小
     * @return 该分片的 ETag
     */
    String uploadPart(int partNumber, InputStream data, long size);

    /**
     * 完成上传，合并所有分片
     *
     * @param parts 所有分片的 ETag 信息 (partNumber + etag)
     * @return 最终文件的存储路径
     */
    String complete(List<PartETagInfo> parts);

    /**
     * 中止上传，清理已上传的分片
     */
    void abort();

    /**
     * 资源释放
     */
    @Override
    void close();
}
