package tech.icc.filesrv.core.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;
import tech.icc.filesrv.common.spi.storage.UploadResult;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.file.OwnerInfo;
import tech.icc.filesrv.core.application.dto.FileInfoDto;
import tech.icc.filesrv.core.domain.files.FileReferenceRepository;
import tech.icc.filesrv.core.domain.services.DeduplicationService;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * FileService 核心流程集成测试
 * <p>
 * 测试策略：
 * - 使用 @SpringBootTest 加载完整 Spring 上下文
 * - 使用 H2 内存数据库（不依赖 Docker/Testcontainers）
 * - Mock 外部依赖（StorageAdapter, DeduplicationService）
 * <p>
 * 测试重点：
 * 1. 文件上传核心流程
 * 2. 文件大小限制验证
 * 3. 文件查询功能
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FileService 核心流程测试")
class FileServiceIntegrationTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private FileReferenceRepository fileReferenceRepository;

    @MockBean
    private StorageAdapter storageAdapter;

    @MockBean
    private DeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        // Mock 去重服务：默认不去重
        when(deduplicationService.findExistingFile(anyString()))
                .thenReturn(null);
    }

    @Test
    @DisplayName("应该成功上传文件")
    void shouldUploadFileSuccessfully() throws IOException {
        // Given: 准备上传文件
        byte[] content = "test file content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content
        );

        OwnerInfo owner = new OwnerInfo("user123", "测试用户");
        AccessControl accessControl = AccessControl.privateAccess();

        // Mock 存储适配器返回成功结果
        UploadResult uploadResult = UploadResult.builder()
                .storageKey("storage/test-key-123")
                .etag("mock-etag-abc")
                .size((long) content.length)
                .build();
        when(storageAdapter.upload(any(), any(), anyLong()))
                .thenReturn(uploadResult);

        // When: 执行上传
        FileInfoDto result = fileService.upload(file, owner, accessControl, null);

        // Then: 验证结果
        assertThat(result).isNotNull();
        assertThat(result.getFileName()).isEqualTo("test.txt");
        assertThat(result.getSize()).isEqualTo(content.length);
        assertThat(result.getOwnerId()).isEqualTo("user123");
        assertThat(result.getFkey()).isNotBlank();

        // 验证数据库中存在该记录
        assertThat(fileReferenceRepository.findByFKey(result.getFkey())).isPresent();
    }

    @Test
    @DisplayName("应该拒绝超过大小限制的文件")
    void shouldRejectOversizedFile() {
        // Given: 准备一个超过 10MB 的文件（模拟）
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large.bin",
                "application/octet-stream",
                largeContent
        );

        OwnerInfo owner = new OwnerInfo("user123", "测试用户");
        AccessControl accessControl = AccessControl.privateAccess();

        // When & Then: 执行上传，期望抛出异常
        assertThatThrownBy(() -> fileService.upload(largeFile, owner, accessControl, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件大小超过限制");
    }

    @Test
    @DisplayName("应该能查询已上传的文件")
    void shouldRetrieveUploadedFile() throws IOException {
        // Given: 先上传一个文件
        byte[] content = "query test content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "query-test.txt",
                "text/plain",
                content
        );

        OwnerInfo owner = new OwnerInfo("user456", "查询测试用户");
        AccessControl accessControl = AccessControl.publicReadAccess();

        UploadResult uploadResult = UploadResult.builder()
                .storageKey("storage/query-test-key")
                .etag("query-etag-xyz")
                .size((long) content.length)
                .build();
        when(storageAdapter.upload(any(), any(), anyLong()))
                .thenReturn(uploadResult);

        FileInfoDto uploaded = fileService.upload(file, owner, accessControl, null);
        String fkey = uploaded.getFkey();

        // When: 查询文件信息
        FileInfoDto retrieved = fileService.getFileInfo(fkey);

        // Then: 验证查询结果
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getFkey()).isEqualTo(fkey);
        assertThat(retrieved.getFileName()).isEqualTo("query-test.txt");
        assertThat(retrieved.getSize()).isEqualTo(content.length);
        assertThat(retrieved.getOwnerId()).isEqualTo("user456");
        assertThat(retrieved.getAccessControl()).isNotNull();
        assertThat(retrieved.getAccessControl().getAccessLevel()).isEqualTo("public_read");
    }

    @Test
    @DisplayName("查询不存在的文件应该返回 null")
    void shouldReturnNullForNonExistentFile() {
        // When: 查询一个不存在的 fkey
        FileInfoDto result = fileService.getFileInfo("non-existent-fkey-12345");

        // Then: 应该返回 null
        assertThat(result).isNull();
    }
}
