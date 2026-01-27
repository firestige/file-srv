package tech.icc.filesrv.core.domain.services;

import tech.icc.filesrv.core.domain.files.FileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * 去重服务
 * <p>
 * 领域服务接口，负责文件内容去重相关逻辑。
 */
public interface DeduplicationService {

    /**
     * 根据内容哈希查找已存在的文件
     *
     * @param contentHash 内容哈希
     * @return 已存在的 FileInfo，或 empty
     */
    Optional<FileInfo> findByContentHash(String contentHash);

    /**
     * 计算内容哈希（xxHash-64）
     *
     * @param content 文件内容流
     * @return 哈希值（十六进制字符串）
     * @throws IOException 读取异常
     */
    String computeHash(InputStream content) throws IOException;

    /**
     * 增加引用计数（秒传场景）
     *
     * @param contentHash 内容哈希
     * @return 更新后的 FileInfo
     */
    FileInfo incrementReference(String contentHash);

    /**
     * 减少引用计数
     *
     * @param contentHash 内容哈希
     * @return 是否可以 GC（refCount <= 0）
     */
    boolean decrementReference(String contentHash);
}
