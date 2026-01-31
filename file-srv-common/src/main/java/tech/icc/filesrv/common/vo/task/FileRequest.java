package tech.icc.filesrv.common.vo.task;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.CustomMetadata;
import tech.icc.filesrv.common.vo.file.FileTags;

/**
 * File request - original request submitted when creating task.
 *
 * @param filename    intended file name
 * @param contentType expected MIME type
 * @param size        expected file size in bytes
 * @param eTag        expected eTag/checksum for validation
 * @param location    target storage location
 * @param access      access control
 * @param fileTags    file tags for categorization
 * @param metadata    custom metadata
 */
@Builder
public record FileRequest(
        String filename,
        String contentType,
        Long size,
        String contentHash,
        String eTag,
        String location,
        @JsonUnwrapped OwnerInfo owner,
        @JsonUnwrapped AccessControl access,
        @JsonUnwrapped FileTags fileTags,
        @JsonUnwrapped CustomMetadata metadata
) {}
