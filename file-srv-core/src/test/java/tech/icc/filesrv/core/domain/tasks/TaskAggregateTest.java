package tech.icc.filesrv.core.domain.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.icc.filesrv.common.vo.task.CallbackConfig;
import tech.icc.filesrv.common.vo.task.TaskStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskAggregate 单元测试
 * <p>
 * 测试任务聚合根的核心业务逻辑：
 * - 任务创建
 * - 状态转换
 * - 分片管理
 * - Callback 流程
 * - 过期和中止
 */
@DisplayName("TaskAggregate 单元测试")
class TaskAggregateTest {

    @Nested
    @DisplayName("任务创建")
    class CreateTests {

        @Test
        @DisplayName("应成功创建无 callback 的任务")
        void shouldCreateTaskWithoutCallbacks() {
            // Given & When
            TaskAggregate task = TaskAggregate.create(
                    "test-fkey",
                    List.of(),
                    Duration.ofHours(24)
            );

            // Then
            assertNotNull(task.getTaskId());
            assertEquals("test-fkey", task.getFKey());
            assertEquals(TaskStatus.PENDING, task.getStatus());
            assertFalse(task.hasCallbacks());
            assertNotNull(task.getCreatedAt());
            assertNotNull(task.getExpiresAt());
        }

        @Test
        @DisplayName("应成功创建有 callback 的任务")
        void shouldCreateTaskWithCallbacks() {
            // Given
            List<CallbackConfig> callbacks = List.of(
                    new CallbackConfig("virus-scan", List.of()),
                    new CallbackConfig("thumbnail", List.of())
            );

            // When
            TaskAggregate task = TaskAggregate.create(
                    "test-fkey",
                    callbacks,
                    Duration.ofHours(24)
            );

            // Then
            assertTrue(task.hasCallbacks());
            assertNotNull(task.getContext());
        }

        @Test
        @DisplayName("过期时间应正确设置")
        void shouldSetExpirationCorrectly() {
            // Given
            Duration expireAfter = Duration.ofHours(12);
            Instant before = Instant.now().plus(expireAfter).minusSeconds(5);

            // When
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), expireAfter);

            // Then
            Instant after = Instant.now().plus(expireAfter).plusSeconds(5);
            assertTrue(task.getExpiresAt().isAfter(before));
            assertTrue(task.getExpiresAt().isBefore(after));
        }
    }

    @Nested
    @DisplayName("任务状态转换")
    class StateTransitionTests {

        @Test
        @DisplayName("应成功从 PENDING 转换到 IN_PROGRESS")
        void shouldTransitionFromPendingToInProgress() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));

            // When
            task.startUpload("session-123", "node-1");

            // Then
            assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
            assertEquals("session-123", task.getSessionId());
            assertEquals("node-1", task.getNodeId());
        }

        @Test
        @DisplayName("不能在非 PENDING 状态启动上传")
        void shouldNotStartUploadInWrongState() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            task.startUpload("session-123", "node-1");

            // When & Then
            assertThrows(IllegalStateException.class, () ->
                    task.startUpload("session-456", "node-2")
            );
        }

        @Test
        @DisplayName("记录分片应自动将 PENDING 转换为 IN_PROGRESS")
        void shouldTransitionToInProgressWhenRecordingPart() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            PartInfo part = PartInfo.of(1, "etag-123", 1024);

            // When
            task.recordPart(part);

            // Then
            assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
            assertEquals(1, task.getParts().size());
        }

        @Test
        @DisplayName("完成上传无 callback 应直接标记为 COMPLETED")
        void shouldCompleteDirectlyWithoutCallbacks() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            task.startUpload("session-123", "node-1");

            // When
            task.completeUpload("/path/to/file", "hash-abc", 10240L, "image/png", "test.png");

            // Then
            assertEquals(TaskStatus.COMPLETED, task.getStatus());
            assertEquals("/path/to/file", task.getStoragePath());
            assertEquals("hash-abc", task.getHash());
            assertEquals(10240L, task.getTotalSize());
            assertNotNull(task.getCompletedAt());
        }

        @Test
        @DisplayName("完成上传有 callback 应进入 PROCESSING")
        void shouldTransitionToProcessingWithCallbacks() {
            // Given
            List<CallbackConfig> callbacks = List.of(
                    new CallbackConfig("virus-scan", List.of())
            );
            TaskAggregate task = TaskAggregate.create("test-fkey", callbacks, Duration.ofHours(24));
            task.startUpload("session-123", "node-1");

            // When
            task.completeUpload("/path/to/file", "hash-abc", 10240L, "image/png", "test.png");

            // Then
            assertEquals(TaskStatus.PROCESSING, task.getStatus());
            assertTrue(task.getCurrentCallback().isPresent());
            assertEquals("virus-scan", task.getCurrentCallback().get());
        }
    }

    @Nested
    @DisplayName("分片管理")
    class PartManagementTests {

        @Test
        @DisplayName("应正确记录多个分片")
        void shouldRecordMultipleParts() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));

            // When
            task.recordPart(PartInfo.of(1, "etag-1", 1024));
            task.recordPart(PartInfo.of(2, "etag-2", 2048));
            task.recordPart(PartInfo.of(3, "etag-3", 512));

            // Then
            assertEquals(3, task.getParts().size());
        }

        @Test
        @DisplayName("重复的分片应被替换")
        void shouldReplaceDuplicatePart() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            task.recordPart(PartInfo.of(1, "old-etag", 1024));

            // When
            task.recordPart(PartInfo.of(1, "new-etag", 2048));

            // Then
            assertEquals(1, task.getParts().size());
            PartInfo part = task.getParts().get(0);
            assertEquals("new-etag", part.etag());
            assertEquals(2048, part.size());
        }

        @Test
        @DisplayName("应按分片序号排序返回")
        void shouldReturnPartsSorted() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            task.recordPart(PartInfo.of(3, "etag-3", 512));
            task.recordPart(PartInfo.of(1, "etag-1", 1024));
            task.recordPart(PartInfo.of(2, "etag-2", 2048));

            // When
            List<PartInfo> sorted = task.getSortedParts();

            // Then
            assertEquals(3, sorted.size());
            assertEquals(1, sorted.get(0).partNumber());
            assertEquals(2, sorted.get(1).partNumber());
            assertEquals(3, sorted.get(2).partNumber());
        }
    }

    @Nested
    @DisplayName("Callback 执行流程")
    class CallbackFlowTests {

        @Test
        @DisplayName("应正确推进 callback 索引")
        void shouldAdvanceCallbackIndex() {
            // Given
            List<CallbackConfig> callbacks = List.of(
                    new CallbackConfig("virus-scan", List.of()),
                    new CallbackConfig("thumbnail", List.of())
            );
            TaskAggregate task = TaskAggregate.create("test-fkey", callbacks, Duration.ofHours(24));
            task.startUpload("session-123", "node-1");
            task.completeUpload("/path", "hash", 1024L, "text/plain", "test.txt");

            // When
            assertEquals("virus-scan", task.getCurrentCallback().orElse(null));
            task.advanceCallback();

            // Then
            assertEquals(TaskStatus.PROCESSING, task.getStatus());
            assertEquals("thumbnail", task.getCurrentCallback().orElse(null));
        }

        @Test
        @DisplayName("最后一个 callback 完成应标记为 COMPLETED")
        void shouldCompleteAfterLastCallback() {
            // Given
            List<CallbackConfig> callbacks = List.of(
                    new CallbackConfig("virus-scan", List.of())
            );
            TaskAggregate task = TaskAggregate.create("test-fkey", callbacks, Duration.ofHours(24));
            task.startUpload("session-123", "node-1");
            task.completeUpload("/path", "hash", 1024L, "text/plain", "test.txt");

            // When
            task.advanceCallback();

            // Then
            assertEquals(TaskStatus.COMPLETED, task.getStatus());
            assertFalse(task.getCurrentCallback().isPresent());
            assertNotNull(task.getCompletedAt());
        }

        @Test
        @DisplayName("非 PROCESSING 状态不能推进 callback")
        void shouldNotAdvanceCallbackInWrongState() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));

            // When & Then
            assertThrows(IllegalStateException.class, task::advanceCallback);
        }
    }

    @Nested
    @DisplayName("任务中止和失败")
    class AbortAndFailTests {

        @Test
        @DisplayName("PENDING 状态可以中止")
        void shouldAbortPendingTask() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));

            // When
            task.abort();

            // Then
            assertEquals(TaskStatus.ABORTED, task.getStatus());
            assertNotNull(task.getCompletedAt());
        }

        @Test
        @DisplayName("IN_PROGRESS 状态可以中止")
        void shouldAbortInProgressTask() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            task.startUpload("session-123", "node-1");

            // When
            task.abort();

            // Then
            assertEquals(TaskStatus.ABORTED, task.getStatus());
        }

        @Test
        @DisplayName("COMPLETED 状态不能中止")
        void shouldNotAbortCompletedTask() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            task.startUpload("session-123", "node-1");
            task.completeUpload("/path", "hash", 1024L, "text/plain", "test.txt");

            // When & Then
            assertThrows(IllegalStateException.class, task::abort);
        }

        @Test
        @DisplayName("应正确标记失败原因")
        void shouldMarkFailedWithReason() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            task.startUpload("session-123", "node-1");

            // When
            task.markFailed("Virus detected");

            // Then
            assertEquals(TaskStatus.FAILED, task.getStatus());
            assertEquals("Virus detected", task.getFailureReason());
            assertNotNull(task.getCompletedAt());
        }
    }

    @Nested
    @DisplayName("任务过期")
    class ExpirationTests {

        @Test
        @DisplayName("应正确判断任务是否过期")
        void shouldDetectExpiredTask() {
            // Given - 创建一个已过期的任务
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofMillis(-1000));

            // When & Then
            assertTrue(task.isExpired());
        }

        @Test
        @DisplayName("未过期任务应返回 false")
        void shouldReturnFalseForNonExpiredTask() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));

            // When & Then
            assertFalse(task.isExpired());
        }

        @Test
        @DisplayName("已完成任务不应被标记为过期")
        void shouldNotMarkCompletedTaskAsExpired() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofMillis(-1000));
            task.startUpload("session-123", "node-1");
            task.completeUpload("/path", "hash", 1024L, "text/plain", "test.txt");

            // When & Then
            assertFalse(task.isExpired());
        }

        @Test
        @DisplayName("应能标记过期状态")
        void shouldMarkExpired() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            task.startUpload("session-123", "node-1");

            // When
            task.markExpired();

            // Then
            assertEquals(TaskStatus.EXPIRED, task.getStatus());
            assertNotNull(task.getCompletedAt());
        }

        @Test
        @DisplayName("终态任务标记过期应被忽略")
        void shouldIgnoreExpireOnTerminalState() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            task.startUpload("session-123", "node-1");
            task.completeUpload("/path", "hash", 1024L, "text/plain", "test.txt");

            // When
            task.markExpired();

            // Then
            assertEquals(TaskStatus.COMPLETED, task.getStatus()); // 保持原状态
        }
    }

    @Nested
    @DisplayName("Context 管理")
    class ContextManagementTests {

        @Test
        @DisplayName("完成上传应更新 context")
        void shouldUpdateContextOnComplete() {
            // Given
            TaskAggregate task = TaskAggregate.create("test-fkey", List.of(), Duration.ofHours(24));
            task.startUpload("session-123", "node-1");

            // When
            task.completeUpload("/path/to/file", "hash-abc", 10240L, "image/png", "test.png");

            // Then
            assertEquals("/path/to/file", task.getContext().get("storagePath").orElse(null));
            assertEquals("hash-abc", task.getContext().get("fileHash").orElse(null));
            assertEquals(10240L, task.getContext().get("fileSize").orElse(null));
            assertEquals("image/png", task.getContext().get("contentType").orElse(null));
            assertEquals("test.png", task.getContext().get("filename").orElse(null));
        }
    }
}
