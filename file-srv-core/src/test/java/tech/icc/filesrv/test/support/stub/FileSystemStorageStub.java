package tech.icc.filesrv.test.support.stub;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import tech.icc.filesrv.common.spi.storage.PartETagInfo;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;
import tech.icc.filesrv.common.spi.storage.StorageResult;
import tech.icc.filesrv.common.spi.storage.UploadSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文件系统存储的测试 stub
 * <p>
 * 模拟 NFS/NAS 类型的文件存储，使用本地文件系统实现。
 * 分片上传采用 RandomAccessFile 随机写入策略。
 * <p>
 * <b>限制说明：</b>
 * <ul>
 *   <li>分片上传：建议限制分片数量 ≤ 10，单个分片 ≤ 5MB</li>
 *   <li>存储位置：使用临时目录，测试结束后需清理</li>
 *   <li>线程安全：支持并发操作</li>
 * </ul>
 */
public class FileSystemStorageStub implements StorageAdapter {
    
    /** 存储根目录 */
    private final Path rootDir;
    
    /** 分片会话存储：sessionId -> 会话对象 */
    private final Map<String, FileSystemUploadSession> sessions = new ConcurrentHashMap<>();
    
    /** 会话计数器，用于生成唯一 sessionId */
    private final AtomicLong sessionCounter = new AtomicLong(0);
    
    /** 默认分片大小（用于计算偏移量）：5MB */
    private static final long DEFAULT_PART_SIZE = 5 * 1024 * 1024;
    
    /**
     * 创建文件系统存储 stub
     * 
     * @param rootDir 存储根目录
     */
    public FileSystemStorageStub(Path rootDir) {
        this.rootDir = rootDir;
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage root directory: " + rootDir, e);
        }
    }
    
    /**
     * 使用临时目录创建
     */
    public static FileSystemStorageStub createTemp() {
        try {
            Path tempDir = Files.createTempDirectory("filesrv-test-");
            return new FileSystemStorageStub(tempDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory", e);
        }
    }
    
    @Override
    public String getAdapterType() {
        return "filesystem-stub";
    }

    @Override
    public StorageResult upload(String path, InputStream content, String contentType) {
        try {
            Path filePath = resolvePath(path);
            Files.createDirectories(filePath.getParent());
            
            long size = Files.copy(content, filePath, StandardCopyOption.REPLACE_EXISTING);
            String etag = calculateETag(filePath);
            
            return StorageResult.of(path, etag, size);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file: " + path, e);
        }
    }

    @Override
    public Resource download(String path) {
        Path filePath = resolvePath(path);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("File not found: " + path);
        }
        return new FileSystemResource(filePath);
    }

    @Override
    public void delete(String path) {
        Path filePath = resolvePath(path);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + path, e);
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(resolvePath(path));
    }

    @Override
    public String generatePresignedUrl(String path, Duration expiry) {
        // 返回 file:// URL，测试环境可以直接访问
        Path absolutePath = resolvePath(path).toAbsolutePath();
        return String.format("file://%s?expiry=%ds", absolutePath, expiry.getSeconds());
    }

    @Override
    public UploadSession beginUpload(String path, String contentType) {
        String sessionId = "fs-session-" + sessionCounter.incrementAndGet();
        Path filePath = resolvePath(path);
        FileSystemUploadSession session = new FileSystemUploadSession(sessionId, path, filePath);
        sessions.put(sessionId, session);
        return session;
    }
    
    /**
     * 清理所有数据（测试用）
     */
    public void clear() {
        sessions.clear();
        deleteRecursively(rootDir);
    }
    
    /**
     * 获取根目录路径（测试用）
     */
    public Path getRootDir() {
        return rootDir;
    }
    
    // ==================== 辅助方法 ====================
    
    private Path resolvePath(String path) {
        // 移除开头的 / 并解析相对路径
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return rootDir.resolve(normalized);
    }
    
    /**
     * 计算简单的 ETag（使用文件内容 hashCode）
     */
    private String calculateETag(Path filePath) throws IOException {
        byte[] content = Files.readAllBytes(filePath);
        int hash = Arrays.hashCode(content);
        return String.format("\"%08x\"", hash);
    }
    
    private void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                Files.list(path).forEach(this::deleteRecursively);
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // 忽略清理错误
        }
    }
    
    // ==================== 内部类：文件系统分片上传会话 ====================
    
    /**
     * 文件系统分片上传会话
     * <p>
     * 采用 RandomAccessFile 随机写入策略，类似 NFS/NAS 适配器的实现方式。
     * 特点：
     * <ul>
     *   <li>延迟初始化：第一次 uploadPart 才创建文件</li>
     *   <li>随机写入：分片可乱序上传</li>
     *   <li>动态扩展：无需预知文件总大小</li>
     * </ul>
     */
    private class FileSystemUploadSession implements UploadSession {
        private final String sessionId;
        private final String path;
        private final Path filePath;
        
        /** 随机访问文件句柄（延迟初始化） */
        private RandomAccessFile raf;
        
        /** 已上传的分片记录：partNumber -> etag */
        private final Map<Integer, String> uploadedParts = new ConcurrentHashMap<>();
        
        private volatile boolean closed = false;
        
        public FileSystemUploadSession(String sessionId, String path, Path filePath) {
            this.sessionId = sessionId;
            this.path = path;
            this.filePath = filePath;
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
                // 延迟初始化：第一次上传时才创建文件
                initializeFileIfNeeded();
                
                // 计算写入偏移量（基于固定分片大小）
                long offset = (partNumber - 1L) * DEFAULT_PART_SIZE;
                
                // 读取分片数据
                byte[] buffer = new byte[(int) size];
                int totalRead = 0;
                while (totalRead < size) {
                    int read = data.read(buffer, totalRead, (int) size - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                
                if (totalRead != size) {
                    throw new IllegalArgumentException(
                        String.format("Part size mismatch: expected %d, got %d", size, totalRead));
                }
                
                // 确保文件大小足够（动态扩展）
                long requiredSize = offset + size;
                synchronized (raf) {
                    if (raf.length() < requiredSize) {
                        raf.setLength(requiredSize);
                    }
                    
                    // 随机写入指定位置
                    raf.seek(offset);
                    raf.write(buffer, 0, totalRead);
                }
                
                // 计算分片 ETag
                String etag = String.format("\"%08x\"", Arrays.hashCode(buffer));
                uploadedParts.put(partNumber, etag);
                
                return etag;
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload part " + partNumber, e);
            }
        }
        
        @Override
        public String complete(List<PartETagInfo> partInfos) {
            ensureNotClosed();
            
            // 验证所有分片都已上传
            for (PartETagInfo info : partInfos) {
                if (!uploadedParts.containsKey(info.partNumber())) {
                    throw new IllegalStateException(
                        "Part " + info.partNumber() + " not uploaded");
                }
            }
            
            try {
                // 关闭文件句柄
                if (raf != null) {
                    raf.close();
                }
                
                // 清理会话
                sessions.remove(sessionId);
                closed = true;
                
                return path;
            } catch (IOException e) {
                throw new RuntimeException("Failed to complete upload", e);
            }
        }
        
        @Override
        public void abort() {
            ensureNotClosed();
            
            try {
                // 关闭文件句柄
                if (raf != null) {
                    raf.close();
                }
                
                // 删除未完成的文件
                Files.deleteIfExists(filePath);
                
                // 清理会话
                uploadedParts.clear();
                sessions.remove(sessionId);
                closed = true;
            } catch (IOException e) {
                throw new RuntimeException("Failed to abort upload", e);
            }
        }
        
        @Override
        public void close() {
            if (!closed) {
                abort();
            }
        }
        
        private void initializeFileIfNeeded() throws IOException {
            if (raf == null) {
                synchronized (this) {
                    if (raf == null) {
                        Files.createDirectories(filePath.getParent());
                        raf = new RandomAccessFile(filePath.toFile(), "rw");
                    }
                }
            }
        }
        
        private void ensureNotClosed() {
            if (closed) {
                throw new IllegalStateException("Session " + sessionId + " is closed");
            }
        }
    }
}
