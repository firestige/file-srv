package tech.icc.filesrv.core.application.entrypoint.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class MetaQueryParams {
    private String fileName;
    private String creator;
    private String contentType;
    private LocalDateTime createdFrom;
    private LocalDateTime updatedTo;
    private Set<String> tags;
}
