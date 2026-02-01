package tech.icc.filesrv.core.infra.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tech.icc.filesrv.core.infra.persistence.entity.FileRelationEntity;
import tech.icc.filesrv.core.infra.persistence.entity.FileRelationEntity.FileRelationId;
import tech.icc.filesrv.core.infra.persistence.entity.FileRelationEntity.RelationType;

import java.time.Instant;
import java.util.List;

/**
 * File relation JPA repository - manages file relationship data.
 * <p>
 * This repository provides operations for managing file relationships including:
 * <ul>
 *   <li>Query derived files of a main file</li>
 *   <li>Query current main file of a derived file</li>
 *   <li>Query source file of a derived file</li>
 *   <li>Find orphan files (files with non-existent related files)</li>
 * </ul>
 * </p>
 */
public interface FileRelationRepository extends JpaRepository<FileRelationEntity, FileRelationId> {

    /**
     * Find all derived files of a main file.
     *
     * @param fileFkey     the main file key
     * @param relationType must be {@link RelationType#DERIVED}
     * @return list of related file keys
     */
    @Query("SELECT r.relatedFkey FROM FileRelationEntity r " +
           "WHERE r.fileFkey = :fileFkey AND r.relationType = :relationType")
    List<String> findRelatedFkeys(@Param("fileFkey") String fileFkey,
                                   @Param("relationType") RelationType relationType);

    /**
     * Find the current main file of a derived file.
     *
     * @param fileFkey the derived file key
     * @return the current main file key, or null if not found
     */
    @Query("SELECT r.relatedFkey FROM FileRelationEntity r " +
           "WHERE r.fileFkey = :fileFkey AND r.relationType = 'CURRENT_MAIN'")
    String findCurrentMainFkey(@Param("fileFkey") String fileFkey);

    /**
     * Find the source file of a derived file.
     *
     * @param fileFkey the derived file key
     * @return the source file key, or null if not found
     */
    @Query("SELECT r.relatedFkey FROM FileRelationEntity r " +
           "WHERE r.fileFkey = :fileFkey AND r.relationType = 'SOURCE'")
    String findSourceFkey(@Param("fileFkey") String fileFkey);

    /**
     * Find orphan files (files with non-existent related files).
     * <p>
     * An orphan file is defined as:
     * <ul>
     *   <li>Its related file (sourceKey or currentMainKey) does not exist in file_metadata</li>
     *   <li>The relationship was created before the grace period</li>
     * </ul>
     * </p>
     *
     * @param gracePeriodStart files created before this time are considered orphans
     * @return list of orphan file keys
     */
    @Query("SELECT DISTINCT r.fileFkey FROM FileRelationEntity r " +
           "LEFT JOIN FileReferenceEntity f ON r.relatedFkey = f.fKey " +
           "WHERE f.fKey IS NULL AND r.createdAt < :gracePeriodStart")
    List<String> findOrphanFiles(@Param("gracePeriodStart") Instant gracePeriodStart);

    /**
     * Delete all relations for a given file key.
     * <p>
     * This is used when deleting a file to clean up its relationships.
     * </p>
     *
     * @param fileFkey the file key
     */
    void deleteByFileFkey(String fileFkey);

    /**
     * Delete all relations where the related file is the given key.
     * <p>
     * This is used when a main file is deleted to clean up references from derived files.
     * </p>
     *
     * @param relatedFkey the related file key
     */
    void deleteByRelatedFkey(String relatedFkey);
}
