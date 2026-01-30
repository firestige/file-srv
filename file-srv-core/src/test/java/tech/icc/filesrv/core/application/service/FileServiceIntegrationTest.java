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
import tech.icc.filesrv.common.spi.storage.StorageResult;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.core.TestApplication;
import tech.icc.filesrv.core.application.service.dto.FileInfoDto;
import tech.icc.filesrv.core.domain.files.FileReferenceRepository;
import tech.icc.filesrv.core.domain.services.DeduplicationService;
import tech.icc.filesrv.core.domain.services.StorageRoutingService;
import tech.icc.filesrv.core.domain.storage.StorageNode;
import tech.icc.filesrv.core.domain.storage.StoragePolicy;

import java.io.IOException;
import java.io.InputStream;

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
@SpringBootTest(classes = TestApplication.class)
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

    @MockBean
    private StorageRoutingService storageRoutingService;

    @BeforeEach
    void setUp() {
        // Mock 去重服务：默认不存在相同内容（不秒传）
        when(deduplicationService.findByContentHash(anyString()))
                .thenReturn(java.util.Optional.empty());
        when(deduplicationService.computeHash(any(byte[].class)))
                .thenReturn("mock-content-hash-" + System.currentTimeMillis());

        // Mock 存储路由服务
        StorageNode mockNode = StorageNode.create("test-node", "Test Node", "local");
        when(storageRoutingService.selectNode(any(StoragePolicy.class)))
                .thenReturn(mockNode);
        when(storageRoutingService.getAdapter(anyString()))
                .thenReturn(storageAdapter);
        when(storageRoutingService.buildStoragePath(anyString(), anyString()))
                .thenReturn("storage/mock-path");
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

        OwnerInfo owner = OwnerInfo.builder()
                .createdBy("user123")
                .creatorName("测试用户")
                .build();
        AccessControl accessControl = AccessControl.privateAccess();

        // 构建 FileInfoDto
        FileInfoDto fileInfoDto = FileInfoDto.builder()
                .owner(owner)
                .access(accessControl)
                .build();

        // Mock 存储适配器返回成功结果
        StorageResult uploadResult = StorageResult.of(
                "storage/test-key-123",
                "mock-etag-abc",
                (long) content.length
        );
        when(storageAdapter.upload(anyString(), any(InputStream.class), anyString()))
                .thenReturn(uploadResult);

        // When: 执行上传
        FileInfoDto result = fileService.upload(fileInfoDto, file);

        // Then: 验证结果
        assertThat(result).isNotNull();
        assertThat(result.identity()).isNotNull();
        assertThat(result.identity().fileName()).isEqualTo("test.txt");
        assertThat(result.identity().fileSize()).isEqualTo(content.length);
        assertThat(result.owner().createdBy()).isEqualTo("user123");
        assertThat(result.identity().fKey()).isNotBlank();

        // 验证数据库中存在该记录
        assertThat(fileReferenceRepository.findByFKey(result.identity().fKey())).isPresent();
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

        OwnerInfo owner = OwnerInfo.builder()
                .createdBy("user123")
                .creatorName("测试用户")
                .build();
        AccessControl accessControl = AccessControl.privateAccess();

        FileInfoDto fileInfoDto = FileInfoDto.builder()
                .owner(owner)
                .access(accessControl)
                .build();

        // When & Then: 执行上传，期望抛出异常
        assertThatThrownBy(() -> fileService.upload(fileInfoDto, largeFile))
                .hasMessageContaining("File size exceeds limit");
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

        OwnerInfo owner = OwnerInfo.builder()
                .createdBy("user456")
                .creatorName("查询测试用户")
                .build();
        AccessControl accessControl = AccessControl.publicAccess();

        FileInfoDto fileInfoDto = FileInfoDto.builder()
                .owner(owner)
                .access(accessControl)
                .build();

        StorageResult uploadResult = StorageResult.of(
                "storage/query-test-key",
                "query-etag-xyz",
                (long) content.length
        );
        when(storageAdapter.upload(anyString(), any(InputStream.class), anyString()))
                .thenReturn(uploadResult);

        FileInfoDto uploaded = fileService.upload(fileInfoDto, file);
        String fkey = uploaded.identity().fKey();

        // When: 查询文件信息
        java.util.Optional<FileInfoDto> retrieved = fileService.getFileInfo(fkey);

        // Then: 验证查询结果
        assertThat(retrieved).isPresent();
        FileInfoDto result = retrieved.get();
        assertThat(result.identity().fKey()).isEqualTo(fkey);
        assertThat(result.identity().fileName()).isEqualTo("query-test.txt");
        assertThat(result.identity().fileSize()).isEqualTo(content.length);
        assertThat(result.owner().createdBy()).isEqualTo("user456");
        assertThat(result.access()).isNotNull();
        assertThat(result.access().isPublic()).isTrue();
    }

    @Test
    @DisplayName("查询不存在的文件应该返回 empty")
    void shouldReturnEmptyForNonExistentFile() {
        // When: 查询一个不存在的 fkey
        java.util.Optional<FileInfoDto> result = fileService.getFileInfo("non-existent-fkey-12345");

        // Then: 应该返回 empty
        assertThat(result).isEmpty();
    }
}
