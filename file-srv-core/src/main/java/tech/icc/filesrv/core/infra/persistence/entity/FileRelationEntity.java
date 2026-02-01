package tech.icc.filesrv.core.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * File relation JPA entity - tracks relationships between files.
 * <p>
 * This table implements a flexible relationship model supporting:
 * <ul>
 *   <li>SOURCE: Direct source file (the parent file when this file was generated)</li>
 *   <li>CURRENT_MAIN: Current main file (follows main file switches)</li>
 *   <li>DERIVED: Derived files (if this is a main file)</li>
 * </ul>
 * </p>
 * <p>
 * The composite primary key ensures unique relationships between files.
 * Additional indexes support efficient lookups for related files and orphan detection.
 * </p>
 */
@Entity
@Table(name = "file_relations", indexes = {
        @Index(name = "idx_related", columnList = "related_fkey, relation_type"),
        @Index(name = "idx_created", columnList = "created_at")
})
@IdClass(FileRelationEntity.FileRelationId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileRelationEntity {

    /**
     * File key (owner of this relationship).
     */
    @Id
    @Column(name = "file_fkey", length = 64, nullable = false)
    private String fileFkey;

    /**
     * Related file key.
     */
    @Id
    @Column(name = "related_fkey", length = 64, nullable = false)
    private String relatedFkey;

    /**
     * Relationship type.
     */
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", length = 16, nullable = false)
    private RelationType relationType;

    /**
     * Creation timestamp.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Last update timestamp.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Relationship types.
     */
    public enum RelationType {
        /**
         * Source file relationship (the parent file when this file was generated).
         * This is immutable and records the historical origin.
         */
        SOURCE,

        /**
         * Current main file relationship (follows main file switches).
         * This can be updated when the main file changes.
         */
        CURRENT_MAIN,

        /**
         * Derived file relationship (files derived from this file).
         * Maintained by the main file.
         */
        DERIVED
    }

    /**
     * Composite primary key for file relations.
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class FileRelationId implements Serializable {
        private String fileFkey;
        private String relatedFkey;
        private RelationType relationType;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FileRelationId that)) return false;
            return fileFkey.equals(that.fileFkey) &&
                   relatedFkey.equals(that.relatedFkey) &&
                   relationType == that.relationType;
        }

        @Override
        public int hashCode() {
            int result = fileFkey.hashCode();
            result = 31 * result + relatedFkey.hashCode();
            result = 31 * result + relationType.hashCode();
            return result;
        }
    }
}
