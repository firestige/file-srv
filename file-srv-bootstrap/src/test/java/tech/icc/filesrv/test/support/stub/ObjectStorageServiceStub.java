package tech.icc.filesrv.test.support.stub;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 对象存储的内存 stub
 * <p>
 * 线程安全的内存实现，用于单元测试。
 * <p>
 * <b>限制说明：</b>
 * <ul>
 *   <li>分片上传：建议限制分片数量 ≤ 10，单个分片 ≤ 5MB</li>
 *   <li>总存储：所有文件存储在内存中，避免大文件测试</li>
 *   <li>会话管理：不实现超时清理，需测试结束后显式清理</li>
 * </ul>
 */
public class ObjectStorageServiceStub implements StorageAdapter {
    
    /** 文件存储：path -> 文件内容 */
    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();
    
    /** 分片会话存储：sessionId -> 会话对象 */
    private final Map<String, InMemoryUploadSession> sessions = new ConcurrentHashMap<>();
    
    /** 会话计数器，用于生成唯一 sessionId */
    private final AtomicLong sessionCounter = new AtomicLong(0);
    
    @Override
    public String getAdapterType() {
        return "primary";  // 测试环境中作为主存储节点
    }

    @Override
    public StorageResult upload(String path, InputStream content, String contentType) {
        try {
            byte[] data = readAllBytes(content);
            storage.put(path, data);
            String etag = calculateETag(data);
            return StorageResult.of(path, etag, (long) data.length);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload content", e);
        }
    }

    @Override
    public Resource download(String path) {
        byte[] data = storage.get(path);
        if (data == null) {
            throw new RuntimeException("File not found: " + path);
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
        // 返回模拟 URL，格式便于测试验证
        return String.format("memory://%s?expiry=%ds", path, expiry.getSeconds());
    }

    @Override
    public UploadSession beginUpload(String path, String contentType) {
        String sessionId = "session-" + sessionCounter.incrementAndGet();
        InMemoryUploadSession session = new InMemoryUploadSession(sessionId, path);
        sessions.put(sessionId, session);
        return session;
    }
    
    @Override
    public UploadSession resumeUpload(String path, String sessionId) {
        InMemoryUploadSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("Session not found or expired: " + sessionId);
        }
        if (!session.getPath().equals(path)) {
            throw new IllegalArgumentException(
                "Path mismatch: session path=" + session.getPath() + ", requested=" + path);
        }
        return session;
    }
    
    /**
     * 清理所有数据（测试用）
     */
    public void clear() {
        storage.clear();
        sessions.clear();
    }
    
    /**
     * 获取存储的文件数量（测试用）
     */
    public int size() {
        return storage.size();
    }
    
    // ==================== 辅助方法 ====================
    
    private byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = input.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
    
    /**
     * 计算简单的 ETag（使用内容 hashCode）
     * <p>
     * 生产环境应该用 MD5，但测试环境为了性能使用简化版本。
     * <p>
     * <b>注意：</b>返回纯 ETag 值，不包含引号（与 HCS Adapter 的 normalizeETag 行为一致）。
     */
    private String calculateETag(byte[] data) {
        int hash = java.util.Arrays.hashCode(data);
        return String.format("%08x", hash);
    }
    
    // ==================== 内部类：分片上传会话 ====================
    
    private class InMemoryUploadSession implements UploadSession {
        private final String sessionId;
        private final String path;
        /** 分片存储：partNumber -> 分片内容 */
        private final Map<Integer, byte[]> parts = new ConcurrentHashMap<>();
        private volatile boolean closed = false;
        
        public InMemoryUploadSession(String sessionId, String path) {
            this.sessionId = sessionId;
            this.path = path;
        }
        
        @Override
        public String getSessionId() {
            return sessionId;
        }
        
        @Override
        public String getPath() {
            return path;
        }
        
        @Override
        public String uploadPart(int partNumber, InputStream data, long size) {
            ensureNotClosed();
            try {
                byte[] partData = readAllBytes(data);
                if (partData.length != size) {
                    throw new IllegalArgumentException(
                        String.format("Part size mismatch: expected %d, got %d", size, partData.length));
                }
                parts.put(partNumber, partData);
                return calculateETag(partData);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload part " + partNumber, e);
            }
        }
        
        @Override
        public String complete(List<PartETagInfo> partInfos) {
            ensureNotClosed();
            
            // 验证所有分片都已上传且 ETag 匹配
            for (PartETagInfo info : partInfos) {
                byte[] partData = parts.get(info.partNumber());
                if (partData == null) {
                    throw new IllegalStateException(
                        "Part " + info.partNumber() + " not uploaded");
                }
                
                // 验证 ETag（模拟真实对象存储的行为）
                String actualETag = calculateETag(partData);
                if (!actualETag.equals(info.etag())) {
                    throw new IllegalStateException(
                        String.format("Part %d ETag mismatch: expected %s, got %s",
                            info.partNumber(), info.etag(), actualETag));
                }
            }
            
            // 按分片序号合并
            try {
                ByteArrayOutputStream merged = new ByteArrayOutputStream();
                for (PartETagInfo info : partInfos) {
                    byte[] partData = parts.get(info.partNumber());
                    merged.write(partData);
                }
                
                byte[] finalData = merged.toByteArray();
                storage.put(path, finalData);
                
                // 清理会话数据
                sessions.remove(sessionId);
                closed = true;
                
                return path;
            } catch (IOException e) {
                throw new RuntimeException("Failed to merge parts", e);
            }
        }
        
        @Override
        public void abort() {
            if (closed) {
                return;  // 幂等：已终止的会话不重复处理
            }
            parts.clear();
            sessions.remove(sessionId);
            closed = true;
        }
        
        @Override
        public void close() {
            // 不清理会话，保持会话存活以支持 resumeUpload
            // 只有 complete() 或 abort() 才真正清理会话数据
        }
        
        private void ensureNotClosed() {
            if (closed) {
                throw new IllegalStateException("Session " + sessionId + " is closed");
            }
        }
    }
}
