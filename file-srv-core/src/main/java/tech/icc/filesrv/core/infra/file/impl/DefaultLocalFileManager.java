package tech.icc.filesrv.core.infra.file.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tech.icc.filesrv.core.infra.file.LocalFileManager;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认本地文件管理器实现
 */
@Service
public class DefaultLocalFileManager implements LocalFileManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultLocalFileManager.class);

    private final Path tempBaseDir;
    private final StorageAdapter storageAdapter;
    private final Map<String, Path> cachedFiles = new ConcurrentHashMap<>();

    // todo 这里的 Path 是参数，我们要在 config 中追加
    public DefaultLocalFileManager(@Qualifier("tempBaseDir") Path tempBaseDir, StorageAdapter storageAdapter) {
        this.tempBaseDir = tempBaseDir;
        this.storageAdapter = storageAdapter;
        
        // 确保临时目录存在
        try {
            Files.createDirectories(tempBaseDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory: " + tempBaseDir, e);
        }
    }

    @Override
    public Path prepareLocalFile(String storagePath, String taskId) {
        // 检查是否有缓存
        Path cached = cachedFiles.get(taskId);
        if (cached != null && Files.exists(cached)) {
            log.debug("Using cached file: taskId={}, path={}", taskId, cached);
            return cached;
        }

        // 从存储层下载
        Path taskDir = getTempDirectory(taskId);
        Path localFile = taskDir.resolve("source");

        try {
            Resource resource = storageAdapter.download(storagePath);
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Downloaded file from storage: taskId={}, storagePath={}, localPath={}", 
                    taskId, storagePath, localFile);
            return localFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to download file: " + storagePath, e);
        }
    }

    @Override
    public void cleanup(String taskId) {
        cachedFiles.remove(taskId);
        
        Path taskDir = tempBaseDir.resolve(taskId);
        if (Files.exists(taskDir)) {
            try {
                deleteRecursively(taskDir);
                log.debug("Cleaned up task directory: {}", taskDir);
            } catch (IOException e) {
                log.warn("Failed to cleanup task directory: {}", taskDir, e);
            }
        }
    }

    @Override
    public Path getTempDirectory(String taskId) {
        Path taskDir = tempBaseDir.resolve(taskId);
        try {
            Files.createDirectories(taskDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create task temp directory: " + taskDir, e);
        }
        return taskDir;
    }

    @Override
    public void cacheUploadedFile(String taskId, Path localPath) {
        cachedFiles.put(taskId, localPath);
        log.debug("Cached uploaded file: taskId={}, path={}", taskId, localPath);
    }

    @Override
    public Path getCachedFile(String taskId) {
        return cachedFiles.get(taskId);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
