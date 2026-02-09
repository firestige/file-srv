package tech.icc.filesrv.common.context;

import lombok.Setter;

import java.util.Optional;

/**
 * 执行信息上下文
 * <p>
 * 存储任务执行期间的只读信息（taskId, fileHash, localPath等）
 */
@Setter
public class ExecutionInfoContext {
    
    private String taskId;
    private String storagePath;
    private String localFilePath;
    private String fileHash;
    private String contentType;
    private Long fileSize;
    private String filename;
    private String ownerId;
    private String ownerName;

    public ExecutionInfoContext() {
    }

    // ==================== Getters ====================

    public Optional<String> getTaskId() {
        return Optional.ofNullable(taskId);
    }

    public Optional<String> getStoragePath() {
        return Optional.ofNullable(storagePath);
    }

    public Optional<String> getLocalFilePath() {
        return Optional.ofNullable(localFilePath);
    }

    public Optional<String> getFileHash() {
        return Optional.ofNullable(fileHash);
    }

    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    public Optional<Long> getFileSize() {
        return Optional.ofNullable(fileSize);
    }

    public Optional<String> getFilename() {
        return Optional.ofNullable(filename);
    }

    public Optional<String> getOwnerId() {
        return Optional.ofNullable(ownerId);
    }

    public Optional<String> getOwnerName() {
        return Optional.ofNullable(ownerName);
    }

    @Override
    public String toString() {
        return "ExecutionInfoContext{" +
                "taskId='" + taskId + '\'' +
                ", fileHash='" + fileHash + '\'' +
                ", filename='" + filename + '\'' +
                ", ownerId='" + ownerId + '\'' +
                '}';
    }
}
