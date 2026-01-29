package tech.icc.filesrv.common.vo.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * File request - original request submitted when creating task.
 *
 * @param filename    intended file name
 * @param contentType expected MIME type
 * @param size        expected file size in bytes
 * @param eTag        expected eTag/checksum for validation
 * @param location    target storage location
 */
@Builder
public record FileRequest(
        String filename,
        String contentType,
        Long size,
        @JsonProperty("eTag") String eTag,
        String location
) {}
