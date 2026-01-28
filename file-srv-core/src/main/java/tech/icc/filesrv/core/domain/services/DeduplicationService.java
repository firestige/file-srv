package tech.icc.filesrv.core.domain.services;

import tech.icc.filesrv.core.domain.files.FileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
     * 计算内容哈希（xxHash-64）
     * <p>
     * 适用于小文件（如 10MB 以内），已加载到内存的场景。
     *
     * @param content 文件内容字节数组
     * @return 哈希值（十六进制字符串）
     */
    String computeHash(byte[] content);

    /**
     * 计算内容哈希，同时将内容写入输出流
     * <p>
     * 用于避免多次读取输入流：边计算哈希边写入临时文件或存储。
     *
     * @param content 文件内容流
     * @param output  输出流（如临时文件）
     * @return 哈希值（十六进制字符串）
     * @throws IOException 读取/写入异常
     */
    String computeHashAndCopy(InputStream content, OutputStream output) throws IOException;

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
