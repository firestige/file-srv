package tech.icc.filesrv.core.domain.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.hashing.LongHashFunction;
import net.openhft.hashing.LongTupleHashFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.icc.filesrv.core.domain.files.FileInfo;
import tech.icc.filesrv.core.domain.files.FileInfoRepository;
import tech.icc.filesrv.core.domain.files.FileStatus;
import tech.icc.filesrv.core.domain.services.DeduplicationService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * 去重服务实现
 * <p>
 * 使用 xxHash-64 计算内容哈希，支持秒传和引用计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationServiceImpl implements DeduplicationService {

    private static final int BUFFER_SIZE = 8192;
    private static final long XXHASH_SEED = 0L;

    private final FileInfoRepository fileInfoRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<FileInfo> findByContentHash(String contentHash) {
        return fileInfoRepository.findByContentHash(contentHash)
                .filter(info -> info.status() == FileStatus.ACTIVE);
    }

    @Override
    public String computeHash(InputStream content) throws IOException {
        LongHashFunction xxHash = LongHashFunction.xx(XXHASH_SEED);
        byte[] buffer = new byte[BUFFER_SIZE];

        // 简化实现：读取全部内容计算哈希
        // TODO: 对于大文件，考虑使用流式哈希或分块哈希
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int bytesRead;
        while ((bytesRead = content.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }

        byte[] data = baos.toByteArray();
        long hash = xxHash.hashBytes(data);

        // 转换为16位十六进制字符串
        return String.format("%016x", hash);
    }

    @Override
    public String computeHashAndCopy(InputStream content, OutputStream output) throws IOException {
        LongHashFunction xxHash = LongHashFunction.xx(XXHASH_SEED);
        byte[] buffer = new byte[BUFFER_SIZE];

        // 边读取边写入边计算哈希
        ByteArrayOutputStream hashBuffer = new ByteArrayOutputStream();
        int bytesRead;
        while ((bytesRead = content.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            hashBuffer.write(buffer, 0, bytesRead);
        }
        output.flush();

        byte[] data = hashBuffer.toByteArray();
        long hash = xxHash.hashBytes(data);

        return String.format("%016x", hash);
    }

    @Override
    @Transactional
    public FileInfo incrementReference(String contentHash) {
        log.debug("Incrementing reference count for: {}", contentHash);

        int updated = fileInfoRepository.incrementRefCount(contentHash);
        if (updated == 0) {
            throw new IllegalStateException("Failed to increment ref count: " + contentHash);
        }

        return fileInfoRepository.findByContentHash(contentHash)
                .orElseThrow(() -> new IllegalStateException("FileInfo not found: " + contentHash));
    }

    @Override
    @Transactional
    public boolean decrementReference(String contentHash) {
        log.debug("Decrementing reference count for: {}", contentHash);

        int updated = fileInfoRepository.decrementRefCount(contentHash);
        if (updated == 0) {
            log.warn("Failed to decrement ref count (may already be 0): {}", contentHash);
            return false;
        }

        return fileInfoRepository.findByContentHash(contentHash)
                .map(FileInfo::canGC)
                .orElse(false);
    }
}
