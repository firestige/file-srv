package tech.icc.filesrv.core.infra.persistence.repository.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import tech.icc.filesrv.core.domain.files.FileReference;
import tech.icc.filesrv.core.domain.files.FileReferenceRepository;
import tech.icc.filesrv.core.domain.files.FileReferenceSpec;
import tech.icc.filesrv.core.infra.persistence.entity.FileReferenceEntity;
import tech.icc.filesrv.core.infra.persistence.repository.FileReferenceJpaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文件引用仓储实现
 */
@Repository
@RequiredArgsConstructor
public class FileReferenceRepositoryImpl implements FileReferenceRepository {

    private final FileReferenceJpaRepository jpaRepository;

    @Override
    public FileReference save(FileReference reference) {
        FileReferenceEntity entity = FileReferenceEntity.fromDomain(reference);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<FileReference> findByFKey(String fKey) {
        return jpaRepository.findById(fKey).map(FileReferenceEntity::toDomain);
    }

    @Override
    public List<FileReference> findByOwner(String ownerId) {
        return jpaRepository.findByOwnerId(ownerId).stream()
                .map(FileReferenceEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByFKey(String fKey) {
        jpaRepository.deleteById(fKey);
    }

    @Override
    public boolean existsByFKey(String fKey) {
        return jpaRepository.existsById(fKey);
    }

    @Override
    public Page<FileReference> findAll(FileReferenceSpec spec, Pageable pageable) {
        Specification<FileReferenceEntity> jpaSpec = toJpaSpec(spec);
        return jpaRepository.findAll(jpaSpec, pageable)
                .map(FileReferenceEntity::toDomain);
    }

    /**
     * 将领域规约转换为 JPA Specification
     */
    private Specification<FileReferenceEntity> toJpaSpec(FileReferenceSpec spec) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (spec.ownerId() != null && !spec.ownerId().isBlank()) {
                predicates.add(cb.equal(root.get("ownerId"), spec.ownerId()));
            }

            if (spec.filenamePattern() != null && !spec.filenamePattern().isBlank()) {
                String pattern = spec.filenamePattern()
                        .replace("*", "%")
                        .replace("?", "_");
                predicates.add(cb.like(root.get("filename"), pattern));
            }

            if (spec.contentType() != null && !spec.contentType().isBlank()) {
                if (spec.contentType().endsWith("/*")) {
                    String prefix = spec.contentType().substring(0, spec.contentType().length() - 1);
                    predicates.add(cb.like(root.get("contentType"), prefix + "%"));
                } else {
                    predicates.add(cb.equal(root.get("contentType"), spec.contentType()));
                }
            }

            if (spec.startTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), spec.startTime()));
            }

            if (spec.endTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), spec.endTime()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
