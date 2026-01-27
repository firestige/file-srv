package tech.icc.filesrv.core.application.service;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.icc.filesrv.core.application.service.dto.FileInfoDto;
import tech.icc.filesrv.core.application.service.dto.MetaQueryCriteria;

import java.time.Duration;
import java.util.Optional;

/**
 * 文件应用服务
 * <p>
 * 编排领域对象完成文件相关用例，使用应用层 DTO 进行数据传输。
 */
@Service
public class FileService {

    /**
     * 上传文件
     *
     * @param fileInfo 文件信息
     * @param file     上传的文件
     * @return 保存后的文件信息
     */
    public FileInfoDto upload(FileInfoDto fileInfo, MultipartFile file) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * 下载文件
     *
     * @param fileKey 文件唯一标识
     * @return 文件资源
     */
    public Resource download(String fileKey) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * 根据文件标识获取文件信息
     *
     * @param fileKey 文件唯一标识
     * @return 文件信息，不存在时返回 empty
     */
    public Optional<FileInfoDto> getFileInfo(String fileKey) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * 删除文件
     *
     * @param fileKey 文件唯一标识
     */
    public void delete(String fileKey) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * 获取预签名 URL
     *
     * @param fileKey 文件唯一标识
     * @param expire  有效期
     * @return 预签名 URL
     */
    public String getPresignedUrl(String fileKey, Duration expire) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * 查询文件元数据
     *
     * @param criteria 查询条件
     * @param pageable 分页参数
     * @return 分页的文件信息
     */
    public Page<FileInfoDto> queryMetadata(MetaQueryCriteria criteria, Pageable pageable) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
