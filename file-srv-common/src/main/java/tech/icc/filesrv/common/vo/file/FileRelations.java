package tech.icc.filesrv.common.vo.file;

import lombok.Builder;

import java.util.List;

/**
 * File relation information - tracks source, current main file, and derived files.
 * <p>
 * This value object implements a dual-reference design to prevent orphan files:
 * <ul>
 *   <li><strong>sourceKey</strong>: The original parent file when this file was generated (immutable)</li>
 *   <li><strong>currentMainKey</strong>: The current main file (follows switches)</li>
 *   <li><strong>derivedKeys</strong>: List of derived files (if this is a main file)</li>
 * </ul>
 * </p>
 *
 * <p><strong>Example Scenario:</strong></p>
 * <pre>
 * Initial State:
 *   Original Image (fkey1) → Generate Thumbnail (fkey2)
 *   fkey2.relations: sourceKey=fkey1, currentMainKey=fkey1
 *
 * Main File Switch:
 *   Plugin sets thumbnail (fkey2) as main file
 *   fkey2.relations: sourceKey=fkey1 (unchanged), currentMainKey=fkey2 (updated)
 *   fkey1.relations: derivedKeys=[fkey2] (updated)
 * </pre>
 *
 * @param sourceKey       the direct source file key (immutable, records generation parent)
 * @param currentMainKey  the current main file key (mutable, follows main file switches)
 * @param derivedKeys     list of derived file keys (if this is a main file)
 */
@Builder
public record FileRelations(
        String sourceKey,
        String currentMainKey,
        List<String> derivedKeys
) {
    /**
     * Create an empty FileRelations instance (no relationships).
     *
     * @return empty FileRelations with all fields null
     */
    public static FileRelations empty() {
        return new FileRelations(null, null, null);
    }

    /**
     * Create a FileRelations instance from a source file key.
     * Initially, the source and current main file are the same.
     *
     * @param sourceKey the source file key
     * @return FileRelations with sourceKey and currentMainKey set to the same value
     */
    public static FileRelations fromSource(String sourceKey) {
        return new FileRelations(sourceKey, sourceKey, null);
    }

    /**
     * Check if this file has any relationships.
     *
     * @return true if at least one field is non-null
     */
    public boolean hasRelations() {
        return sourceKey != null || currentMainKey != null || 
               (derivedKeys != null && !derivedKeys.isEmpty());
    }

    /**
     * Check if this file is a derived file (has a source).
     *
     * @return true if sourceKey is not null
     */
    public boolean isDerived() {
        return sourceKey != null;
    }

    /**
     * Check if this file is a main file (has derived files).
     *
     * @return true if derivedKeys is not null and not empty
     */
    public boolean isMainFile() {
        return derivedKeys != null && !derivedKeys.isEmpty();
    }
}
