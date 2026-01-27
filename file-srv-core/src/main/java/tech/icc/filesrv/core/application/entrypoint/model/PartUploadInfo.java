package tech.icc.filesrv.core.application.entrypoint.model;

public record PartUploadInfo(
        String eTag,
        Integer partNumber
) {}
