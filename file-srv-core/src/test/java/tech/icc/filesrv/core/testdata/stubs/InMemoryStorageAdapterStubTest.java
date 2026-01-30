package tech.icc.filesrv.core.testdata.stubs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * InMemoryStorageAdapterStub 测试
 */
@DisplayName("InMemoryStorageAdapterStub 测试")
class InMemoryStorageAdapterStubTest {

    private InMemoryStorageAdapterStub storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorageAdapterStub();
    }

    @Test
    @DisplayName("应成功上传和下载文件")
    void shouldUploadAndDownloadFile() throws IOException {
        // Given
        String path = "test/file.txt";
        String content = "Hello, World!";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // When
        var result = storage.upload(path, inputStream, "text/plain");

        // Then
        assertThat(result.path()).isEqualTo(path);
        assertThat(result.size()).isEqualTo(content.length());
        assertThat(storage.exists(path)).isTrue();

        // 下载验证
        var resource = storage.download(path);
        String downloaded = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(downloaded).isEqualTo(content);
    }

    @Test
    @DisplayName("应成功删除文件")
    void shouldDeleteFile() {
        // Given
        String path = "test/file.txt";
        storage.upload(path, new ByteArrayInputStream("content".getBytes()), "text/plain");
        assertThat(storage.exists(path)).isTrue();

        // When
        storage.delete(path);

        // Then
        assertThat(storage.exists(path)).isFalse();
    }

    @Test
    @DisplayName("下载不存在的文件应抛出异常")
    void shouldThrowExceptionWhenDownloadingNonExistentFile() {
        assertThatThrownBy(() -> storage.download("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    @DisplayName("应生成预签名 URL")
    void shouldGeneratePresignedUrl() {
        // Given
        String path = "test/file.txt";
        storage.upload(path, new ByteArrayInputStream("content".getBytes()), "text/plain");

        // When
        String url = storage.generatePresignedUrl(path, java.time.Duration.ofMinutes(5));

        // Then
        assertThat(url).contains(path);
        assertThat(url).contains("expires=300");
    }

    @Test
    @DisplayName("测试辅助方法应正常工作")
    void shouldWorkWithHelperMethods() {
        // Given
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        storage.upload("file1.txt", new ByteArrayInputStream(content), "text/plain");
        storage.upload("file2.txt", new ByteArrayInputStream("another".getBytes()), "text/plain");

        // Then
        assertThat(storage.getFileCount()).isEqualTo(2);
        assertThat(storage.verifyFileContent("file1.txt", content)).isTrue();

        // Clear
        storage.clear();
        assertThat(storage.getFileCount()).isEqualTo(0);
    }
}
