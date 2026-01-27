package tech.icc.filesrv.core.infra.persistence.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.icc.filesrv.core.domain.files.FileInfo;
import tech.icc.filesrv.core.domain.files.FileInfoRepository;
import tech.icc.filesrv.core.domain.files.FileStatus;
import tech.icc.filesrv.core.infra.persistence.entity.FileInfoEntity;
import tech.icc.filesrv.core.infra.persistence.repository.FileInfoJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 物理文件信息仓储实现
 */
@Repository
@RequiredArgsConstructor
public class FileInfoRepositoryImpl implements FileInfoRepository {

    private final FileInfoJpaRepository jpaRepository;

    @Override
    public FileInfo save(FileInfo fileInfo) {
        FileInfoEntity entity = FileInfoEntity.fromDomain(fileInfo);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<FileInfo> findByContentHash(String contentHash) {
        return jpaRepository.findById(contentHash).map(FileInfoEntity::toDomain);
    }

    @Override
    public boolean existsByContentHash(String contentHash) {
        return jpaRepository.existsById(contentHash);
    }

    @Override
    @Transactional
    public int incrementRefCount(String contentHash) {
        return jpaRepository.incrementRefCount(contentHash);
    }

    @Override
    @Transactional
    public int decrementRefCount(String contentHash) {
        return jpaRepository.decrementRefCount(contentHash);
    }

    @Override
    public List<FileInfo> findGarbageFiles(int limit) {
        return jpaRepository.findGarbageFiles(FileStatus.DELETED, PageRequest.of(0, limit))
                .stream()
                .map(FileInfoEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByContentHash(String contentHash) {
        jpaRepository.deleteById(contentHash);
    }
}
