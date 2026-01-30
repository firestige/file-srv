package tech.icc.filesrv.core.testdata.stubs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.core.infra.executor.CallbackChainRunner;
import tech.icc.filesrv.core.infra.executor.CallbackTaskPublisher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Mock Callback 执行器 Stub
 * <p>
 * 用于测试场景，提供 callback 执行的验证和断言能力。
 * <p>
 * 主要功能：
 * <ul>
 *   <li>记录所有发布和执行的 callback 任务</li>
 *   <li>支持注入断言函数，验证执行参数</li>
 *   <li>提供查询接口，检查调用历史</li>
 *   <li>支持模拟执行成功/失败场景</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 创建 stub
 * MockCallbackExecutorStub stub = new MockCallbackExecutorStub();
 * 
 * // 注入断言：验证任务 ID 格式
 * stub.onPublish(taskId -> {
 *     assertThat(taskId).matches("^[A-Z0-9]{32}$");
 * });
 * 
 * // 注入断言：验证任务状态
 * stub.onRun(task -> {
 *     assertThat(task.getStatus()).isEqualTo(TaskStatus.PROCESSING);
 *     assertThat(task.getCallbacks()).isNotEmpty();
 * });
 * 
 * // 执行测试
 * stub.getPublisher().publish("TASK123");
 * stub.getRunner().run(task);
 * 
 * // 验证调用
 * assertThat(stub.getPublishedTasks()).containsExactly("TASK123");
 * assertThat(stub.getExecutedTasks()).containsExactly(task);
 * }</pre>
 */
public class MockCallbackExecutorStub {

    private static final Logger log = LoggerFactory.getLogger(MockCallbackExecutorStub.class);

    /**
     * 发布的任务 ID 列表
     */
    private final List<String> publishedTasks = new CopyOnWriteArrayList<>();

    /**
     * 执行的任务列表
     */
    private final List<TaskAggregate> executedTasks = new CopyOnWriteArrayList<>();

    /**
     * 任务执行详情：taskId → 详细信息
     */
    private final Map<String, ExecutionDetail> executionDetails = new LinkedHashMap<>();

    /**
     * publish 断言函数列表
     */
    private final List<Consumer<String>> publishAssertions = new ArrayList<>();

    /**
     * run 断言函数列表
     */
    private final List<Consumer<TaskAggregate>> runAssertions = new ArrayList<>();

    /**
     * 是否启用自动执行（publish 时自动执行 callback 链）
     */
    private boolean autoExecute = false;

    /**
     * 是否模拟执行失败
     */
    private boolean simulateFailure = false;

    /**
     * 模拟失败的原因
     */
    private String failureReason = "Simulated callback execution failure";

    // ============ Public API ============

    /**
     * 获取 Publisher 实例
     */
    public CallbackTaskPublisher getPublisher() {
        return new MockPublisher();
    }

    /**
     * 获取 Runner 实例
     */
    public CallbackChainRunner getRunner() {
        return new MockRunner();
    }

    /**
     * 注入 publish 断言
     * <p>
     * 示例：验证任务 ID 格式
     * <pre>{@code
     * stub.onPublish(taskId -> {
     *     assertThat(taskId).matches("^[A-Z0-9]{32}$");
     * });
     * }</pre>
     */
    public MockCallbackExecutorStub onPublish(Consumer<String> assertion) {
        publishAssertions.add(assertion);
        return this;
    }

    /**
     * 注入 run 断言
     * <p>
     * 示例：验证任务状态和 callback 数量
     * <pre>{@code
     * stub.onRun(task -> {
     *     assertThat(task.getStatus()).isEqualTo(TaskStatus.PROCESSING);
     *     assertThat(task.getCallbacks()).hasSizeGreaterThan(0);
     * });
     * }</pre>
     */
    public MockCallbackExecutorStub onRun(Consumer<TaskAggregate> assertion) {
        runAssertions.add(assertion);
        return this;
    }

    /**
     * 启用自动执行模式
     * <p>
     * 当 publish 时自动调用 runner.run()
     */
    public MockCallbackExecutorStub enableAutoExecute() {
        this.autoExecute = true;
        return this;
    }

    /**
     * 禁用自动执行模式
     */
    public MockCallbackExecutorStub disableAutoExecute() {
        this.autoExecute = false;
        return this;
    }

    /**
     * 模拟执行失败
     */
    public MockCallbackExecutorStub simulateFailure(String reason) {
        this.simulateFailure = true;
        this.failureReason = reason;
        return this;
    }

    /**
     * 重置为正常执行
     */
    public MockCallbackExecutorStub resetToSuccess() {
        this.simulateFailure = false;
        this.failureReason = null;
        return this;
    }

    // ============ Query API ============

    /**
     * 获取所有发布的任务 ID
     */
    public List<String> getPublishedTasks() {
        return new ArrayList<>(publishedTasks);
    }

    /**
     * 获取所有执行的任务
     */
    public List<TaskAggregate> getExecutedTasks() {
        return new ArrayList<>(executedTasks);
    }

    /**
     * 检查任务是否已发布
     */
    public boolean isPublished(String taskId) {
        return publishedTasks.contains(taskId);
    }

    /**
     * 检查任务是否已执行
     */
    public boolean isExecuted(String taskId) {
        return executionDetails.containsKey(taskId);
    }

    /**
     * 获取任务执行详情
     */
    public ExecutionDetail getExecutionDetail(String taskId) {
        return executionDetails.get(taskId);
    }

    /**
     * 获取所有执行详情
     */
    public Map<String, ExecutionDetail> getAllExecutionDetails() {
        return new LinkedHashMap<>(executionDetails);
    }

    /**
     * 清空所有记录
     */
    public void clear() {
        publishedTasks.clear();
        executedTasks.clear();
        executionDetails.clear();
        publishAssertions.clear();
        runAssertions.clear();
        autoExecute = false;
        simulateFailure = false;
        failureReason = null;
    }

    // ============ Inner Classes ============

    /**
     * Mock Publisher 实现
     */
    private class MockPublisher implements CallbackTaskPublisher {
        @Override
        public void publish(String taskId) {
            log.debug("Mock publish: taskId={}", taskId);
            publishedTasks.add(taskId);

            // 执行 publish 断言
            for (Consumer<String> assertion : publishAssertions) {
                try {
                    assertion.accept(taskId);
                } catch (AssertionError e) {
                    log.error("Publish assertion failed for taskId={}: {}", taskId, e.getMessage());
                    throw e;
                }
            }

            // 如果启用自动执行，找到对应任务并执行
            if (autoExecute) {
                TaskAggregate task = executedTasks.stream()
                        .filter(t -> t.getTaskId().equals(taskId))
                        .findFirst()
                        .orElse(null);
                if (task != null) {
                    new MockRunner().run(task);
                }
            }
        }
    }

    /**
     * Mock Runner 实现
     */
    private class MockRunner implements CallbackChainRunner {
        @Override
        public void run(TaskAggregate task) {
            String taskId = task.getTaskId();
            log.debug("Mock run: taskId={}, currentIndex={}, totalCallbacks={}",
                    taskId, task.getCurrentCallbackIndex(), task.getCallbacks().size());

            executedTasks.add(task);

            // 记录执行详情
            ExecutionDetail detail = new ExecutionDetail(
                    taskId,
                    task.getCurrentCallbackIndex(),
                    task.getCallbacks().size(),
                    System.currentTimeMillis()
            );
            executionDetails.put(taskId, detail);

            // 执行 run 断言
            for (Consumer<TaskAggregate> assertion : runAssertions) {
                try {
                    assertion.accept(task);
                } catch (AssertionError e) {
                    log.error("Run assertion failed for taskId={}: {}", taskId, e.getMessage());
                    detail.markFailed(e.getMessage());
                    throw e;
                }
            }

            // 模拟失败
            if (simulateFailure) {
                detail.markFailed(failureReason);
                throw new RuntimeException(failureReason);
            }

            // 模拟成功执行
            detail.markSucceeded();
        }
    }

    /**
     * 任务执行详情
     */
    public static class ExecutionDetail {
        private final String taskId;
        private final int startIndex;
        private final int totalCallbacks;
        private final long startTime;
        private Long endTime;
        private boolean succeeded;
        private String failureReason;

        public ExecutionDetail(String taskId, int startIndex, int totalCallbacks, long startTime) {
            this.taskId = taskId;
            this.startIndex = startIndex;
            this.totalCallbacks = totalCallbacks;
            this.startTime = startTime;
        }

        public void markSucceeded() {
            this.succeeded = true;
            this.endTime = System.currentTimeMillis();
        }

        public void markFailed(String reason) {
            this.succeeded = false;
            this.failureReason = reason;
            this.endTime = System.currentTimeMillis();
        }

        public String getTaskId() {
            return taskId;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getTotalCallbacks() {
            return totalCallbacks;
        }

        public long getStartTime() {
            return startTime;
        }

        public Long getEndTime() {
            return endTime;
        }

        public boolean isSucceeded() {
            return succeeded;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public Long getDuration() {
            return endTime != null ? endTime - startTime : null;
        }

        @Override
        public String toString() {
            return "ExecutionDetail{" +
                    "taskId='" + taskId + '\'' +
                    ", startIndex=" + startIndex +
                    ", totalCallbacks=" + totalCallbacks +
                    ", duration=" + getDuration() + "ms" +
                    ", succeeded=" + succeeded +
                    (failureReason != null ? ", failureReason='" + failureReason + '\'' : "") +
                    '}';
        }
    }
}
