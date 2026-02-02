# Kafka SPI 边界划分不明确问题

## 问题标签
`SPI` `ACL` `架构边界` `依赖倒置` `Kafka`

## 问题现象

### 依赖关系异常
在 `file-srv-spi-kafka` 模块的 `pom.xml` 中，发现了对 `file-srv-core` 模块的直接依赖：

```xml
<dependency>
    <groupId>tech.icc.filesrv</groupId>
    <artifactId>file-srv-core</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 问题代码
`KafkaCallbackTaskConsumer.java` 直接引用了 core 层的类：

```java
// ❌ SPI 模块不应该引用 core 层
import tech.icc.filesrv.core.domain.task.TaskAggregate;
import tech.icc.filesrv.core.domain.task.TaskStatus;
import tech.icc.filesrv.core.application.port.TaskRepository;
import tech.icc.filesrv.core.infra.executor.CallbackChainRunner;

@Component
public class KafkaCallbackTaskConsumer {
    
    private final TaskRepository taskRepository;          // core 层接口
    private final CallbackChainRunner chainRunner;        // core 层实现
    private final IdempotentChecker idempotentChecker;    // core 层组件
    
    @KafkaListener(...)
    public void consume(CallbackTaskMessage msg, Acknowledgment ack) {
        // 80+ 行业务逻辑
        Optional<TaskAggregate> taskOpt = taskRepository.findById(msg.getTaskId());
        if (taskOpt.isEmpty()) {
            log.warn("Task not found: {}", msg.getTaskId());
            ack.acknowledge();
            return;
        }
        
        TaskAggregate task = taskOpt.get();
        if (task.getStatus() != TaskStatus.CALLBACK_PENDING) {
            log.info("Task status is not CALLBACK_PENDING: {}", task.getStatus());
            ack.acknowledge();
            return;
        }
        
        // 幂等性检查
        if (!idempotentChecker.tryAcquire(msg.getTaskId())) {
            ack.acknowledge();
            return;
        }
        
        try {
            chainRunner.run(task);
            ack.acknowledge();
        } catch (Exception e) {
            // 重试逻辑、死信逻辑...
        }
    }
}
```

### 边界破坏的表现
1. **依赖方向错误**：SPI 模块（基础设施层）依赖 core 模块（领域层）
2. **业务逻辑侵入**：消息消费者包含任务状态判断、幂等性检查、异常处理等业务逻辑
3. **难以替换**：如需更换为 RocketMQ，需要复制大量业务代码

## 根本原因

### 架构边界分析

正确的依赖方向应遵循依赖倒置原则：

```
┌─────────────────────────────────────────────────────────┐
│                      应用层                              │
│  file-srv-autoconfiguration (Bean 装配)                 │
└───────────────────────┬─────────────────────────────────┘
                        │ 依赖
    ┌───────────────────┼───────────────────┐
    ▼                   ▼                   ▼
┌─────────┐      ┌───────────┐      ┌─────────────────┐
│  core   │      │  common   │      │  spi-kafka      │
│ 领域逻辑 │      │  契约/DTO │      │  基础设施适配    │
└────┬────┘      └─────▲─────┘      └───────┬─────────┘
     │                 │                    │
     └─────────────────┴────────────────────┘
           core 和 spi 都只依赖 common
```

### 错误的依赖关系
```
file-srv-spi-kafka
    ├── 依赖 file-srv-common     ✅ 正确
    └── 依赖 file-srv-core       ❌ 错误！破坏了边界
```

### 根因总结

**SPI/ACL 模块的职责定位错误。**

ACL（Anti-Corruption Layer，防腐层）的职责应该只是：
1. **协议转换**：将 Kafka 消息转换为领域可理解的格式
2. **契约适配**：将外部系统的数据结构映射为内部契约
3. **错误转译**：将基础设施异常转换为业务异常

**不应该包含**：
- 任务状态检查（TaskStatus 判断）
- 幂等性控制（IdempotentChecker 调用）
- 重试策略（RetryCount 计算）
- 死信处理（DeadLetter 发布条件判断）

这些都是**业务逻辑**，应该在 core 层实现。

## 解决方案

### 设计思路
采用**委托模式**，将业务逻辑抽象为契约（Handler），由 core 层实现，SPI 层只负责监听和委托。

```
┌────────────────────────────────────────────────────────────────┐
│                          common 模块                            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  CallbackTaskMessageHandler (接口契约)                    │  │
│  │  + handle(CallbackTaskMessage): HandleResult             │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  ExecutorProperties (配置属性)                            │  │
│  │  + kafka, retry, idempotency 等配置                       │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
                              ▲
            ┌─────────────────┴─────────────────┐
            │                                   │
┌───────────┴───────────┐         ┌─────────────┴─────────────┐
│       core 模块        │         │      spi-kafka 模块       │
│  ┌─────────────────┐  │         │  ┌─────────────────────┐  │
│  │ DefaultCallback │  │         │  │ KafkaCallbackTask   │  │
│  │ TaskMessage     │  │         │  │ Consumer            │  │
│  │ Handler         │  │         │  │                     │  │
│  │                 │  │         │  │ - 监听 Kafka 消息    │  │
│  │ - 幂等性检查     │  │         │  │ - 委托给 Handler    │  │
│  │ - 过期检查      │  │         │  │ - ACK/RETRY 处理    │  │
│  │ - 任务加载      │  │         │  │                     │  │
│  │ - 链式执行      │  │         │  └─────────────────────┘  │
│  │ - 异常处理      │  │         │                           │
│  │ - 死信发布      │  │         │  只依赖 common，不依赖 core │
│  └─────────────────┘  │         └───────────────────────────┘
└───────────────────────┘
```

### 代码实现

#### 1. common 层定义契约

```java
// file-srv-common/.../spi/executor/CallbackTaskMessageHandler.java
public interface CallbackTaskMessageHandler {
    
    HandleResult handle(CallbackTaskMessage message);
    
    enum HandleResult {
        ACK,    // 确认消费，不再重试
        RETRY   // 需要重试
    }
}
```

#### 2. core 层实现业务逻辑

```java
// file-srv-core/.../executor/impl/DefaultCallbackTaskMessageHandler.java
@Slf4j
@RequiredArgsConstructor
public class DefaultCallbackTaskMessageHandler implements CallbackTaskMessageHandler {
    
    private final TaskRepository taskRepository;
    private final CallbackChainRunner chainRunner;
    private final IdempotentChecker idempotentChecker;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ExecutorProperties properties;
    
    @Override
    public HandleResult handle(CallbackTaskMessage message) {
        // 1. 过期检查
        if (isExpired(message)) {
            log.warn("Message expired: {}", message.getTaskId());
            return HandleResult.ACK;
        }
        
        // 2. 幂等性检查
        if (!idempotentChecker.tryAcquire(message.getTaskId())) {
            log.debug("Duplicate message: {}", message.getTaskId());
            return HandleResult.ACK;
        }
        
        // 3. 加载任务
        Optional<TaskAggregate> taskOpt = taskRepository.findById(message.getTaskId());
        if (taskOpt.isEmpty()) {
            log.warn("Task not found: {}", message.getTaskId());
            return HandleResult.ACK;
        }
        
        // 4. 状态检查
        TaskAggregate task = taskOpt.get();
        if (task.getStatus() != TaskStatus.CALLBACK_PENDING) {
            log.info("Skip non-pending task: {}", task.getStatus());
            return HandleResult.ACK;
        }
        
        // 5. 执行回调链
        try {
            chainRunner.run(task);
            return HandleResult.ACK;
        } catch (Exception e) {
            return handleException(message, e);
        }
    }
    
    private HandleResult handleException(CallbackTaskMessage message, Exception e) {
        int retryCount = message.getRetryCount();
        int maxRetries = properties.getRetry().getMaxAttempts();
        
        if (retryCount >= maxRetries) {
            log.error("Max retries exceeded, sending to DLQ: {}", message.getTaskId());
            deadLetterPublisher.publish(message, e);
            return HandleResult.ACK;
        }
        
        log.warn("Retry {} for task: {}", retryCount + 1, message.getTaskId());
        return HandleResult.RETRY;
    }
}
```

#### 3. SPI 层纯粹委托

```java
// file-srv-spi-kafka/.../executor/KafkaCallbackTaskConsumer.java
@Slf4j
@RequiredArgsConstructor
public class KafkaCallbackTaskConsumer {
    
    private final CallbackTaskMessageHandler handler;  // 只依赖契约接口
    
    @KafkaListener(
        topics = "${file-srv.executor.kafka.topic}",
        containerFactory = "callbackTaskListenerFactory"
    )
    public void consume(CallbackTaskMessage message, Acknowledgment ack) {
        HandleResult result = handler.handle(message);
        
        switch (result) {
            case ACK -> ack.acknowledge();
            case RETRY -> throw new RetryableException("Retry requested");
        }
    }
}
```

**重构后只有约 15 行代码，职责清晰：监听 → 委托 → ACK/RETRY**

#### 4. 移除错误依赖

```xml
<!-- file-srv-spi-kafka/pom.xml -->
<dependencies>
    <dependency>
        <groupId>tech.icc.filesrv</groupId>
        <artifactId>file-srv-common</artifactId>  <!-- ✅ 只依赖 common -->
    </dependency>
    <!-- ❌ 移除 file-srv-core 依赖 -->
</dependencies>
```

## 技术启示

### 1. ACL/SPI 层的黄金法则

> **ACL 只做协议和契约转换，绝不包含业务逻辑。**

判断标准：如果代码中出现了领域概念（如 TaskStatus、RetryCount 判断），说明业务逻辑已经侵入。

### 2. 可替换性验证

> **如果更换中间件（如 Kafka → RocketMQ）需要复制业务代码，说明边界划分失败。**

正确的设计下，更换 RocketMQ 只需：
1. 新建 `file-srv-spi-rocketmq` 模块
2. 实现监听器，委托给同一个 `CallbackTaskMessageHandler`
3. 无需复制任何业务逻辑

```java
// 假设的 RocketMQ 实现
@RocketMQMessageListener(topic = "callback-task")
public class RocketMQCallbackTaskConsumer implements RocketMQListener<CallbackTaskMessage> {
    
    private final CallbackTaskMessageHandler handler;
    
    @Override
    public void onMessage(CallbackTaskMessage message) {
        HandleResult result = handler.handle(message);
        // 处理 ACK/RETRY
    }
}
```

### 3. 依赖方向原则

```
         ┌─────────────┐
         │   common    │  ← 契约层，被所有模块依赖
         └──────▲──────┘
                │
    ┌───────────┼───────────┐
    │           │           │
┌───┴───┐  ┌────┴────┐  ┌───┴────┐
│ core  │  │  spi-*  │  │ config │
│       │  │         │  │        │
└───────┘  └─────────┘  └────────┘

❌ 错误：spi-* → core（基础设施依赖领域）
✅ 正确：spi-* → common（基础设施依赖契约）
✅ 正确：core → common（领域依赖契约）
```

### 4. 简单性检验

> **好的 ACL 应该简单到「很容易测试甚至不需要测试」。**

重构后的 `KafkaCallbackTaskConsumer`：
- 无分支逻辑（除了 switch 结果处理）
- 无状态
- 无复杂异常处理
- 测试只需验证：调用 handler → 根据结果 ACK/RETRY

### 5. 边界破坏的信号

当你看到以下迹象时，说明 SPI 边界可能已被破坏：

| 信号 | 说明 |
|-----|------|
| SPI 模块 import 了 core 包 | 依赖方向错误 |
| 消费者/适配器超过 30 行 | 业务逻辑可能侵入 |
| 需要 Mock 领域服务才能测试 SPI | 耦合过深 |
| 更换中间件需要大量改动 | 抽象层次不足 |

### 6. 重构检查清单

- [ ] SPI 模块的 pom.xml 是否只依赖 common？
- [ ] SPI 代码是否只有协议转换逻辑？
- [ ] 业务逻辑是否已抽象为 Handler 契约？
- [ ] Handler 实现是否在 core 层？
- [ ] 更换中间件是否只需新增适配器？
