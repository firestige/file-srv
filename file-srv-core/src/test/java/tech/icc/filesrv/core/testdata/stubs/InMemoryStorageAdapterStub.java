package tech.icc.filesrv.core.testdata.stubs;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import tech.icc.filesrv.common.spi.storage.PartETagInfo;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;
import tech.icc.filesrv.common.spi.storage.StorageResult;
import tech.icc.filesrv.common.spi.storage.UploadSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存存储适配器 Stub
 * <p>
 * 用于单元测试，完全在内存中模拟文件存储，无需真实的存储服务。
 * <p>
 * 特性：
 * <ul>
 *   <li>支持文件上传/下载/删除</li>
 *   <li>支持分片上传会话</li>
 *   <li>支持预签名 URL 生成（返回模拟 URL）</li>
 *   <li>提供测试辅助方法（验证、清空）</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * InMemoryStorageAdapterStub storage = new InMemoryStorageAdapterStub();
 * 
 * // 上传文件
 * StorageResult result = storage.upload("test/file.txt", inputStream, "text/plain");
 * 
 * // 下载文件
 * Resource resource = storage.download("test/file.txt");
 * 
 * // 验证文件存在
 * assertTrue(storage.exists("test/file.txt"));
 * 
 * // 测试完成后清空
 * storage.clear();
 * }</pre>
 */
public class InMemoryStorageAdapterStub implements StorageAdapter {

    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();
    private final Map<String, InMemoryUploadSession> sessions = new ConcurrentHashMap<>();
    private final String adapterType;

    public InMemoryStorageAdapterStub() {
        this("IN_MEMORY_STUB");
    }

    public InMemoryStorageAdapterStub(String adapterType) {
        this.adapterType = adapterType;
    }

    @Override
    public String getAdapterType() {
        return adapterType;
    }

    @Override
    public StorageResult upload(String path, InputStream content, String contentType) {
        try {
            byte[] data = readAllBytes(content);
            storage.put(path, data);
            return new StorageResult(
                    path,
                    calculateChecksum(data),
                    (long) data.length,
                    Instant.now()
            );
        } catch (IOException e) {
            throw new RuntimeException("Upload failed: " + path, e);
        }
    }

    @Override
    public Resource download(String path) {
        byte[] data = storage.get(path);
        if (data == null) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        return new ByteArrayResource(data);
    }

    @Override
    public void delete(String path) {
        storage.remove(path);
    }

    @Override
    public boolean exists(String path) {
        return storage.containsKey(path);
    }

    @Override
    public String generatePresignedUrl(String path, Duration expiry) {
        if (!exists(path)) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        // 返回模拟的预签名 URL
        return "http://stub-storage.local/presigned/" + path + "?expires=" + expiry.getSeconds();
    }

    @Override
    public UploadSession beginUpload(String path, String contentType) {
        String sessionId = UUID.randomUUID().toString();
        InMemoryUploadSession session = new InMemoryUploadSession(sessionId, path, contentType);
        sessions.put(sessionId, session);
        return session;
    }

    @Override
    public UploadSession resumeUpload(String path, String sessionId) {
        InMemoryUploadSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        if (!session.path.equals(path)) {
            throw new IllegalArgumentException("Session path mismatch");
        }
        return session;
    }

    // ==================== 测试辅助方法 ====================

    /**
     * 获取存储的文件内容
     */
    public byte[] getStoredFile(String path) {
        return storage.get(path);
    }

    /**
     * 获取存储的文件数量
     */
    public int getFileCount() {
        return storage.size();
    }

    /**
     * 获取活动会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 清空所有存储和会话
     */
    public void clear() {
        storage.clear();
        sessions.clear();
    }

    /**
     * 验证文件内容
     */
    public boolean verifyFileContent(String path, byte[] expectedContent) {
        byte[] actualContent = storage.get(path);
        if (actualContent == null) {
            return false;
        }
        if (actualContent.length != expectedContent.length) {
            return false;
        }
        for (int i = 0; i < actualContent.length; i++) {
            if (actualContent[i] != expectedContent[i]) {
                return false;
            }
        }
        return true;
    }

    // ==================== 内部类 ====================

    /**
     * 内存上传会话实现
     */
    private class InMemoryUploadSession implements UploadSession {
        private final String sessionId;
        private final String path;
        private final String contentType;
        private final Map<Integer, byte[]> parts = new ConcurrentHashMap<>();
        private boolean closed = false;

        public InMemoryUploadSession(String sessionId, String path, String contentType) {
            this.sessionId = sessionId;
            this.path = path;
            this.contentType = contentType;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public String uploadPart(int partNumber, InputStream data, long size) {
            checkNotClosed();
            try {
                byte[] partData = readAllBytes(data);
                parts.put(partNumber, partData);
                return "etag-" + partNumber + "-" + calculateChecksum(partData);
            } catch (IOException e) {
                throw new RuntimeException("Upload part failed: " + partNumber, e);
            }
        }

        @Override
        public String complete(List<PartETagInfo> parts) {
            checkNotClosed();
            
            // 合并所有分片
            ByteArrayOutputStream merged = new ByteArrayOutputStream();
            for (int i = 1; i <= parts.size(); i++) {
                byte[] partData = this.parts.get(i);
                if (partData == null) {
                    throw new IllegalStateException("Missing part: " + i);
                }
                try {
                    merged.write(partData);
                } catch (IOException e) {
                    throw new RuntimeException("Merge failed", e);
                }
            }
            
            // 存储完整文件
            byte[] fileData = merged.toByteArray();
            storage.put(path, fileData);
            
            // 清理会话
            sessions.remove(sessionId);
            closed = true;
            
            return path;
        }

        @Override
        public void abort() {
            if (!closed) {
                parts.clear();
                sessions.remove(sessionId);
                closed = true;
            }
        }

        @Override
        public void close() {
            if (!closed) {
                abort();
            }
        }

        private void checkNotClosed() {
            if (closed) {
                throw new IllegalStateException("Session already closed: " + sessionId);
            }
        }
    }

    // ==================== 工具方法 ====================

    private byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private String calculateChecksum(byte[] data) {
        // 简单的校验和计算（测试用）
        long sum = 0;
        for (byte b : data) {
            sum += b & 0xFF;
        }
        return "checksum-" + Long.toHexString(sum);
    }
}
