package tech.icc.filesrv.common.vo.file;

import lombok.Builder;

/**
 * Storage reference - abstract storage location.
 *
 * @param storageType storage type, e.g., "HCS", "S3", "LOCAL"
 * @param location    storage location/bucket name
 * @param path        object path within the storage
 * @param eTag        file ETag/checksum for integrity verification
 * @param fileUrl     public access URL if available
 */
@Builder
public record StorageRef(
        String storageType,
        String location,
        String path,
        String eTag,
        String fileUrl
) {}
