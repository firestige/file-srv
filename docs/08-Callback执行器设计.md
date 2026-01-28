# Callback 执行器设计

## 一、设计背景

### 1.1 问题陈述

当前 `TaskService` 存在职责不清问题：

| 现有职责 | 所属层 | 问题 |
|---------|--------|------|
| 创建任务、查询任务、状态转换 | 应用层 ✅ | 正确，属于编排 |
| `executeCallbacks()` - 循环执行插件链 | **执行逻辑** ❌ | 应在 infra/executor |
| `validateCallbacks()` - 检查插件存在 | **执行逻辑** ❌ | 应在 executor |
| `prepareLocalFile` / `cleanup` | **执行逻辑** ❌ | 应在 executor |

根据 DDD 分层原则，应用层定位是 **编排和驱动**，不应包含具体的执行逻辑。同时，当前的同步执行模式存在以下问题：

1. **资源限制**：N 个上传任务完成时，无法限制并发执行的 callback 数量
2. **单点问题**：callback 只能在接收请求的节点执行，无法跨节点负载均衡
3. **无持久化**：执行队列在内存中，服务重启会丢失
4. **无重试机制**：失败后无法自动重试

### 1.2 设计目标

| 目标 | 说明 |
|------|------|
| **职责分离** | TaskService 只做编排，执行逻辑迁移到 executor 包 |
| **分布式执行** | 基于 Kafka 实现跨节点负载均衡 |
| **资源控制** | 每个节点可配置并发消费数量 |
| **可靠性** | 消息持久化、幂等处理、重试机制、死信队列 |
| **断点恢复** | 支持从失败的 callback 位置继续执行 |
| **可观测性** | 执行状态、队列深度、成功/失败率等指标 |

### 1.3 设计原则

1. **复用现有抽象**：使用 `PluginResult` 表达插件执行结果，不重复定义
2. **状态即结果**：使用 `TaskAggregate.status` 表达链执行最终状态
3. **异常表达异常**：超时、执行错误等用具体异常类型表达
4. **最小化 API**：接口简洁，实现灵活

## 二、整体架构

### 2.1 架构概览

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     Kafka-Based Callback Executor Architecture                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   TaskService (应用层)                                                          │
│       │                                                                         │
│       │ 1. completeUpload() → task.status = PROCESSING                          │
│       │ 2. publish CallbackTaskMessage                                          │
│       ▼                                                                         │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                    CallbackTaskPublisher                                │   │
│   │  ─────────────────────────────────────                                  │   │
│   │  职责：发布 callback 任务消息到 Kafka                                    │   │
│   │                                                                         │   │
│   │  • publish(taskId, startIndex)  → 发布到 callback-tasks topic           │   │
│   │  • 分区策略：按 taskId hash，保证同一任务消息有序                         │   │
│   └───────────────────────────────┬─────────────────────────────────────────┘   │
│                                   │                                             │
│                                   ▼                                             │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                         Kafka Topic                                     │   │
│   │  ─────────────────────────────────                                      │   │
│   │  Topic: file-callback-tasks                                             │   │
│   │  Partitions: 8 (可配置)                                                  │   │
│   │  Replication: 3                                                         │   │
│   │  Retention: 7d                                                          │   │
│   │                                                                         │   │
│   │  Consumer Group: file-callback-executor                                 │   │
│   │  节点 A 消费 P0, P1 | 节点 B 消费 P2, P3 | ...                            │   │
│   └───────────────────────────────┬─────────────────────────────────────────┘   │
│                                   │                                             │
│                                   ▼                                             │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                    CallbackTaskConsumer                                 │   │
│   │  ─────────────────────────────────────                                  │   │
│   │  Concurrency: 4 (每节点并发消费线程数，可配置)                             │   │
│   │                                                                         │   │
│   │  职责：                                                                  │   │
│   │  • 消费 callback 任务消息                                                │   │
│   │  • 幂等检查、过期检查                                                    │   │
│   │  • 委托 CallbackChainRunner 执行                                         │   │
│   │  • 处理执行结果（成功/失败/重试）                                         │   │
│   │  • 管理 offset commit                                                   │   │
│   └───────────────────────────────┬─────────────────────────────────────────┘   │
│                                   │                                             │
│                                   ▼                                             │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                    CallbackChainRunner                                  │   │
│   │  ─────────────────────────────────────                                  │   │
│   │  职责：执行单个任务的 callback 链                                         │   │
│   │                                                                         │   │
│   │  • 加载 TaskAggregate                                                   │   │
│   │  • 准备本地文件                                                          │   │
│   │  • 从 startIndex 开始执行 callback                                       │   │
│   │  • 解释 PluginResult，更新 Task 状态                                     │   │
│   │  • 每步持久化进度（支持断点恢复）                                          │   │
│   │  • 清理本地文件                                                          │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                    Dead Letter Topic (DLT)                              │   │
│   │  ─────────────────────────────────────                                  │   │
│   │  Topic: file-callback-tasks-dlt                                         │   │
│   │  用途：重试耗尽后的消息存档，供人工介入                                    │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 多节点负载均衡

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Kafka 消费者组负载均衡                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────┐     ┌─────────────────────────────────────┐                   │
│  │ Node A  │     │         Kafka Topic                 │                   │
│  │ Service │────►│  callback-tasks (partitions=8)      │                   │
│  └─────────┘     │                                     │                   │
│                  │  ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐...  │                   │
│  ┌─────────┐     │  │P0 │ │P1 │ │P2 │ │P3 │ │P4 │     │                   │
│  │ Node B  │────►│  └─┬─┘ └─┬─┘ └─┬─┘ └─┬─┘ └─┬─┘     │                   │
│  │ Service │     │    │     │     │     │     │       │                   │
│  └─────────┘     └────┼─────┼─────┼─────┼─────┼───────┘                   │
│                       │     │     │     │     │                           │
│  ┌─────────┐          ▼     ▼     ▼     ▼     ▼                           │
│  │ Node C  │     ┌─────────────────────────────────────┐                   │
│  │ Service │     │   Consumer Group: callback-executor │                   │
│  └─────────┘     │                                     │                   │
│       │          │  ┌────────┐  ┌────────┐  ┌────────┐│                   │
│       └─────────►│  │ Node A │  │ Node B │  │ Node C ││                   │
│                  │  │ P0,P1  │  │ P2,P3  │  │ P4...  ││                   │
│                  │  │ (4线程)│  │ (4线程)│  │ (4线程)││                   │
│                  │  └────────┘  └────────┘  └────────┘│                   │
│                  └─────────────────────────────────────┘                   │
│                                                                             │
│  优势：                                                                      │
│  • 分区自动分配给不同节点                                                     │
│  • 节点增减时自动 rebalance                                                  │
│  • 同一 taskId 的消息总是路由到同一分区（顺序保证）                            │
│  • 每节点可配置并发数，控制资源使用                                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 与应用层的边界

| 组件 | 所属层 | 职责 |
|------|--------|------|
| `TaskService` | 应用层 | 编排、事务、状态转换、发布消息 |
| `CallbackTaskPublisher` | 基础设施层 | 消息发布 |
| `CallbackTaskConsumer` | 基础设施层 | 消息消费、调度 |
| `CallbackChainRunner` | 基础设施层 | 执行细节、超时、错误处理 |
| `PluginRegistry` | 基础设施层 | 插件管理 |
| `LocalFileManager` | 基础设施层 | 本地文件管理 |

## 三、消息设计

### 3.1 Callback 任务消息

```java
/**
 * Callback 任务消息
 * <p>
 * 发布到 Kafka 的消息格式，触发 callback 链执行。
 */
public record CallbackTaskMessage(
    /** 消息唯一标识（用于幂等） */
    String messageId,
    
    /** 任务标识 */
    String taskId,
    
    /** 起始 callback 索引（支持断点恢复） */
    int startIndex,
    
    /** 发布时间 */
    Instant publishedAt,
    
    /** 截止时间（超过不再执行） */
    Instant deadline,
    
    /** 重试次数 */
    int retryCount,
    
    /** 来源节点（调试用） */
    String sourceNode
) {
    public static CallbackTaskMessage create(String taskId, int startIndex, Duration deadline) {
        return new CallbackTaskMessage(
            UUID.randomUUID().toString(),
            taskId,
            startIndex,
            Instant.now(),
            Instant.now().plus(deadline),
            0,
            getNodeId()
        );
    }

    public CallbackTaskMessage withRetry() {
        return new CallbackTaskMessage(
            this.messageId,
            this.taskId,
            this.startIndex,
            this.publishedAt,
            this.deadline,
            this.retryCount + 1,
            this.sourceNode
        );
    }

    public boolean isExpired() {
        return Instant.now().isAfter(deadline);
    }
}
```

### 3.2 消息字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `messageId` | String | UUID，用于幂等检查，防止重复消费 |
| `taskId` | String | 任务标识，同时作为分区键保证顺序 |
| `startIndex` | int | 起始 callback 索引，支持断点恢复 |
| `publishedAt` | Instant | 发布时间，用于监控延迟 |
| `deadline` | Instant | 截止时间，超过则跳过执行 |
| `retryCount` | int | 重试次数，用于退避策略和最大重试判断 |
| `sourceNode` | String | 来源节点，便于问题排查 |

### 3.3 分区策略

```
分区键 = taskId

效果：
• 同一任务的消息总是路由到同一分区
• 保证同一任务的消息顺序消费
• 避免并发执行同一任务的不同 callback
```

### 3.4 死信消息格式

```java
/**
 * 死信消息
 * <p>
 * 重试耗尽或不可恢复错误时发送到 DLT。
 */
public record DeadLetterMessage(
    /** 原始消息 */
    CallbackTaskMessage originalMessage,
    
    /** 失败原因 */
    String failureReason,
    
    /** 最后一次失败时间 */
    Instant failedAt,
    
    /** 失败的 callback 名称 */
    String failedCallback,
    
    /** 执行节点 */
    String executedBy
) {}
```

## 四、核心组件设计

### 4.1 返回值层次关系

为避免重复定义，复用现有抽象：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           返回值层次关系                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  PluginResult (SPI 层，已有)                                                 │
│  ─────────────────────────                                                  │
│  职责：单个插件执行结果                                                       │
│  使用者：Plugin 实现者                                                       │
│  │                                                                          │
│  ├── Success(outputs)   → 执行成功，携带输出数据                             │
│  ├── Failure(reason)    → 执行失败，不可恢复                                 │
│  └── Skip(reason)       → 跳过执行（条件不满足等）                            │
│                                                                             │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                             │
│  ChainRunner 内部处理                                                        │
│  ───────────────────────                                                    │
│  职责：解释 PluginResult，决定下一步动作                                      │
│  │                                                                          │
│  ├── Success → 推进到下一个 callback                                        │
│  ├── Failure → 标记任务失败，停止链                                          │
│  └── Skip    → 跳过当前 callback，继续下一个                                 │
│                                                                             │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                             │
│  Consumer 层面                                                               │
│  ─────────────                                                              │
│  职责：决定消息如何 ack、是否重试                                             │
│  │                                                                          │
│  通过检查 task.status 和异常类型决定：                                        │
│  ├── 正常完成 → ack.acknowledge()                                           │
│  ├── 可重试失败 → 发布重试消息                                               │
│  ├── 不可重试失败 → ack + 发送 DLT                                           │
│  └── 超时 → 由异常处理逻辑决定                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 CallbackTaskPublisher

```java
/**
 * Callback 任务发布者
 * <p>
 * 负责将 callback 任务发布到 Kafka。
 */
public interface CallbackTaskPublisher {

    /**
     * 发布 callback 任务
     *
     * @param taskId     任务标识
     * @param startIndex 起始 callback 索引
     */
    void publish(String taskId, int startIndex);

    /**
     * 发布 callback 任务（带自定义 deadline）
     *
     * @param taskId     任务标识
     * @param startIndex 起始 callback 索引
     * @param deadline   截止时间
     */
    void publish(String taskId, int startIndex, Duration deadline);

    /**
     * 延迟发布（用于重试退避）
     *
     * @param taskId     任务标识
     * @param startIndex 起始 callback 索引
     * @param delay      延迟时间
     */
    void publishWithDelay(String taskId, int startIndex, Duration delay);
}
```

### 4.3 CallbackTaskConsumer

```java
/**
 * Callback 任务消费者
 * <p>
 * Kafka 消费者，处理 callback 任务消息。
 */
public interface CallbackTaskConsumer {

    /**
     * 暂停消费（用于限流或维护）
     */
    void pause();

    /**
     * 恢复消费
     */
    void resume();

    /**
     * 获取当前消费状态
     */
    ConsumerStatus getStatus();

    /**
     * 消费者状态
     */
    record ConsumerStatus(
        boolean paused,
        int activeExecutions,
        long consumedCount,
        long failedCount
    ) {}
}
```

### 4.4 CallbackChainRunner

```java
/**
 * Callback 链执行器
 * <p>
 * 负责单个任务的 callback 链执行，包含：
 * <ul>
 *   <li>本地文件准备</li>
 *   <li>逐个执行 callback</li>
 *   <li>解释 PluginResult，更新 Task 状态</li>
 *   <li>每步持久化进度</li>
 *   <li>资源清理</li>
 * </ul>
 */
public interface CallbackChainRunner {

    /**
     * 执行 callback 链
     * <p>
     * 从 startIndex 开始执行，直到：
     * <ul>
     *   <li>全部完成 → task.status = COMPLETED</li>
     *   <li>某个失败 → task.status = FAILED</li>
     *   <li>超时 → 抛出 CallbackTimeoutException</li>
     * </ul>
     * 
     * @param task       任务聚合（状态为 PROCESSING）
     * @param startIndex 起始 callback 索引
     * @throws CallbackTimeoutException 执行超时
     * @throws CallbackExecutionException 执行异常
     */
    void run(TaskAggregate task, int startIndex);
}
```

### 4.5 异常类型

```java
/**
 * Callback 执行超时异常
 */
public class CallbackTimeoutException extends RuntimeException {
    private final String taskId;
    private final String callbackName;
    private final int callbackIndex;
    
    public CallbackTimeoutException(String taskId, String callbackName, int callbackIndex) {
        super("Callback timeout: " + callbackName);
        this.taskId = taskId;
        this.callbackName = callbackName;
        this.callbackIndex = callbackIndex;
    }
    
    // getters...
}

/**
 * Callback 执行异常（非 PluginResult.Failure 的异常情况）
 */
public class CallbackExecutionException extends RuntimeException {
    private final String taskId;
    private final String callbackName;
    private final int callbackIndex;
    private final boolean retryable;
    
    public CallbackExecutionException(String taskId, String callbackName, 
                                      int callbackIndex, String message, boolean retryable) {
        super(message);
        this.taskId = taskId;
        this.callbackName = callbackName;
        this.callbackIndex = callbackIndex;
        this.retryable = retryable;
    }
    
    // getters...
}
```

### 4.6 IdempotencyChecker

```java
/**
 * 幂等检查器
 * <p>
 * 防止重复消费同一消息。
 */
public interface IdempotencyChecker {

    /**
     * 检查消息是否已处理
     *
     * @param messageId 消息唯一标识
     * @return true 表示已处理（重复）
     */
    boolean isDuplicate(String messageId);
    
    /**
     * 标记消息已处理
     *
     * @param messageId 消息唯一标识
     * @param ttl       过期时间
     */
    void markProcessed(String messageId, Duration ttl);
}
```

## 五、执行流程

### 5.1 任务提交流程

```
┌───────────────────────────────────────────────────────────────────────────┐
│  TaskService.completeUpload()                                             │
│  ─────────────────────────────                                            │
│                                                                           │
│  @Transactional                                                           │
│  public TaskInfoDto completeUpload(...) {                                 │
│      TaskAggregate task = getTaskOrThrow(taskId);                         │
│                                                                           │
│      // 1. 完成存储层上传                                                  │
│      String path = session.complete(parts);                               │
│                                                                           │
│      // 2. 更新任务状态                                                    │
│      task.completeUpload(path, hash, totalSize, ...);                     │
│      // 状态: IN_PROGRESS → PROCESSING                                    │
│      taskRepository.save(task);                                           │
│      updateTaskCache(task);                                               │
│                                                                           │
│      // 3. 发布 callback 任务到 Kafka                                     │
│      if (task.hasCallbacks()) {                                           │
│          callbackPublisher.publish(task.getTaskId(), 0);                  │
│      } else {                                                             │
│          // 无 callback，直接标记完成                                      │
│          task.markCompleted();                                            │
│          taskRepository.save(task);                                       │
│          eventPublisher.publishCompleted(task);                           │
│      }                                                                    │
│                                                                           │
│      // 4. 返回当前状态（可能是 PROCESSING）                               │
│      return toDto(task);                                                  │
│  }                                                                        │
└───────────────────────────────────────────────────────────────────────────┘
```

### 5.2 消费者处理流程

```java
@KafkaListener(
    topics = "${file-service.executor.kafka.topic}",
    groupId = "${file-service.executor.kafka.consumer-group}",
    concurrency = "${file-service.executor.kafka.concurrency}"
)
public void consume(CallbackTaskMessage msg, Acknowledgment ack) {
    
    // 1. 幂等检查
    if (idempotencyChecker.isDuplicate(msg.messageId())) {
        log.debug("Duplicate message: {}", msg.messageId());
        ack.acknowledge();
        return;
    }
    
    // 2. 过期检查
    if (msg.isExpired()) {
        log.warn("Message expired: taskId={}, deadline={}", msg.taskId(), msg.deadline());
        handleExpired(msg);
        ack.acknowledge();
        return;
    }
    
    // 3. 加载任务
    TaskAggregate task = taskRepository.findByTaskId(msg.taskId()).orElse(null);
    if (task == null || task.getStatus() != TaskStatus.PROCESSING) {
        log.warn("Task not found or invalid status: taskId={}", msg.taskId());
        ack.acknowledge();
        return;
    }
    
    try {
        // 4. 执行 callback 链
        chainRunner.run(task, msg.startIndex());
        
        // 5. 标记幂等
        idempotencyChecker.markProcessed(msg.messageId(), properties.getIdempotencyTtl());
        
        // 6. 确认消息
        ack.acknowledge();
        metrics.recordSuccess();
        
    } catch (CallbackTimeoutException e) {
        handleTimeout(e, msg, ack);
        
    } catch (CallbackExecutionException e) {
        handleExecutionError(e, msg, ack);
        
    } catch (Exception e) {
        // 未预期异常：不 ack，让 Kafka 重投递
        log.error("Unexpected error: taskId={}", msg.taskId(), e);
        metrics.recordError();
        throw e;
    }
}
```

### 5.3 Callback 链执行流程

```java
@Override
public void run(TaskAggregate task, int startIndex) {
    TaskContext context = task.getContext();
    
    // 1. 准备本地文件
    Path localPath = localFileManager.prepare(task.getStoragePath(), task.getTaskId());
    context.put(TaskContext.KEY_LOCAL_FILE_PATH, localPath.toString());
    
    try {
        List<String> callbacks = task.getCallbacks();
        
        // 2. 从 startIndex 开始执行
        for (int i = startIndex; i < callbacks.size(); i++) {
            String callbackName = callbacks.get(i);
            log.info("Executing callback: taskId={}, callback={}, index={}", 
                     task.getTaskId(), callbackName, i);
            
            // 3. 执行单个 callback（带超时）
            PluginResult result = executeWithTimeout(task.getTaskId(), callbackName, context, i);
            
            // 4. 解释 PluginResult
            switch (result) {
                case PluginResult.Success success -> {
                    // 合并输出到 context
                    context.putAll(success.outputs());
                    // 推进并持久化（断点恢复关键）
                    task.advanceCallback();
                    taskRepository.save(task);
                    log.debug("Callback succeeded: {}", callbackName);
                }
                
                case PluginResult.Failure failure -> {
                    // 标记任务失败，停止链
                    task.markFailed("Callback [" + callbackName + "] failed: " + failure.reason());
                    taskRepository.save(task);
                    eventPublisher.publishFailed(task);
                    log.error("Callback failed: {} - {}", callbackName, failure.reason());
                    return;  // 正常返回，状态已是 FAILED
                }
                
                case PluginResult.Skip skip -> {
                    // 跳过当前 callback，继续下一个
                    log.info("Callback skipped: {} - {}", callbackName, skip.reason());
                    task.advanceCallback();
                    taskRepository.save(task);
                }
            }
        }
        
        // 5. 全部完成
        task.markCompleted();
        taskRepository.save(task);
        eventPublisher.publishCompleted(task);
        log.info("All callbacks completed: taskId={}", task.getTaskId());
        
    } finally {
        // 6. 清理本地文件
        localFileManager.cleanup(task.getTaskId());
    }
}

private PluginResult executeWithTimeout(String taskId, String callbackName, 
                                        TaskContext context, int index) {
    SharedPlugin plugin = pluginRegistry.getPlugin(callbackName);
    
    Future<PluginResult> future = timeoutExecutor.submit(() -> plugin.apply(context));
    
    try {
        return future.get(properties.getCallbackTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        future.cancel(true);
        throw new CallbackTimeoutException(taskId, callbackName, index);
    } catch (ExecutionException e) {
        Throwable cause = e.getCause();
        throw new CallbackExecutionException(taskId, callbackName, index, 
                                             cause.getMessage(), isRetryable(cause));
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new CallbackExecutionException(taskId, callbackName, index, 
                                             "Interrupted", true);
    }
}
```

### 5.4 重试与死信处理

```java
private void handleTimeout(CallbackTimeoutException e, CallbackTaskMessage msg, Acknowledgment ack) {
    if (canRetry(msg)) {
        // 从超时位置重试
        Duration delay = retryPolicy.getDelay(msg.retryCount());
        log.warn("Callback timeout, retrying: taskId={}, callback={}, delay={}", 
                 msg.taskId(), e.getCallbackName(), delay);
        publisher.publishWithDelay(msg.taskId(), e.getCallbackIndex(), delay);
    } else {
        // 重试耗尽
        TaskAggregate task = taskRepository.findByTaskId(msg.taskId()).orElse(null);
        if (task != null) {
            task.markFailed("Callback timeout after retries: " + e.getCallbackName());
            taskRepository.save(task);
            eventPublisher.publishFailed(task);
        }
        sendToDlt(msg, "Timeout after " + msg.retryCount() + " retries");
    }
    ack.acknowledge();
    metrics.recordTimeout();
}

private void handleExecutionError(CallbackExecutionException e, CallbackTaskMessage msg, Acknowledgment ack) {
    if (e.isRetryable() && canRetry(msg)) {
        Duration delay = retryPolicy.getDelay(msg.retryCount());
        log.warn("Callback error, retrying: taskId={}, callback={}, delay={}", 
                 msg.taskId(), e.getCallbackName(), delay);
        publisher.publishWithDelay(msg.taskId(), e.getCallbackIndex(), delay);
    } else {
        // 不可重试或重试耗尽
        sendToDlt(msg, e.getMessage());
    }
    ack.acknowledge();
    metrics.recordFailure();
}

private boolean canRetry(CallbackTaskMessage msg) {
    return msg.retryCount() < retryPolicy.getMaxRetries() && !msg.isExpired();
}

private void sendToDlt(CallbackTaskMessage msg, String reason) {
    DeadLetterMessage dlt = new DeadLetterMessage(
        msg, reason, Instant.now(), 
        "unknown",  // 可从异常中获取
        nodeId
    );
    dltPublisher.publish(dlt);
    log.error("Message sent to DLT: taskId={}, reason={}", msg.taskId(), reason);
}
```

## 六、配置设计

### 6.1 ExecutorProperties

```java
/**
 * Callback 执行器配置
 */
@ConfigurationProperties(prefix = "file-service.executor")
public record ExecutorProperties(
    /** Kafka 配置 */
    KafkaConfig kafka,
    
    /** 超时配置 */
    TimeoutConfig timeout,
    
    /** 重试配置 */
    RetryConfig retry,
    
    /** 幂等配置 */
    IdempotencyConfig idempotency
) {
    public record KafkaConfig(
        /** 任务 topic */
        String topic,
        /** 死信 topic */
        String dltTopic,
        /** 消费者组 */
        String consumerGroup,
        /** 每节点并发数 */
        int concurrency
    ) {
        public KafkaConfig {
            if (topic == null) topic = "file-callback-tasks";
            if (dltTopic == null) dltTopic = "file-callback-tasks-dlt";
            if (consumerGroup == null) consumerGroup = "file-callback-executor";
            if (concurrency <= 0) concurrency = 4;
        }
    }
    
    public record TimeoutConfig(
        /** 单个 callback 超时 */
        Duration callback,
        /** 整个链超时 */
        Duration chain,
        /** 任务最大等待时间 */
        Duration taskDeadline
    ) {
        public TimeoutConfig {
            if (callback == null) callback = Duration.ofMinutes(5);
            if (chain == null) chain = Duration.ofMinutes(30);
            if (taskDeadline == null) taskDeadline = Duration.ofHours(1);
        }
    }
    
    public record RetryConfig(
        /** 最大重试次数 */
        int maxRetries,
        /** 首次重试延迟 */
        Duration initialDelay,
        /** 退避乘数 */
        double backoffMultiplier,
        /** 最大延迟 */
        Duration maxDelay
    ) {
        public RetryConfig {
            if (maxRetries <= 0) maxRetries = 3;
            if (initialDelay == null) initialDelay = Duration.ofSeconds(1);
            if (backoffMultiplier <= 0) backoffMultiplier = 2.0;
            if (maxDelay == null) maxDelay = Duration.ofMinutes(1);
        }
        
        public Duration getDelay(int retryCount) {
            long delay = (long) (initialDelay.toMillis() * Math.pow(backoffMultiplier, retryCount));
            return Duration.ofMillis(Math.min(delay, maxDelay.toMillis()));
        }
    }
    
    public record IdempotencyConfig(
        /** 幂等 key 过期时间 */
        Duration ttl
    ) {
        public IdempotencyConfig {
            if (ttl == null) ttl = Duration.ofHours(24);
        }
    }
}
```

### 6.2 YAML 配置示例

```yaml
file-service:
  executor:
    kafka:
      topic: file-callback-tasks
      dlt-topic: file-callback-tasks-dlt
      consumer-group: file-callback-executor
      concurrency: 4                    # 每节点并发消费线程数
      
    timeout:
      callback: 5m                      # 单个 callback 超时
      chain: 30m                        # 整个链超时
      task-deadline: 1h                 # 任务最大等待时间
      
    retry:
      max-retries: 3
      initial-delay: 1s
      backoff-multiplier: 2.0
      max-delay: 1m
      
    idempotency:
      ttl: 24h                          # 幂等 key 过期时间

spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false         # 手动 commit
      max-poll-records: 10
      properties:
        max.poll.interval.ms: 300000    # 5分钟，需大于单个 callback 超时
    producer:
      acks: all
      retries: 3
```

## 七、文件结构

### 7.1 新增文件清单

```
file-srv-core/src/main/java/tech/icc/filesrv/core/infra/executor/
├── CallbackTaskPublisher.java            # 任务发布接口
├── CallbackTaskConsumer.java             # 任务消费接口  
├── CallbackChainRunner.java              # Callback 链执行接口
├── IdempotencyChecker.java               # 幂等检查接口
├── ExecutorProperties.java               # 配置属性
├── message/
│   ├── CallbackTaskMessage.java          # Kafka 消息格式
│   └── DeadLetterMessage.java            # 死信消息格式
├── exception/
│   ├── CallbackTimeoutException.java     # 超时异常
│   └── CallbackExecutionException.java   # 执行异常
├── impl/
│   ├── KafkaCallbackTaskPublisher.java   # Kafka 发布实现
│   ├── KafkaCallbackTaskConsumer.java    # Kafka 消费实现
│   ├── DefaultCallbackChainRunner.java   # 链执行实现
│   ├── RedisIdempotencyChecker.java      # Redis 幂等实现
│   └── package-info.java
└── package-info.java
```

### 7.2 修改文件清单

| 文件 | 变更内容 |
|------|----------|
| `TaskService.java` | 移除 `executeCallbacks()` 等执行逻辑，注入 `CallbackTaskPublisher`，在 `completeUpload()` 中发布消息 |
| `FileServiceAutoConfiguration.java` | 注册 executor 相关 Bean |
| `application.yml` | 添加 executor 配置 |

### 7.3 删除/废弃内容

| 位置 | 内容 | 处理方式 |
|------|------|----------|
| `TaskService.executeCallbacks()` | callback 执行逻辑 | 迁移到 `DefaultCallbackChainRunner` |
| `TaskService.validateCallbacks()` | callback 验证 | 保留在 TaskService（创建任务时验证） |
| `TaskService.publishCompletedEvent()` | 事件发布 | 迁移到 `DefaultCallbackChainRunner` |
| `TaskService.publishFailedEvent()` | 事件发布 | 迁移到 `DefaultCallbackChainRunner` |
| `TaskService.extractPluginOutputs()` | 输出提取 | 迁移到 `DefaultCallbackChainRunner` |

## 八、设计决策

### 8.1 关键决策点

| 决策点 | 选择 | 理由 |
|--------|------|------|
| **分布式队列** | Kafka | 持久化、负载均衡、原生重试支持、生态成熟 |
| **消费模式** | 手动 ack | 精确控制消息确认时机，避免消息丢失 |
| **幂等实现** | Redis SET NX | 简单高效，支持 TTL 自动过期 |
| **状态持久化** | 每个 callback 完成后持久化 | 支持断点恢复，代价是 DB 写入 |
| **超时实现** | `Future.get(timeout)` + 中断 | 标准做法，需插件支持中断 |
| **返回值设计** | 复用 `PluginResult` + 异常 | 避免重复定义，层次清晰 |
| **重试策略** | 指数退避 | 避免瞬时故障时的重试风暴 |

### 8.2 与单机方案对比

| 维度 | 单机线程池 | Kafka 分布式 |
|------|------------|--------------|
| **负载均衡** | 单节点 | 多节点自动分担 |
| **持久化** | 内存队列，重启丢失 | Kafka 持久化 |
| **重试** | 需自己实现 | Kafka + 应用层重试 |
| **背压** | 阻塞或拒绝 | 消费者按能力拉取 |
| **扩容** | 增加线程（有限） | 增加节点（水平扩展） |
| **复杂度** | 低 | 中等（引入 Kafka） |
| **运维成本** | 低 | 中等（Kafka 集群） |

### 8.3 后续扩展点

1. **优先级队列**：使用多个 topic 实现优先级调度
2. **延迟消息**：集成 Kafka 延迟队列或 Redis 延迟队列
3. **监控告警**：集成 Prometheus metrics，Grafana 面板
4. **管理接口**：提供暂停/恢复消费、查询队列深度等 API
5. **分布式追踪**：集成 Sleuth/Zipkin 追踪 callback 执行链路






