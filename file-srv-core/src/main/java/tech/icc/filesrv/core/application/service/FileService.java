package tech.icc.filesrv.core.application.service;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.icc.filesrv.core.application.entrypoint.model.FileInfo;
import tech.icc.filesrv.core.application.entrypoint.model.MetaQueryParams;

import java.time.Duration;

@Service
public class FileService {
    public FileInfo upload(FileInfo fileInfo, MultipartFile file) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Resource download(String fileKey) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public FileInfo getFileInfo(String fileKey) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void delete(String fileKey) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String getPresignUrl(String fileKey, Duration expire) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Page<FileInfo> queryMetadata(MetaQueryParams queryParams, Pageable pageable) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
