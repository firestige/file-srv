package tech.icc.filesrv.common.context;

import tech.icc.filesrv.common.vo.file.FileMetadataUpdate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 文件元数据上下文
 * <p>
 * 管理主文件和衍生文件的元数据更新
 */
public class FileMetadataContext {
    
    /** 主文件的元数据更新 */
    private FileMetadataUpdate primaryFileMetadata;
    
    /** 衍生文件的元数据更新（key = fKey） */
    private final Map<String, FileMetadataUpdate> derivedFileMetadataMap;

    public FileMetadataContext() {
        this.derivedFileMetadataMap = new HashMap<>();
    }

    // ==================== 主文件元数据 ====================

    /**
     * 更新主文件元数据
     */
    public void updatePrimaryMetadata(Consumer<FileMetadataUpdate.FileMetadataUpdateBuilder> updater) {
        FileMetadataUpdate.FileMetadataUpdateBuilder builder = getPrimaryMetadataBuilder();
        updater.accept(builder);
        this.primaryFileMetadata = builder.build();
    }

    /**
     * 获取主文件元数据更新
     */
    public Optional<FileMetadataUpdate> getPrimaryMetadata() {
        return Optional.ofNullable(primaryFileMetadata);
    }

    /**
     * 是否有主文件元数据更新
     */
    public boolean hasPrimaryMetadataUpdates() {
        return primaryFileMetadata != null && primaryFileMetadata.hasUpdates();
    }

    // ==================== 衍生文件元数据 ====================

    /**
     * 更新衍生文件元数据
     */
    public void updateDerivedFileMetadata(String fKey, 
            Consumer<FileMetadataUpdate.FileMetadataUpdateBuilder> updater) {
        FileMetadataUpdate.FileMetadataUpdateBuilder builder = getDerivedFileMetadataBuilder(fKey);
        updater.accept(builder);
        derivedFileMetadataMap.put(fKey, builder.build());
    }

    /**
     * 获取衍生文件元数据更新
     */
    public Optional<FileMetadataUpdate> getDerivedFileMetadata(String fKey) {
        return Optional.ofNullable(derivedFileMetadataMap.get(fKey));
    }

    /**
     * 获取所有衍生文件元数据更新
     */
    public Map<String, FileMetadataUpdate> getAllDerivedFileMetadata() {
        return Map.copyOf(derivedFileMetadataMap);
    }

    /**
     * 是否有衍生文件元数据更新
     */
    public boolean hasDerivedFileMetadataUpdates() {
        return !derivedFileMetadataMap.isEmpty();
    }

    // ==================== 内部方法 ====================

    private FileMetadataUpdate.FileMetadataUpdateBuilder getPrimaryMetadataBuilder() {
        return Optional.ofNullable(primaryFileMetadata)
                .map(FileMetadataUpdate::toBuilder)
                .orElseGet(FileMetadataUpdate::builder);
    }

    private FileMetadataUpdate.FileMetadataUpdateBuilder getDerivedFileMetadataBuilder(String fKey) {
        return Optional.ofNullable(derivedFileMetadataMap.get(fKey))
                .map(FileMetadataUpdate::toBuilder)
                .orElseGet(FileMetadataUpdate::builder);
    }

    @Override
    public String toString() {
        return "FileMetadataContext{" +
                "hasPrimaryUpdate=" + hasPrimaryMetadataUpdates() +
                ", derivedFileCount=" + derivedFileMetadataMap.size() +
                '}';
    }
}
