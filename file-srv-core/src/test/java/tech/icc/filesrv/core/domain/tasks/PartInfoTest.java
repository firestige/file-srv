package tech.icc.filesrv.core.domain.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PartInfo 单元测试
 * <p>
 * 测试分片信息值对象的验证规则
 */
@DisplayName("PartInfo 单元测试")
class PartInfoTest {

    @Test
    @DisplayName("应成功创建有效的分片信息")
    void shouldCreateValidPartInfo() {
        // When
        PartInfo part = PartInfo.of(1, "etag-123", 1024);

        // Then
        assertEquals(1, part.partNumber());
        assertEquals("etag-123", part.etag());
        assertEquals(1024, part.size());
    }

    @Test
    @DisplayName("分片序号小于 1 应抛出异常")
    void shouldRejectInvalidPartNumber() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                PartInfo.of(0, "etag-123", 1024)
        );
        assertThrows(IllegalArgumentException.class, () ->
                PartInfo.of(-1, "etag-123", 1024)
        );
    }

    @Test
    @DisplayName("空白 ETag 应抛出异常")
    void shouldRejectBlankETag() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                PartInfo.of(1, "", 1024)
        );
        assertThrows(IllegalArgumentException.class, () ->
                PartInfo.of(1, "   ", 1024)
        );
        assertThrows(IllegalArgumentException.class, () ->
                PartInfo.of(1, null, 1024)
        );
    }

    @Test
    @DisplayName("负数大小应抛出异常")
    void shouldRejectNegativeSize() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                PartInfo.of(1, "etag-123", -1)
        );
    }

    @Test
    @DisplayName("大小为 0 应被接受")
    void shouldAcceptZeroSize() {
        // When
        PartInfo part = PartInfo.of(1, "etag-123", 0);

        // Then
        assertEquals(0, part.size());
    }
}
