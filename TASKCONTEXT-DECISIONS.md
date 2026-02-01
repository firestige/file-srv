# TaskContext 数据管理与集成测试决策文档

> **创建时间**：2026-02-01  
> **状态**：进行中  
> **目的**：记录 TaskContext 元数据注入、E2E 测试、持久化策略等关键决策

---

## 背景与目标

**问题起源**：E2E 测试执行时发现以下 4 个问题

1. **TaskContext 中缺少文件元数据**：创建任务时虽然提供了 filename，但 TaskContext 打印和 GET 请求返回都没有 fileName
2. **插件输出与预期不符**：插件读取的参数和输出结果与预期不一致
3. **缺少元数据注入机制**：期望把文件元数据注入 TaskContext，减少重复查询文件服务的开销
4. **测试用例未正确等待异步完成**：从输出看，测试还未执行完插件链就已经查询并通过验证（时序问题）

**核心诉求**：
- 在 callback 执行时保证 Context 中存在完整的 task 信息和 file 信息
- Plugin 可将更新后的元数据和文件信息写入 Context，便于最后一步统一更新数据库，减少损耗
- 建立清晰的数据管理机制，确保可见性和可维护性
- 分布式场景下保证数据一致性和性能

---

## 决策

### 决策点1：TaskContext 元数据注入机制

**问题描述**：
- Plugin 执行时需要访问完整的 task 信息和 file 信息，避免重复查询数据库
- Plugin 可能修改文件元数据（如 filename、contentType），需要写回 Context
- 当前缺少清晰的注入机制和时机定义

**采纳决策**：
- **分阶段注入**：在不同生命周期阶段注入对应的元数据
- **Plugin 写回机制**：Plugin 修改的元数据统一写入 Context，最后一步批量更新 DB

**注入阶段定义**：

| 阶段 | 触发时机 | 注入内容 | 方法签名 |
|------|---------|---------|---------|
| **任务创建** | `TaskAggregate.create()` | task 基础信息 + file 初始元数据 | `injectTaskCreationMetadata()` |
| **上传完成** | `completeUpload()` | 存储路径 + 实际 hash | `injectUploadCompletionData()` |
| **Plugin 执行** | Plugin.apply() | Plugin 特定输出 | Plugin 自行调用 `context.put()` |

**核心设计原则**（已确认）：

1. **Context 定位**：
   - TaskContext 是**任务执行上下文**，专门用于 Executor 执行 plugin 时存取自定义数据
   - **不是**系统的统一入口，Task 聚合根字段仍然是业务逻辑的权威状态
   - 职责：Plugin 数据隔离层 + 输入输出交换介质

2. **注入策略**：
   - 采用**懒加载**：只在需要时注入，避免过早优化
   - **最佳时机**：`completeUpload()` 时一次性注入完整数据
   - **不在 create() 时注入**：上传过程不需要 Context

3. **上传过程与 Context 的关系**：
   - `uploadPart()` **不访问** Context，直接操作 Task 聚合根字段
   - 分片信息对 plugin 不可见（plugin 只需要知道最终的完整文件信息）

**注入时机最终方案**：

| 生命周期阶段 | 是否注入 Context | 原因 |
|------------|----------------|------|
| `create()` | ❌ 不注入 | 上传过程不需要 Context |
| `uploadPart()` | ❌ 不访问 | 直接操作 Task 字段，分片信息与 plugin 无关 |
| `completeUpload()` | ✅ **一次性注入** | Callback 执行前准备，注入完整 task + file 信息 |
| `plugin.apply()` | ✅ 读写访问 | Plugin 读取输入、写入输出 |

**必须注入的字段**（P0 优先级）：

**Task 信息**：
- `task.id`：任务唯一标识（必需）
- `task.status`：当前状态（可选，便于调试）

**File 信息**（核心）：
- `file.fkey`：文件唯一标识（必需，由 File 域生成）
- `file.name`：原始文件名（必需）
- `file.type`：MIME 类型（必需）
- `file.size`：文件大小字节数（必需）
- `file.path`：存储路径（必需）
- `file.etag`：文件校验和/ETag（必需）

**Delivery 文件信息**（Plugin 写入）：
- `delivery.{fkey}.type`：衍生文件类型（如 THUMBNAIL、TRANSCODE）
- `delivery.{fkey}.path`：存储路径
- `delivery.{fkey}.contentType`：MIME 类型
- `delivery.{fkey}.size`：文件大小
- `delivery.{fkey}.refKeys`：关联的 fKey 列表（逗号分隔，TODO）

**不注入的字段**：
- ❌ 分片信息（`partCount`、`partETags`）：plugin 不需要知道
- ❌ 会话信息（`sessionId`、`uploadId`）：属于上传协议层，与 plugin 无关
- ❌ File 聚合根的完整元数据（owner、audit、access、tags）：由 File 域管理，Task 域只需基础信息

**Plugin 写回机制**（已确认）：

1. **文件池模型**：
   - `delivery` 列表 = callback 执行过程中产生的**所有文件**（文件池）
   - `file.*` 字段 = plugin **主动选择**的最终交付文件（从文件池中选）
   - Plugin 可创建新文件、选择主文件、清理中间文件

2. **fKey 管理**：
   - fKey 由 **File 域生成**（与 Task 域无关）
   - Plugin 创建新文件并上传后获得 fKey
   - 使用 fKey 作为 delivery 的索引（`delivery.{fkey}.*`）

3. **关联关系**（refKeys）：
   - 格式：逗号分隔的字符串（`"fkey1,fkey2,fkey3"`）
   - 用途：标记文件间的关联（如：缩略图关联原图）
   - 状态：**标记为 TODO**（本次不实现，预留字段）

4. **聚合根边界**：
   - **File 聚合根**：跟踪文件生命周期，可长期存在
   - **Task 聚合根**：跟踪任务执行，完成后超过归档期即删除
   - 两者独立管理，Task 通过 fKey 引用 File

5. **大文件处理**：
   - Plugin 创建大文件（如 10GB 视频转码）时需要分片上传
   - 系统需提供 **Plugin Storage API**：
     * `storageService.uploadLargeFile(file)` → 自动分片上传 → 返回 fKey
     * `storageService.downloadFile(fKey)` → 下载文件供 plugin 处理
     * `storageService.deleteFile(fKey)` → 清理中间文件

**待明确问题**（后续讨论）：

1. **数据库更新策略**：
   - 当前：每个 plugin 执行后立即 save()（保证一致性）
   - 优化：所有 plugin 执行完批量 save()（减少 IO）
   - 如何处理 plugin 失败？保留中间状态还是回滚？

2. **时间戳字段**（P1 优先级）：
   - 是否注入 `task.createdAt`、`task.updatedAt`？
   - Plugin 是否需要知道任务创建时间（例如：清理过期临时文件）？

---

### 决策点2：TaskAggregate.buildParams() Bug 修复

**问题描述**：
- `buildParams()` 方法创建了空 Map，但未遍历 `cfg.params()` 填充数据
- 导致插件无法读取 callback 配置的参数

**采纳决策**：
- 遍历 `cfg.params()` 填充 callback 参数到 Map

**实现方式**：
```java
// 修复前（错误）
Map<String, String> param = new HashMap<>();  // 空 Map！

// 修复后（正确）
Map<String, String> param = new HashMap<>();
for (var p : cfg.params()) {
    param.put(p.key(), p.value());
}
```

---

### 决策点3：E2E 测试异步等待策略

**问题描述**：
- 当前测试直接调用 `callbackRunner.run()` 绕过了事件传递机制
- 无法验证 Kafka 消息发布 → 消费 → 执行的完整链路
- 集成测试不应 hack 内部实现，应通过外部可观测状态验证

**采纳决策**：
- **使用 Awaitility 轮询等待任务状态变为 COMPLETED**
- **不直接调用 `callbackRunner.run()`，让消息队列自然触发**

**实现方式**：

```java
import static org.awaitility.Awaitility.*;
import static java.util.concurrent.TimeUnit.*;

@Test
void shouldExecuteCompleteCallbackChainE2E() throws Exception {
    // ... 前置步骤：创建任务、上传分片、完成上传
    
    // 等待 callback 链执行完成（通过轮询状态）
    await()
        .atMost(10, SECONDS)         // 最大等待 10 秒
        .pollInterval(1, SECONDS)     // 每 1 秒轮询一次
        .until(() -> {
            String response = mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andReturn()
                .getResponse()
                .getContentAsString();
            String status = JsonPath.read(response, "$.data.status");
            return "COMPLETED".equals(status);
        });
    
    // 验证最终结果
    TaskAggregate task = taskRepository.findByTaskId(taskId).orElseThrow();
    assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    // ... 验证 TaskContext 中的插件输出
}
```

**配置参数**：
- **超时时间**：10 秒（测试数据简单，插件执行快）
- **轮询间隔**：1 秒（平衡响应速度和 CPU 开销）
- **验证逻辑**：通过 HTTP GET 请求解析 JSON 响应中的 `status` 字段

**架构问题识别** ⚠️：

**问题：测试环境无法验证消息发布-订阅流程**

**现状分析**：
- 当前使用 `CallbackTaskPublisherStub` 只记录 taskId，**不触发执行**
- 测试需要手动调用 `callbackRunner.run(task)`，**绕过了消息机制**
- 无法验证完整的异步流程：发布 → 消费 → 执行

**根本原因**：
- `CallbackTaskPublisher` 接口过度耦合 Kafka（注释写死 "发布到 Kafka"）
- Consumer 使用 `@KafkaListener`，无法在测试环境启用
- 缺少基于 Spring Event 的实现（本地消息总线）

**架构缺陷**：
```
❌ 当前架构：过度绑定 Kafka，测试绕过消息机制
TaskService → CallbackTaskPublisher (Kafka 耦合)
                ↓
              KafkaCallbackTaskPublisher → Kafka → @KafkaListener
              CallbackTaskPublisherStub → (只记录，不触发)

✅ 应有架构：适配器模式，支持多种 MQ 和测试
TaskService → CallbackTaskPublisher (纯抽象)
                ↓
              ├─ SpringEventCallbackPublisher → ApplicationEventPublisher
              │     ↓ @EventListener (测试环境自动触发)
              │   CallbackChainRunner
              │
              └─ KafkaCallbackPublisher → KafkaTemplate
                    ↓ @KafkaListener (生产环境)
                  CallbackChainRunner
```

**影响**：
- ❌ 测试无法验证消息订阅-消费流程
- ❌ 换成 RocketMQ/RabbitMQ 需要大量改动
- ❌ 违反依赖倒置原则（DIP）

**解决方案**（见 P0 清单）：
- 新增 `SpringEventCallbackPublisher` 实现（测试环境）
- 使用 `@Profile` 隔离环境
- 修改测试用例，移除手动调用，验证自动触发

---

**超时控制机制已确认** ✅：

经过代码分析，`DefaultCallbackChainRunner` **已完整实现**超时控制机制：

1. **单个 Callback 超时**（第170行）：
   ```java
   Duration callbackTimeout = properties.timeout().callback();  // 默认 5 分钟
   Future<PluginResult> future = timeoutExecutor.submit(() -> plugin.apply(context));
   PluginResult result = future.get(callbackTimeout.toMillis(), TimeUnit.MILLISECONDS);
   ```
   - 使用 `ExecutorService` + `Future.get(timeout)` 实现超时终止
   - 超时后抛出 `TimeoutException`，触发本地重试机制
   - 重试次数**可配置**，默认 3 次，重试耗尽后抛出 `CallbackTimeoutException`

2. **超时配置项**（`ExecutorProperties.TimeoutConfig`）：
   ```yaml
   file-service:
     executor:
       timeout:
         callback: 5m        # 单个 callback 超时（默认 5 分钟）
         chain: 30m          # 整个链超时（预留，默认 30 分钟）
         task-deadline: 1h   # 任务最大等待时间（默认 1 小时）
   ```

3. **重试配置项**（`ExecutorProperties.RetryConfig`） - **完全可配置**：
   ```yaml
   file-service:
     executor:
       retry:
         max-retries-per-callback: 3    # 最大重试次数（默认 3）
         backoff: 1s                    # 首次退避时间（默认 1 秒）
         backoff-multiplier: 2.0        # 退避乘数（默认 2.0，指数退避）
         max-backoff: 1m                # 最大退避时间（默认 1 分钟）
   ```
   **退避策略示例**（默认配置）：
   - 第 1 次重试：等待 1 秒（1s × 2^0）
   - 第 2 次重试：等待 2 秒（1s × 2^1）
   - 第 3 次重试：等待 4 秒（1s × 2^2）
   - 如果配置 `backoff-multiplier: 1.5`：1s → 1.5s → 2.25s → 3.375s

4. **可重试异常判断**（第160-220行）：
   - ✅ **可重试**：`IOException`、`SocketTimeoutException`、`ConnectException`、`TimeoutException`
   - ✅ **可重试**：Plugin 返回 `PluginResult.Failure(retryable=true)`
   - ❌ **不可重试**：`IllegalArgumentException`、`SecurityException`、`NullPointerException`

5. **幂等性保证**：
   - 每个 callback 执行后立即持久化进度（`task.advanceCallback() → save()`）
   - 支持断点恢复（从 `task.currentCallbackIndex` 继续）

**生产环境配置建议**：
```yaml
file-service:
  executor:
    timeout:
      callback: 10m       # 图像处理可能需要更长时间
    retry:
      max-retries-per-callback: 5    # 生产环境建议更多重试
      backoff: 2s                    # 首次退避 2 秒
      backoff-multiplier: 1.5        # 温和的指数增长
      max-backoff: 2m                # 最大等待 2 分钟
```

**结论**：
- ✅ **无需额外实现**：超时控制机制完善，符合生产环境要求
- ✅ **完全可配置**：超时时间、重试次数、退避策略均可通过 YAML 调整
- ✅ **监控友好**：超时日志清晰，抛出专用异常 `CallbackTimeoutException`
- ✅ **灵活可调**：可根据业务特点（轻量级 vs 重计算）调整参数

**Action Item**：
- [ ] ~~确认当前 `CallbackChainRunner` 是否实现了超时控制~~ ✅ 已确认完整实现
- [x] ~~如果没有，添加 `@Timeout` 或 `CompletableFuture.orTimeout()` 保护~~ ✅ 无需额外实现
- [ ] 修改 E2E 测试用例，移除直接调用 `run()`，改用 Awaitility 轮询

---

### 决策点4：TaskContext 数据管理方案（注解驱动 + 编译时优化）

**问题描述**：
- 数据注入分散在多处，缺乏统一管理和可见性
- 没有契约约束，容易出现 key 拼写错误、漏注入等问题
- 运行时反射读取注解有性能开销

**采纳决策**：
- **方案 3（分阶段 Populator）+ 方案 4（注解验证）+ 编译时代码生成**
- 用注解描述数据来源（自文档化）
- 用 Populator 接口实现注入（职责清晰）
- 用编译时注解处理器生成注入代码（避免反射，类似 Lombok）

**核心设计**：

1. **定义 `@ContextKey` 注解**

```java
@Retention(RetentionPolicy.SOURCE)  // 编译时处理，运行时不保留
@Target(ElementType.FIELD)
public @interface ContextKey {
    Stage stage();           // 注入阶段
    boolean required() default true;  // 是否必需
    String description() default "";  // 字段说明
}

public enum Stage {
    TASK_CREATION,      // 任务创建时注入
    UPLOAD_COMPLETE,    // 上传完成时注入
    PLUGIN_EXECUTION    // 插件执行时注入
}
```

2. **在常量类中标注注解**

```java
public class TaskContextKeys {
    // 任务创建阶段注入
    @ContextKey(stage = Stage.TASK_CREATION, description = "任务唯一标识")
    public static final String TASK_ID = "task.id";
    
    @ContextKey(stage = Stage.TASK_CREATION, description = "原始文件名")
    public static final String FILE_NAME = "file.name";
    
    @ContextKey(stage = Stage.TASK_CREATION, description = "文件 MIME 类型")
    public static final String FILE_CONTENT_TYPE = "file.contentType";
    
    @ContextKey(stage = Stage.TASK_CREATION, description = "文件大小（字节）")
    public static final String FILE_SIZE = "file.size";
    
    @ContextKey(stage = Stage.TASK_CREATION, description = "客户端计算的文件 hash")
    public static final String FILE_CONTENT_HASH = "file.contentHash";
    
    // 上传完成阶段注入
    @ContextKey(stage = Stage.UPLOAD_COMPLETE, description = "对象存储路径")
    public static final String FILE_STORAGE_PATH = "file.storagePath";
    
    @ContextKey(stage = Stage.UPLOAD_COMPLETE, description = "服务端计算的文件 hash")
    public static final String FILE_SERVER_HASH = "file.serverHash";
    
    // 插件执行阶段注入（动态 key，不预定义）
    // Plugin 自行通过 context.put("plugin-name.output-key", value) 写入
}
```

3. **编译时注解处理器自动生成 Populator**

```java
// 编译时自动生成 TaskCreationContextPopulator.java
@Generated("tech.icc.filesrv.apt.ContextKeyProcessor")
public class TaskCreationContextPopulator {
    
    public static void populate(TaskContext context, TaskAggregate task) {
        // 自动生成的静态注入代码（无反射）
        context.put(TaskContextKeys.TASK_ID, task.getTaskId());
        context.put(TaskContextKeys.FILE_NAME, task.getFileName());
        context.put(TaskContextKeys.FILE_CONTENT_TYPE, task.getContentType());
        context.put(TaskContextKeys.FILE_SIZE, String.valueOf(task.getSize()));
        context.put(TaskContextKeys.FILE_CONTENT_HASH, task.getContentHash());
    }
    
    public static void validate(TaskContext context) {
        // 自动生成的验证代码
        if (!context.containsKey(TaskContextKeys.TASK_ID)) {
            throw new IllegalStateException("Required key missing: task.id");
        }
        // ... 其他必需字段验证
    }
}
```

4. **在 TaskAggregate 中调用生成的 Populator**

```java
public class TaskAggregate {
    
    public static TaskAggregate create(...) {
        TaskAggregate task = new TaskAggregate();
        // ... 初始化逻辑
        
        // 调用自动生成的 Populator（编译时生成，无反射）
        TaskCreationContextPopulator.populate(task.getContext(), task);
        
        return task;
    }
    
    public void completeUpload(...) {
        // ... 完成逻辑
        
        // 调用自动生成的 Populator
        UploadCompleteContextPopulator.populate(this.getContext(), this);
    }
}
```

**方案优势**：

| 特性 | 实现方式 | 收益 |
|------|---------|------|
| **自文档化** | `@ContextKey` 注解 + description | 一眼看出所有 key 及其用途 |
| **编译时检查** | 注解处理器生成代码 | 拼写错误在编译期报错 |
| **零反射开销** | 生成静态方法 | 性能等同手写代码 |
| **职责清晰** | 按阶段生成 Populator | 注入逻辑集中管理 |
| **类型安全** | 生成的代码类型明确 | 避免运行时类型转换错误 |

**实施计划**：

- **Phase 1**（本次实现）：手动编写 Populator（快速验证）
  ```java
  // 手动实现，暂不生成
  public class TaskContextPopulators {
      public static void populateTaskCreation(TaskContext ctx, TaskAggregate task) { ... }
      public static void populateUploadComplete(TaskContext ctx, TaskAggregate task) { ... }
  }
  ```

- **Phase 2**（迭代优化）：实现注解处理器
  - 创建 `ContextKeyProcessor extends AbstractProcessor`
  - 扫描 `@ContextKey` 注解
  - 使用 JavaPoet 生成 Populator 代码
  - 配置 `META-INF/services/javax.annotation.processing.Processor`

- **Phase 3**（长期优化）：增强诊断功能
  - 生成 `getAvailableKeys()` 方法
  - 生成 `getDiagnosticInfo()` 方法
  - 生成 Markdown 文档（自动更新）

**参考实现**：
- Lombok 的注解处理器架构
- MapStruct 的代码生成策略
- Spring Boot 的配置属性处理器

---

### 决策点5：分布式场景下 TaskContext 持久化策略

**问题描述**：
- 分片上传在多节点执行（create 在节点 A，complete 在节点 D，callback 在节点 E）
- 如何保证 callback 执行时能拿到完整的 TaskContext 数据？

**采纳决策**：
- **DB 为主（权威数据源）+ Redis 为辅（缓存层）**

**实现方式**：

1. **写入路径**：
   ```
   修改 TaskContext → save() 到 DB（事务保证）→ 成功后更新 Redis
   ```

2. **读取路径**：
   ```
   先查 Redis → cache miss 则降级查 DB → 查到后回填 Redis
   ```

3. **为什么不能只用 Redis**：
   - TaskContext 与 TaskAggregate 是聚合根整体，需要事务保证一致性
   - 需要长期存储（审计、溯源）
   - 需要持久化保证（不能因 Redis 重启丢失数据）
   - DB 提供 ACID 保证，Redis 仅作性能优化

4. **关键保证**：
   - 每次注入元数据后都调用 `taskRepository.save()` 持久化
   - 下次从 DB 加载时获取完整数据（跨节点可见）

---

### 决策点6：并发控制策略

**问题描述**：
- 多节点并发修改 TaskContext 时如何防止数据覆盖？

**采纳决策**：
- 使用 `@Version` 字段实现乐观锁（Spring Data JPA 原生支持）

**实现方式**：
```java
@Entity
public class TaskAggregate {
    @Version
    private Integer version;  // JPA 自动管理版本号
    // ... 其他字段
}
```

**冲突处理**：
- 并发修改时抛出 `OptimisticLockException`
- TaskService 捕获异常并重试（重新加载最新数据）

---

### 决策点7：TaskContext 变更可观测性

**问题描述**：
- 如何追踪 TaskContext 的变更历史？便于调试和审计

**采纳决策**：
- 使用 AOP 切面自动记录 before/after diff

**实现方式**：
```java
@Aspect
@Component
public class TaskContextLoggingAspect {
    
    @Around("execution(* tech.icc.filesrv..TaskAggregate.*(..))")
    public Object logContextChanges(ProceedingJoinPoint pjp) throws Throwable {
        TaskAggregate task = getTaskFromArgs(pjp);
        Map<String, String> before = task.getContext().getData();
        
        Object result = pjp.proceed();
        
        Map<String, String> after = task.getContext().getData();
        logDiff(before, after, pjp.getSignature().getName());
        
        return result;
    }
}
```

---

### 决策点8：FileRelations 双向关系设计

**问题描述**：
- refKeys 存储位置？单向还是双向关系？
- 主文件切换时如何处理关联关系？
- 如何防止孤儿文件产生（资源泄露监控成本很高）？

**方案对比**：

| 方案 | 关系模型 | 优点 | 缺点 | 孤儿风险 |
|------|---------|------|------|---------|
| A. 单向子→父 | 只在衍生文件存refKeys | 维护简单 | 查询需索引 | ⚠️ 主文件切换产生孤儿 |
| B. 双向父↔子 | 父存derivedKeys，子存mainKey | 查询高效 | 级联更新复杂 | ⚠️ 级联失败产生孤儿 |
| **C. 双重引用** | **sourceKey + currentMainKey + derivedKeys** | **避免孤儿，历史清晰** | 需关联表 | ✅ **无孤儿风险** |

**最终决策**：方案C - 双重引用设计（避免生产环境资源泄露）

**核心设计**：
```java
public class FileRelations {
    private String sourceKey;        // 直接来源（生成时的父文件，永不改变）
    private String currentMainKey;   // 当前主文件（跟随切换）
    private List<String> derivedKeys; // 衍生文件列表（如果是主文件）
    
    public static FileRelations empty() {
        return new FileRelations(null, null, null);
    }
    
    public static FileRelations fromSource(String sourceKey) {
        return new FileRelations(sourceKey, sourceKey, null);
    }
}
```

**MySQL 索引方案**：关联表（最灵活，推荐）
```sql
CREATE TABLE file_relations (
    file_fkey VARCHAR(64) NOT NULL COMMENT '文件 fKey',
    related_fkey VARCHAR(64) NOT NULL COMMENT '关联文件 fKey',
    relation_type ENUM('source', 'current_main', 'derived') NOT NULL COMMENT '关系类型',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (file_fkey, related_fkey, relation_type),
    INDEX idx_related (related_fkey, relation_type),
    INDEX idx_created (created_at)  -- 用于孤儿清理
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件关联关系表';

-- 查询文件的所有衍生文件
SELECT related_fkey FROM file_relations 
WHERE file_fkey = ? AND relation_type = 'derived';

-- 查询文件的当前主文件
SELECT related_fkey FROM file_relations 
WHERE file_fkey = ? AND relation_type = 'current_main';

-- 查询孤儿文件（关联文件不存在）
SELECT DISTINCT fr.file_fkey 
FROM file_relations fr
LEFT JOIN file_metadata fm ON fr.related_fkey = fm.fkey
WHERE fm.fkey IS NULL 
  AND fr.created_at < DATE_SUB(NOW(), INTERVAL ? DAY);
```

**主文件切换策略**（避免孤儿产生）：
- `sourceKey`：**永远不变**（保持历史真实性，记录生成时的父文件）
- `currentMainKey`：**跟随切换**（指向当前主文件，便于快速查询）
- `derivedKeys`：**主文件负责维护**（双向同步，保证关系完整性）

**示例场景**：
```
初始状态：
  原图(fkey1) → 生成缩略图(fkey2)
  fkey2.relations: sourceKey=fkey1, currentMainKey=fkey1

主文件切换：
  Plugin 将缩略图(fkey2) 设为主文件
  fkey2.relations: sourceKey=fkey1 (不变), currentMainKey=fkey2 (更新)
  fkey1.relations: derivedKeys=[fkey2] (更新)

孤儿检测：
  遍历所有 file_relations
  检查 sourceKey 和 currentMainKey 指向的文件是否存在
  如果不存在且超过宽限期 → 标记为孤儿
```

**孤儿文件清理**（防止资源泄露）：
- **触发时机**：定时任务（每天凌晨3点）
- **孤儿定义**：sourceKey 或 currentMainKey 指向不存在的文件
- **宽限期**：**可配置**（默认 7 天）
- **配置项**：
  ```yaml
  file:
    orphan:
      retention-days: 7          # 孤儿文件宽限期（天）
      cleanup-cron: "0 0 3 * * ?"  # 每天凌晨3点执行
      enabled: true              # 是否启用自动清理
  ```
- **清理逻辑**：
  1. 查询关联关系表，找到所有关联文件不存在的记录
  2. 过滤：`created_at < now() - retention_days`
  3. 记录日志：孤儿文件信息（便于审计）
  4. 调用 File 域的删除服务（物理删除 + 元数据清理）

**维护策略**：**约定大于配置**（系统默认行为 + Plugin 可覆盖）

1. **系统默认行为**（Executor 自动执行）：
   - Plugin 创建衍生文件时，**自动设置** sourceKey 和 currentMainKey
   - Plugin 修改主文件时，**自动更新**双向关系（主文件的 derivedKeys + 衍生文件的 currentMainKey）
   - 关联表操作封装在 Repository，保证事务一致性

2. **Plugin 可覆盖**：
   - 通过 `customMetadata` 修改 `relations` 字段
   - 系统检测到 Plugin 覆盖时，采用 Plugin 的值（优先级更高）
   - 适用场景：复杂关联关系（如多级衍生、合并文件）

3. **实现保证**：
   - P0 必须完整实现双向关系（**不能只标记 TODO**）
   - 事务保证：关联关系更新与 Task 状态更新在同一事务
   - 幂等性：重复执行不会产生重复记录

**实现要点**：
- P0：完整实现双向关系（必须）
- P0：实现关联表的 CRUD 操作（必须）
- P0：DerivedFile 添加 fKey 和 relations 字段（必须）
- P0：FileInfoResponse 添加 relations 字段（必须）
- P1：实现孤儿清理定时任务
- P1：添加配置项 `file.orphan.retention-days`
- P1：添加监控指标（孤儿文件数量、清理成功/失败次数）

---

## 最终方案

### 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                      TaskAggregate                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │            TaskContext (聚合根一部分)                │   │
│  │  ┌─────────────────────────────────────────────┐   │   │
│  │  │  Map<String, String> data                   │   │   │
│  │  │  ├─ task.id                                 │   │   │
│  │  │  ├─ file.name                               │   │   │
│  │  │  ├─ file.contentType                        │   │   │
│  │  │  ├─ file.storagePath                        │   │   │
│  │  │  ├─ plugin.xxx.output                       │   │   │
│  │  │  └─ ...                                     │   │   │
│  │  └─────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ save()
                           ↓
        ┌──────────────────────────────────────┐
        │     PostgreSQL (权威数据源)          │
        │  ├─ ACID 事务保证                     │
        │  ├─ 长期存储 + 审计追溯               │
        │  └─ 跨节点数据一致性                  │
        └──────────────────────────────────────┘
                           │
                           │ cache update
                           ↓
        ┌──────────────────────────────────────┐
        │        Redis (缓存层)                │
        │  ├─ 热数据加速（TTL 24h)              │
        │  ├─ cache miss 降级 DB                │
        │  └─ 性能优化不影响正确性              │
        └──────────────────────────────────────┘
```

### 数据流转

**1. 任务创建（节点 A）**
```
POST /api/v1/files/upload_task
  → TaskService.createTask()
  → TaskAggregate.create()
    ├─ 初始化聚合根字段（taskId、filename、size、status=PENDING）
    └─ ❌ 不注入 Context（懒加载策略）
  → taskRepository.save()  // 持久化到 DB
  → redis.set(taskId, task)  // 缓存到 Redis
```

**2. 上传完成（节点 D）**
```
POST /api/v1/files/upload_task/{id}/complete
  → TaskService.completeUpload()
  → task = taskRepository.findByTaskId(id)  // 从 DB 加载完整聚合根
  → task.completeUpload()
    ├─ 业务逻辑：计算 storagePath、etag、更新状态
    └─ populateContextForPlugins()  // 懒加载：一次性注入完整数据
        ├─ 从 File 域获取 fKey（通过存储服务）
        ├─ context.put("task.id", taskId)
        ├─ context.put("file.fkey", fKey)
        ├─ context.put("file.name", filename)
        ├─ context.put("file.type", contentType)
        ├─ context.put("file.size", size)
        ├─ context.put("file.path", storagePath)
        ├─ context.put("file.etag", etag)
        └─ 原始文件加入 delivery（可选）
            ├─ context.put("delivery." + fKey + ".type", "ORIGINAL")
            ├─ context.put("delivery." + fKey + ".path", storagePath)
            └─ ...
  → taskRepository.save()  // 持久化到 DB
  → redis.set(taskId, task)  // 更新 Redis 缓存
  → callbackPublisher.publish(taskId)  // 发布 Kafka 消息
```

**3. Plugin 执行（节点 E）**
```
Kafka Consumer 接收消息
  → CallbackChainRunner.run(taskId)
  → task = taskRepository.findByTaskId(id)  // 从 DB 加载完整数据
  → for each plugin:
      plugin.apply(context, pluginStorageAPI)
        ├─ 读取输入：context.getString("file.fkey")
        ├─ 处理文件：下载、转换、生成衍生文件
        ├─ 上传新文件：pluginStorageAPI.uploadLargeFile() → 获得 fKey
        ├─ 写入 delivery：
        │   ├─ context.put("delivery.{fkey}.type", "THUMBNAIL")
        │   ├─ context.put("delivery.{fkey}.path", path)
        │   ├─ context.put("delivery.{fkey}.contentType", type)
        │   ├─ context.put("delivery.{fkey}.size", size)
        │   └─ context.put("delivery.{fkey}.refKeys", "原fkey")  // TODO
        └─ 可选：切换主文件
            ├─ context.put("file.fkey", newFkey)
            ├─ context.put("file.name", newName)
            └─ ...
  → taskRepository.save()  // 保存 plugin 输出到 DB
  → task.markAsCompleted()
  → taskRepository.save()  // 最终状态持久化
```

**Plugin Storage API**（待实现）：
```java
public interface PluginStorageService {
    // 大文件上传（自动分片）
    String uploadLargeFile(File file) throws StorageException;
    
    // 下载文件
    File downloadFile(String fKey) throws StorageException;
    
    // 删除文件
    void deleteFile(String fKey) throws StorageException;
    
    // 获取临时下载 URL（避免下载到本地）
    String getTemporaryUrl(String fKey, Duration expiry) throws StorageException;
}
```

### 并发控制

**乐观锁机制**：
```java
@Entity
public class TaskAggregate {
    @Version
    private Integer version;  // 每次 save() 自动递增
}

// 并发修改时
Node A: load(version=1) → modify → save(version=2) → ✅ 成功
Node B: load(version=1) → modify → save(version=2) → ❌ OptimisticLockException

// 重试逻辑
@Transactional
public void updateTask(String taskId) {
    int maxRetries = 3;
    for (int i = 0; i < maxRetries; i++) {
        try {
            TaskAggregate task = taskRepository.findByTaskId(taskId);
            // 修改 task
            taskRepository.save(task);
            return;  // 成功
        } catch (OptimisticLockException e) {
            if (i == maxRetries - 1) throw e;
            // 重试前短暂等待
            Thread.sleep(100 * (i + 1));
        }
    }
}
```

### 可观测性

**AOP 切面自动记录变更**：
```java
@Aspect
@Slf4j
public class TaskContextLoggingAspect {
    
    @Around("execution(* tech.icc.filesrv..TaskAggregate.*(..)) && " +
            "@annotation(tech.icc.filesrv.common.annotation.TrackContextChange)")
    public Object logContextChanges(ProceedingJoinPoint pjp) throws Throwable {
        TaskAggregate task = extractTask(pjp);
        Map<String, String> before = new HashMap<>(task.getContext().getData());
        
        Object result = pjp.proceed();
        
        Map<String, String> after = task.getContext().getData();
        Map<String, String> diff = calculateDiff(before, after);
        
        if (!diff.isEmpty()) {
            log.info("TaskContext changed in {}.{}(): {}",
                pjp.getSignature().getDeclaringType().getSimpleName(),
                pjp.getSignature().getName(),
                diff);
        }
        
        return result;
    }
}
```

---

## 待实施清单

### P0 - 立即实施（本次 PR）

#### Context 注入机制

- [ ] **修复 `buildParams()` bug**
  - 文件：`TaskAggregate.java`
  - 修改：遍历 `cfg.params()` 填充 Map

- [ ] **扩展 `create()` 方法签名**
  - 文件：`TaskAggregate.java`
  - 修改：添加 filename、contentType、size 参数到聚合根字段
  - ❌ **不调用** Context 注入方法（采用懒加载）

- [ ] **在 `completeUpload()` 中一次性注入元数据**
  - 文件：`TaskAggregate.java`
  - 实现：`populateContextForPlugins()` 私有方法
  - 注入：task.id、file.fkey、file.name、file.type、file.size、file.path、file.etag
  - fKey 来源：✅ 已确认 - TaskAggregate 已有 fKey 字段（创建任务时由 TaskService 生成并传入）

- [ ] **修改 `TaskService.createTask()`**
  - 文件：`TaskService.java`
  - 修改：传递 cmd.filename()、cmd.contentType()、cmd.size()

- [ ] **修改 E2E 测试用例**
  - 文件：`PluginCallbackScenarioTest.java`
  - 移除：直接调用 `callbackRunner.run()`
  - 添加：Awaitility 轮询等待 COMPLETED（10 秒超时，1 秒间隔）

- [ ] **验证测试通过**
  - 运行：`shouldExecuteCompleteCallbackChainE2E()`
  - 确认：filename 不为 null、插件参数正确、状态流转正确

#### 消息发布-订阅架构重构（P0 必须完成）

- [ ] **创建 CallbackTaskEvent 领域事件**
  - 文件：`CallbackTaskEvent.java`（新建）
  - 位置：`file-srv-core/domain/events/`
  - 字段：`taskId`, `messageId`, `deadline`

- [ ] **创建 SpringEventCallbackPublisher**
  - 文件：`SpringEventCallbackPublisher.java`（新建）
  - 位置：`file-srv-core/infra/executor/impl/`
  - 实现：`CallbackTaskPublisher` 接口
  - 注解：`@Component`, `@Profile("test")` 或 `@Profile("!prod")`
  - 逻辑：使用 `ApplicationEventPublisher.publishEvent()`

- [ ] **创建 CallbackTaskEventListener**
  - 文件：`CallbackTaskEventListener.java`（新建）
  - 位置：`file-srv-core/infra/executor/impl/`
  - 注解：`@Component`, `@EventListener`, `@Async`（异步执行）
  - 逻辑：
    ```java
    @EventListener
    @Async
    public void onCallbackTask(CallbackTaskEvent event) {
        TaskAggregate task = taskRepository.findByTaskId(event.taskId());
        chainRunner.run(task);
    }
    ```

- [ ] **KafkaCallbackTaskPublisher 添加 Profile**
  - 文件：`KafkaCallbackTaskPublisher.java`
  - 添加：`@Profile("!test")` 或 `@Profile("prod")`
  - 确保测试环境不启用 Kafka

- [ ] **KafkaCallbackTaskConsumer 添加 Profile**
  - 文件：`KafkaCallbackTaskConsumer.java`
  - 添加：`@Profile("!test")` 或 `@Profile("prod")`
  - 确保测试环境不启用 Kafka Consumer

- [ ] **更新 CallbackTaskPublisher 接口注释**
  - 文件：`CallbackTaskPublisher.java`
  - 移除：Kafka 相关描述
  - 改为：通用的任务调度描述（支持 Kafka/Spring Event/RocketMQ 等）

- [ ] **配置异步执行线程池**
  - 文件：`application-test.yml` 或配置类
  - 配置：
    ```yaml
    spring:
      task:
        execution:
          pool:
            core-size: 4
            max-size: 8
    ```

- [ ] **修改 E2E 测试用例**
  - 文件：`PluginCallbackScenarioTest.java`
  - **移除**：手动调用 `callbackRunner.run(task)`
  - **改为**：Awaitility 轮询等待 COMPLETED（验证自动触发）
  - 验证：`callbackPublisher.isPublished(taskId)` 仍然为 true

- [ ] **验证测试自动触发**
  - 运行测试，确认不手动调用 `run()` 也能通过
  - 验证消息发布 → 事件监听 → 自动执行的完整流程

#### FileRelations 双向关系（P0 必须完成）

- [ ] **创建 FileRelations.java VO**
  - 文件：`file-srv-common/src/main/java/tech/icc/filesrv/common/vo/file/FileRelations.java`
  - 字段：`sourceKey`（直接来源）、`currentMainKey`（当前主文件）、`derivedKeys`（衍生文件列表）
  - 方法：`empty()` 静态工厂、`fromSource(String)` 工厂方法

- [ ] **修改 DerivedFile.java**
  - 文件：`DerivedFile.java`
  - 添加：`String fKey` 字段（文件唯一标识）
  - 添加：`FileRelations relations` 字段（关联关系）
  - 移除：旧的 TODO 标记（必须完整实现）

- [ ] **修改 FileInfoResponse.java**
  - 文件：`FileInfoResponse.java`
  - 添加：`@JsonUnwrapped FileRelations relations` 字段

- [ ] **创建 file_relations 数据库表**
  - 位置：迁移脚本（Flyway/Liquibase）
  - 结构：见决策点8的 SQL DDL
  - 索引：PRIMARY (file_fkey, related_fkey, relation_type)、idx_related、idx_created

- [ ] **创建 FileRelationRepository.java**
  - 文件：`file-srv-core/.../repository/FileRelationRepository.java`
  - 方法：`save()`、`findByFileKey()`、`findDerivedFiles()`、`findCurrentMain()`、`delete()`

- [ ] **TaskAggregate 自动维护关联关系**
  - 文件：`TaskAggregate.java`
  - 逻辑：Plugin 创建衍生文件时自动设置 sourceKey 和 currentMainKey
  - 逻辑：Plugin 修改主文件时自动更新双向关系
  - 集成：在 plugin.apply() 后检测 delivery 变化，调用 FileRelationRepository

- [ ] **验证双向关系功能**
  - 测试：创建衍生文件后查询关联关系
  - 测试：切换主文件后验证 currentMainKey 更新
  - 测试：验证事务一致性（关联关系 + Task 状态同时提交或回滚）

### P1 - 短期优化（1-2 周内）

#### 生产环境配置优化（P1）

- [ ] **创建生产环境配置文件**
  - 文件：`application-prod.yml`
  - 配置超时参数：
    ```yaml
    file-service:
      executor:
        timeout:
          callback: 10m       # 根据实际 Plugin 处理时间调整
          chain: 30m
          task-deadline: 1h
        retry:
          max-retries-per-callback: 5    # 生产环境更多重试机会
          backoff: 2s                    # 首次退避 2 秒
          backoff-multiplier: 1.5        # 温和的指数增长（1.5 比 2.0 更平滑）
          max-backoff: 2m                # 最大等待时间 2 分钟
    ```

- [ ] **根据业务场景调优参数**
  - 轻量级操作（文本处理、元数据提取）：`callback: 2m, max-retries: 3`
  - 中等操作（图像压缩、格式转换）：`callback: 5m, max-retries: 5`
  - 重量级操作（视频转码、AI推理）：`callback: 30m, max-retries: 3`

- [ ] **添加配置文档**
  - 文件：`docs/configuration-guide.md`
  - 说明各参数含义、默认值、推荐范围
  - 提供不同场景的配置模板

#### 孤儿文件清理（P1）

- [ ] **添加配置项**
  - 文件：`application.yml`
  - 配置：
    ```yaml
    file:
      orphan:
        retention-days: 7          # 孤儿文件宽限期（天）
        cleanup-cron: "0 0 3 * * ?"  # 每天凌晨3点执行
        enabled: true              # 是否启用自动清理
    ```

- [ ] **实现孤儿清理定时任务**
  - 文件：`OrphanFileCleanupTask.java`（新建）
  - 逻辑：
    1. 调用 `FileRelationRepository.findOrphanFiles(retentionDays)`
    2. 记录日志（孤儿文件列表）
    3. 调用 File 域删除服务
  - 注解：`@Scheduled(cron = "${file.orphan.cleanup-cron}")`

- [ ] **FileRelationRepository 添加孤儿查询方法**
  - 方法：`List<String> findOrphanFiles(int retentionDays)`
  - SQL：见决策点8的孤儿查询 SQL

- [ ] **添加监控指标**
  - 指标：孤儿文件数量（Gauge）
  - 指标：清理成功次数（Counter）
  - 指标：清理失败次数（Counter）
  - 集成：Micrometer/Prometheus

#### 其他 P1 任务

- [ ] **实现 `@Version` 乐观锁**
  - 文件：`TaskAggregate.java`
  - 添加：`@Version Integer version` 字段
  - 文件：`TaskService.java`
  - 添加：OptimisticLockException 重试逻辑

- [ ] **实现 AOP 日志切面**
  - 文件：`TaskContextLoggingAspect.java`（新建）
  - 实现：before/after diff 记录

- [ ] **确认 Plugin 超时控制**
  - 检查：`CallbackChainRunner` 是否已实现超时
  - 补充：如果没有，添加 `@Timeout` 或 `CompletableFuture.orTimeout()`

- [ ] **Redis 缓存层实现**
  - 文件：`TaskCacheService.java`（新建）
  - 实现：Write-through + Cache-aside 策略

### P2 - 中期迭代（1 个月内）

- [ ] **定义 `TaskContextKeys` 常量类**
  - 文件：`TaskContextKeys.java`（新建）
  - 添加：所有 key 常量定义（task.*, file.*, delivery.*）
  - 添加：JavaDoc 说明每个 key 的用途

- [ ] **实现 Plugin Storage API**
  - 文件：`PluginStorageService.java`（新建）
  - 实现：uploadLargeFile()、downloadFile()、deleteFile()、getTemporaryUrl()
  - 集成：注入到 CallbackChainRunner，传递给 plugin.apply()

- [ ] **重构测试插件使用 fKey**
  - 文件：TestThumbnailPlugin、TestRenamePlugin 等
  - 修改：从 Context 读取 file.fkey
  - 修改：上传新文件获得 fKey，写入 delivery.{fkey}.*

### P3 - 长期优化（2-3 个月）

- [ ] **实现注解处理器**
  - 创建：`ContextKeyProcessor extends AbstractProcessor`
  - 实现：扫描 `@ContextKey` 注解
  - 实现：使用 JavaPoet 生成 Populator 代码
  - 配置：`META-INF/services/javax.annotation.processing.Processor`

- [ ] **添加 TaskContext 诊断方法**
  - 方法：`getAvailableKeys()` 返回所有已注入的 key
  - 方法：`getDiagnosticInfo()` 返回诊断信息
  - 方法：`getHistory()` 返回操作历史（可选）

- [ ] **事件溯源（可选）**
  - 记录：TaskContext 的每次变更为独立事件
  - 支持：时间旅行、状态重建、完整审计

---

## 附录：关键问题跟踪

| 问题 ID | 问题描述 | 状态 | 负责人 | 备注 |
|---------|---------|------|-------|------|
| Q1 | fKey 如何从 File 域获取？ | ✅ 已解决 | - | TaskAggregate 已有 fKey 字段 |
| Q2 | Plugin 是否已实现超时控制？ | ✅ 已确认 | - | 已完整实现，可配置（决策点3） |
| Q3 | 测试环境消息发布-订阅流程验证 | ✅ 已识别架构问题 | - | 需重构为 Spring Event（P0 必须） |
| Q4 | DerivedFile 添加 refKeys 字段 | ✅ 已决策 | - | 必须完整实现（决策点8） |
| Q5 | Plugin Storage API 设计 | ✅ 已决策 | - | P2 实施，支持大文件分片上传 |
| Q6 | File 和 Task 聚合根生命周期 | ✅ 已明确 | - | 独立管理，Task 通过 fKey 引用 |
| Q7 | refKeys 存储位置和维护策略 | ✅ 已决策 | - | FileRelations VO + 约定大于配置 |
| Q8 | MySQL 索引方案 | ✅ 已决策 | - | 关联表 file_relations |
| Q9 | 主文件切换时 refKeys 处理 | ✅ 已决策 | - | 双重引用（sourceKey + currentMainKey） |
| Q10 | 孤儿文件清理策略 | ✅ 已决策 | - | 定时任务 + 可配置宽限期（默认7天） |

---

**文档更新记录**：
- 2026-02-01 16:00：初始版本，记录核心决策
- 2026-02-01 18:30：添加决策点8（FileRelations 双向关系设计）
  - 确认采用方案C：双重引用（sourceKey + currentMainKey + derivedKeys）
  - MySQL 索引方案：关联表 file_relations
  - 维护策略：约定大于配置，系统默认维护，Plugin 可覆盖
  - 孤儿清理：定时任务 + 可配置宽限期（默认7天）
  - 实施范围：P0 必须完整实现双向关系（不能只标记 TODO）
- 2026-02-01 19:00：识别架构问题（Q3）
  - 问题：测试环境无法验证消息发布-订阅流程
  - 原因：CallbackTaskPublisher 过度耦合 Kafka
  - 方案：引入 Spring Event 实现（适配器模式）
  - 影响：P0 必须完成消息发布-订阅架构重构
  - 收益：测试自动触发、支持多种 MQ、符合 DIP 原则
