package tech.icc.filesrv.core.testdata.stubs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.testdata.TestDataBuilders;
import tech.icc.filesrv.core.testdata.fixtures.TaskFixtures;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MockCallbackExecutorStub 测试")
class MockCallbackExecutorStubTest {

    private MockCallbackExecutorStub stub;

    @BeforeEach
    void setUp() {
        stub = new MockCallbackExecutorStub();
    }

    @Nested
    @DisplayName("Publisher 测试")
    class PublisherTests {

        @Test
        @DisplayName("应该记录发布的任务 ID")
        void shouldRecordPublishedTaskIds() {
            // Given
            var publisher = stub.getPublisher();

            // When
            publisher.publish("TASK001");
            publisher.publish("TASK002");
            publisher.publish("TASK003");

            // Then
            assertThat(stub.getPublishedTasks())
                    .containsExactly("TASK001", "TASK002", "TASK003");
        }

        @Test
        @DisplayName("应该执行注入的断言")
        void shouldExecuteInjectedAssertions() {
            // Given
            var publisher = stub.getPublisher();
            stub.onPublish(taskId -> {
                assertThat(taskId).matches("^TASK\\d{3}$");
            });

            // When & Then
            assertThatNoException()
                    .isThrownBy(() -> publisher.publish("TASK001"));

            assertThatThrownBy(() -> publisher.publish("INVALID"))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("应该支持多个断言")
        void shouldSupportMultipleAssertions() {
            // Given
            var publisher = stub.getPublisher();
            stub.onPublish(taskId -> assertThat(taskId).isNotBlank())
                    .onPublish(taskId -> assertThat(taskId).hasSizeGreaterThan(3))
                    .onPublish(taskId -> assertThat(taskId).startsWith("TASK"));

            // When & Then
            assertThatNoException()
                    .isThrownBy(() -> publisher.publish("TASK123"));

            assertThatThrownBy(() -> publisher.publish("ABC"))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("应该检查任务是否已发布")
        void shouldCheckIfTaskIsPublished() {
            // Given
            var publisher = stub.getPublisher();
            publisher.publish("TASK001");

            // When & Then
            assertThat(stub.isPublished("TASK001")).isTrue();
            assertThat(stub.isPublished("TASK002")).isFalse();
        }
    }

    @Nested
    @DisplayName("Runner 测试")
    class RunnerTests {

        @Test
        @DisplayName("应该记录执行的任务")
        void shouldRecordExecutedTasks() {
            // Given
            var runner = stub.getRunner();
            TaskAggregate task1 = TaskFixtures.processingTask();
            TaskAggregate task2 = TaskFixtures.processingTask();

            // When
            runner.run(task1);
            runner.run(task2);

            // Then
            assertThat(stub.getExecutedTasks())
                    .hasSize(2)
                    .containsExactly(task1, task2);
        }

        @Test
        @DisplayName("应该记录执行详情")
        void shouldRecordExecutionDetails() {
            // Given
            var runner = stub.getRunner();
            TaskAggregate task = TaskFixtures.processingTask();

            // When
            runner.run(task);

            // Then
            var detail = stub.getExecutionDetail(task.getTaskId());
            assertThat(detail).isNotNull();
            assertThat(detail.getTaskId()).isEqualTo(task.getTaskId());
            assertThat(detail.getStartIndex()).isEqualTo(task.getCurrentCallbackIndex());
            assertThat(detail.getTotalCallbacks()).isEqualTo(task.getCallbacks().size());
            assertThat(detail.isSucceeded()).isTrue();
            assertThat(detail.getDuration()).isNotNull().isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("应该执行注入的断言")
        void shouldExecuteInjectedAssertions() {
            // Given
            var runner = stub.getRunner();
            stub.onRun(task -> {
                assertThat(task.getCallbacks()).isNotEmpty();
            });

            TaskAggregate validTask = TaskFixtures.processingTask();
            TaskAggregate invalidTask = TestDataBuilders.aTask()
                    .withFKey("test")
                    .build();

            // When & Then
            assertThatNoException()
                    .isThrownBy(() -> runner.run(validTask));

            assertThatThrownBy(() -> runner.run(invalidTask))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("应该支持多个断言")
        void shouldSupportMultipleAssertions() {
            // Given
            var runner = stub.getRunner();
            stub.onRun(task -> assertThat(task.getTaskId()).isNotBlank())
                    .onRun(task -> assertThat(task.getCallbacks()).isNotEmpty())
                    .onRun(task -> assertThat(task.getCurrentCallbackIndex()).isGreaterThanOrEqualTo(0));

            TaskAggregate task = TaskFixtures.processingTask();

            // When & Then
            assertThatNoException()
                    .isThrownBy(() -> runner.run(task));
        }

        @Test
        @DisplayName("应该检查任务是否已执行")
        void shouldCheckIfTaskIsExecuted() {
            // Given
            var runner = stub.getRunner();
            TaskAggregate task = TaskFixtures.processingTask();
            runner.run(task);

            // When & Then
            assertThat(stub.isExecuted(task.getTaskId())).isTrue();
            assertThat(stub.isExecuted("UNKNOWN_TASK")).isFalse();
        }
    }

    @Nested
    @DisplayName("失败模拟测试")
    class FailureSimulationTests {

        @Test
        @DisplayName("应该模拟执行失败")
        void shouldSimulateExecutionFailure() {
            // Given
            var runner = stub.getRunner();
            stub.simulateFailure("Simulated network error");
            TaskAggregate task = TaskFixtures.processingTask();

            // When & Then
            assertThatThrownBy(() -> runner.run(task))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Simulated network error");

            var detail = stub.getExecutionDetail(task.getTaskId());
            assertThat(detail.isSucceeded()).isFalse();
            assertThat(detail.getFailureReason()).isEqualTo("Simulated network error");
        }

        @Test
        @DisplayName("应该重置为正常执行")
        void shouldResetToSuccessMode() {
            // Given
            var runner = stub.getRunner();
            stub.simulateFailure("Error").resetToSuccess();
            TaskAggregate task = TaskFixtures.processingTask();

            // When
            runner.run(task);

            // Then
            var detail = stub.getExecutionDetail(task.getTaskId());
            assertThat(detail.isSucceeded()).isTrue();
            assertThat(detail.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("断言失败应该记录在执行详情中")
        void shouldRecordAssertionFailuresInDetail() {
            // Given
            var runner = stub.getRunner();
            stub.onRun(task -> {
                throw new AssertionError("Callback count mismatch");
            });
            TaskAggregate task = TaskFixtures.processingTask();

            // When
            assertThatThrownBy(() -> runner.run(task))
                    .isInstanceOf(AssertionError.class);

            // Then
            var detail = stub.getExecutionDetail(task.getTaskId());
            assertThat(detail.isSucceeded()).isFalse();
            assertThat(detail.getFailureReason()).isEqualTo("Callback count mismatch");
        }
    }

    @Nested
    @DisplayName("自动执行测试")
    class AutoExecuteTests {

        @Test
        @DisplayName("启用自动执行后，publish 应该触发 run")
        void shouldAutoExecuteOnPublish() {
            // Given
            stub.enableAutoExecute();
            TaskAggregate task = TaskFixtures.processingTask();
            stub.getExecutedTasks().add(task); // 模拟任务已在执行队列

            // When
            stub.getPublisher().publish(task.getTaskId());

            // Then
            assertThat(stub.isPublished(task.getTaskId())).isTrue();
            assertThat(stub.isExecuted(task.getTaskId())).isTrue();
        }

        @Test
        @DisplayName("禁用自动执行后，publish 不应触发 run")
        void shouldNotAutoExecuteWhenDisabled() {
            // Given
            stub.disableAutoExecute();
            TaskAggregate task = TaskFixtures.processingTask();

            // When
            stub.getPublisher().publish(task.getTaskId());

            // Then
            assertThat(stub.isPublished(task.getTaskId())).isTrue();
            assertThat(stub.isExecuted(task.getTaskId())).isFalse();
        }
    }

    @Nested
    @DisplayName("查询和清理测试")
    class QueryAndCleanupTests {

        @Test
        @DisplayName("应该获取所有执行详情")
        void shouldGetAllExecutionDetails() {
            // Given
            var runner = stub.getRunner();
            TaskAggregate task1 = TaskFixtures.processingTask();
            TaskAggregate task2 = TaskFixtures.processingTask();

            runner.run(task1);
            runner.run(task2);

            // When
            var details = stub.getAllExecutionDetails();

            // Then
            assertThat(details).hasSize(2);
            assertThat(details.keySet())
                    .containsExactlyInAnyOrder(task1.getTaskId(), task2.getTaskId());
        }

        @Test
        @DisplayName("应该清空所有记录")
        void shouldClearAllRecords() {
            // Given
            var publisher = stub.getPublisher();
            var runner = stub.getRunner();
            TaskAggregate task = TaskFixtures.processingTask();

            publisher.publish("TASK001");
            runner.run(task);
            stub.onPublish(taskId -> {});
            stub.onRun(t -> {});

            // When
            stub.clear();

            // Then
            assertThat(stub.getPublishedTasks()).isEmpty();
            assertThat(stub.getExecutedTasks()).isEmpty();
            assertThat(stub.getAllExecutionDetails()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ExecutionDetail 测试")
    class ExecutionDetailTests {

        @Test
        @DisplayName("应该正确计算执行时长")
        void shouldCalculateDuration() {
            // Given
            var runner = stub.getRunner();
            TaskAggregate task = TaskFixtures.processingTask();

            // When
            runner.run(task);

            // Then
            var detail = stub.getExecutionDetail(task.getTaskId());
            assertThat(detail.getDuration()).isNotNull().isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("应该包含完整的执行信息")
        void shouldContainCompleteExecutionInfo() {
            // Given
            var runner = stub.getRunner();
            TaskAggregate task = TaskFixtures.processingTask();

            // When
            runner.run(task);

            // Then
            var detail = stub.getExecutionDetail(task.getTaskId());
            assertThat(detail.getTaskId()).isEqualTo(task.getTaskId());
            assertThat(detail.getStartIndex()).isEqualTo(task.getCurrentCallbackIndex());
            assertThat(detail.getTotalCallbacks()).isEqualTo(task.getCallbacks().size());
            assertThat(detail.getStartTime()).isGreaterThan(0);
            assertThat(detail.getEndTime()).isGreaterThan(detail.getStartTime());
        }

        @Test
        @DisplayName("toString 应该包含关键信息")
        void shouldFormatToString() {
            // Given
            var runner = stub.getRunner();
            TaskAggregate task = TaskFixtures.processingTask();
            runner.run(task);

            // When
            var detail = stub.getExecutionDetail(task.getTaskId());
            String str = detail.toString();

            // Then
            assertThat(str)
                    .contains(task.getTaskId())
                    .contains("succeeded=true")
                    .contains("duration=");
        }
    }

    @Nested
    @DisplayName("集成场景测试")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("完整流程：发布 → 验证 → 执行 → 验证")
        void shouldSupportFullWorkflow() {
            // Given: 配置断言
            stub.onPublish(taskId -> assertThat(taskId).isNotBlank())
                    .onRun(task -> assertThat(task.getCallbacks()).hasSizeGreaterThan(0));

            TaskAggregate task = TaskFixtures.taskWithCallbacks();

            // When: 发布任务
            stub.getPublisher().publish(task.getTaskId());

            // Then: 验证发布
            assertThat(stub.isPublished(task.getTaskId())).isTrue();

            // When: 执行任务
            stub.getRunner().run(task);

            // Then: 验证执行
            assertThat(stub.isExecuted(task.getTaskId())).isTrue();
            var detail = stub.getExecutionDetail(task.getTaskId());
            assertThat(detail.isSucceeded()).isTrue();
        }

        @Test
        @DisplayName("应该支持连续执行多个任务")
        void shouldSupportMultipleTaskExecution() {
            // Given
            var publisher = stub.getPublisher();
            var runner = stub.getRunner();

            TaskAggregate task1 = TaskFixtures.processingTask();
            TaskAggregate task2 = TaskFixtures.processingTask();
            TaskAggregate task3 = TaskFixtures.processingTask();

            // When
            publisher.publish(task1.getTaskId());
            runner.run(task1);

            publisher.publish(task2.getTaskId());
            runner.run(task2);

            publisher.publish(task3.getTaskId());
            stub.simulateFailure("Network error");
            assertThatThrownBy(() -> runner.run(task3))
                    .isInstanceOf(RuntimeException.class);

            // Then
            assertThat(stub.getPublishedTasks()).hasSize(3);
            assertThat(stub.getExecutedTasks()).hasSize(3);

            assertThat(stub.getExecutionDetail(task1.getTaskId()).isSucceeded()).isTrue();
            assertThat(stub.getExecutionDetail(task2.getTaskId()).isSucceeded()).isTrue();
            assertThat(stub.getExecutionDetail(task3.getTaskId()).isSucceeded()).isFalse();
        }
    }
}
