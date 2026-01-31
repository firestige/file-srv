package tech.icc.filesrv.core.testdata.stubs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.icc.filesrv.common.enums.TaskStatus;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.testdata.fixtures.TaskFixtures;

import static org.assertj.core.api.Assertions.*;

/**
 * MockCallbackExecutorStub 使用示例
 * <p>
 * 演示如何在测试中使用 MockCallbackExecutorStub 进行 callback 执行验证。
 */
@DisplayName("MockCallbackExecutorStub 使用示例")
class MockCallbackExecutorStubUsageExamples {

    /**
     * 示例 1: 基本用法 - 记录和验证调用
     */
    @Test
    @DisplayName("示例 1: 基本用法")
    void example1_BasicUsage() {
        // 创建 stub
        MockCallbackExecutorStub stub = new MockCallbackExecutorStub();

        // 获取 publisher 和 runner
        var publisher = stub.getPublisher();
        var runner = stub.getRunner();

        // 模拟发布和执行
        TaskAggregate task = TaskFixtures.processingTask();
        publisher.publish(task.getTaskId());
        runner.run(task);

        // 验证调用记录
        assertThat(stub.isPublished(task.getTaskId())).isTrue();
        assertThat(stub.isExecuted(task.getTaskId())).isTrue();
        assertThat(stub.getPublishedTasks()).containsExactly(task.getTaskId());
        assertThat(stub.getExecutedTasks()).containsExactly(task);
    }

    /**
     * 示例 2: 注入断言 - 验证任务参数
     */
    @Test
    @DisplayName("示例 2: 注入断言验证参数")
    void example2_AssertionInjection() {
        MockCallbackExecutorStub stub = new MockCallbackExecutorStub();

        // 注入 publish 断言：验证任务 ID 格式
        stub.onPublish(taskId -> {
            assertThat(taskId).matches("^[A-Z0-9]{32}$");
            assertThat(taskId).isNotBlank();
        });

        // 注入 run 断言：验证任务状态和 callback 配置
        stub.onRun(task -> {
            assertThat(task.getStatus()).isEqualTo(TaskStatus.PROCESSING);
            assertThat(task.getCallbacks()).isNotEmpty();
            assertThat(task.getCurrentCallbackIndex()).isGreaterThanOrEqualTo(0);
        });

        // 执行测试
        TaskAggregate task = TaskFixtures.processingTask();
        stub.getPublisher().publish(task.getTaskId());
        stub.getRunner().run(task);

        // 所有断言自动执行，无异常表示验证通过
    }

    /**
     * 示例 3: 执行详情查询 - 检查执行结果
     */
    @Test
    @DisplayName("示例 3: 查询执行详情")
    void example3_ExecutionDetails() {
        MockCallbackExecutorStub stub = new MockCallbackExecutorStub();
        TaskAggregate task = TaskFixtures.processingTask();

        // 执行任务
        stub.getRunner().run(task);

        // 查询执行详情
        var detail = stub.getExecutionDetail(task.getTaskId());
        assertThat(detail).isNotNull();
        assertThat(detail.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(detail.isSucceeded()).isTrue();
        assertThat(detail.getStartIndex()).isEqualTo(0);
        assertThat(detail.getTotalCallbacks()).isEqualTo(task.getCallbacks().size());
        assertThat(detail.getDuration()).isNotNull().isGreaterThanOrEqualTo(0);

        // 打印详情
        System.out.println(detail);
    }

    /**
     * 示例 4: 模拟失败场景 - 测试错误处理
     */
    @Test
    @DisplayName("示例 4: 模拟执行失败")
    void example4_SimulateFailure() {
        MockCallbackExecutorStub stub = new MockCallbackExecutorStub();
        TaskAggregate task = TaskFixtures.processingTask();

        // 配置失败模拟
        stub.simulateFailure("Network timeout after 3 retries");

        // 验证抛出异常
        assertThatThrownBy(() -> stub.getRunner().run(task))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Network timeout after 3 retries");

        // 验证失败详情
        var detail = stub.getExecutionDetail(task.getTaskId());
        assertThat(detail.isSucceeded()).isFalse();
        assertThat(detail.getFailureReason()).isEqualTo("Network timeout after 3 retries");
    }

    /**
     * 示例 5: 自动执行模式 - publish 触发 run
     */
    @Test
    @DisplayName("示例 5: 自动执行模式")
    void example5_AutoExecuteMode() {
        MockCallbackExecutorStub stub = new MockCallbackExecutorStub();
        TaskAggregate task = TaskFixtures.processingTask();

        // 启用自动执行
        stub.enableAutoExecute();

        // 将任务加入执行队列（模拟）
        stub.getExecutedTasks().add(task);

        // 只需 publish，自动触发 run
        stub.getPublisher().publish(task.getTaskId());

        // 验证已自动执行
        assertThat(stub.isPublished(task.getTaskId())).isTrue();
        assertThat(stub.isExecuted(task.getTaskId())).isTrue();
    }

    /**
     * 示例 6: 多任务场景 - 验证批量执行
     */
    @Test
    @DisplayName("示例 6: 多任务批量执行")
    void example6_MultipleTasksExecution() {
        MockCallbackExecutorStub stub = new MockCallbackExecutorStub();

        // 准备多个任务
        TaskAggregate task1 = TaskFixtures.processingTask();
        TaskAggregate task2 = TaskFixtures.processingTask();
        TaskAggregate task3 = TaskFixtures.processingTask();

        // 执行任务
        stub.getRunner().run(task1);
        stub.getRunner().run(task2);
        stub.getRunner().run(task3);

        // 验证所有任务都已执行
        assertThat(stub.getExecutedTasks()).hasSize(3);
        assertThat(stub.getAllExecutionDetails()).hasSize(3);

        // 验证每个任务都成功
        stub.getAllExecutionDetails().values().forEach(detail -> {
            assertThat(detail.isSucceeded()).isTrue();
        });
    }

    /**
     * 示例 7: 断言失败记录 - 验证测试本身
     */
    @Test
    @DisplayName("示例 7: 验证断言失败被记录")
    void example7_AssertionFailureRecording() {
        MockCallbackExecutorStub stub = new MockCallbackExecutorStub();
        TaskAggregate task = TaskFixtures.processingTask();

        // 注入一个会失败的断言
        stub.onRun(t -> {
            throw new AssertionError("Expected 5 callbacks, got " + t.getCallbacks().size());
        });

        // 执行并捕获异常
        assertThatThrownBy(() -> stub.getRunner().run(task))
                .isInstanceOf(AssertionError.class);

        // 验证失败被记录
        var detail = stub.getExecutionDetail(task.getTaskId());
        assertThat(detail.isSucceeded()).isFalse();
        assertThat(detail.getFailureReason()).contains("Expected 5 callbacks");
    }

    /**
     * 示例 8: 链式配置 - 流畅的 API 使用
     */
    @Test
    @DisplayName("示例 8: 链式配置")
    void example8_FluentConfiguration() {
        TaskAggregate task = TaskFixtures.processingTask();

        // 链式配置 stub
        MockCallbackExecutorStub stub = new MockCallbackExecutorStub()
                .onPublish(taskId -> assertThat(taskId).isNotBlank())
                .onRun(t -> assertThat(t.getCallbacks()).isNotEmpty())
                .enableAutoExecute();

        // 执行测试
        stub.getExecutedTasks().add(task);
        stub.getPublisher().publish(task.getTaskId());

        // 验证
        assertThat(stub.isPublished(task.getTaskId())).isTrue();
        assertThat(stub.isExecuted(task.getTaskId())).isTrue();
    }

    /**
     * 示例 9: Service 层集成测试
     * <p>
     * 演示如何在 Service 层测试中使用 MockCallbackExecutorStub
     */
    @Test
    @DisplayName("示例 9: Service 层集成测试")
    void example9_ServiceLayerIntegration() {
        // 创建 stub
        MockCallbackExecutorStub stub = new MockCallbackExecutorStub();

        // 配置断言：验证 Service 传入的参数
        stub.onPublish(taskId -> {
            assertThat(taskId).matches("^[A-Z0-9]{32}$");
        });

        stub.onRun(task -> {
            assertThat(task.getStatus()).isEqualTo(TaskStatus.PROCESSING);
            assertThat(task.getCallbacks()).hasSizeGreaterThan(0);
            assertThat(task.getStoragePath()).isNotBlank();
        });

        // 模拟 Service 调用
        TaskAggregate task = TaskFixtures.processingTask();
        
        // Service 层会调用 publisher.publish()
        stub.getPublisher().publish(task.getTaskId());
        
        // Consumer 会调用 runner.run()
        stub.getRunner().run(task);

        // 验证调用链
        assertThat(stub.getPublishedTasks()).containsExactly(task.getTaskId());
        assertThat(stub.getExecutedTasks()).containsExactly(task);
        
        // 验证执行成功
        var detail = stub.getExecutionDetail(task.getTaskId());
        assertThat(detail.isSucceeded()).isTrue();
        assertThat(detail.getDuration()).isLessThan(1000L); // 1秒内完成
    }

    /**
     * 示例 10: 清理和重置
     */
    @Test
    @DisplayName("示例 10: 清理和重置")
    void example10_ClearAndReset() {
        MockCallbackExecutorStub stub = new MockCallbackExecutorStub();
        TaskAggregate task = TaskFixtures.processingTask();

        // 执行一些操作
        stub.getPublisher().publish(task.getTaskId());
        stub.getRunner().run(task);
        stub.onPublish(taskId -> {});
        stub.simulateFailure("Error");

        // 清理所有状态
        stub.clear();

        // 验证已清空
        assertThat(stub.getPublishedTasks()).isEmpty();
        assertThat(stub.getExecutedTasks()).isEmpty();
        assertThat(stub.getAllExecutionDetails()).isEmpty();

        // 重置为成功模式
        stub.resetToSuccess();
        
        // 可以继续使用
        TaskAggregate newTask = TaskFixtures.processingTask();
        stub.getRunner().run(newTask);
        
        var detail = stub.getExecutionDetail(newTask.getTaskId());
        assertThat(detail.isSucceeded()).isTrue();
    }
}
