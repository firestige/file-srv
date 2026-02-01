# TaskContext 实施清单

> **创建时间**：2026-02-01  
> **来源文档**：[TASKCONTEXT-DECISIONS.md](TASKCONTEXT-DECISIONS.md)  
> **组织原则**：按依赖关系分组，组内可并行，组间需顺序执行

---

## P0 - 立即实施（本次 PR）

### 阶段 1：基础设施层（无依赖，可并行）

这些修改互不依赖，可以同时进行。

#### 1.1 创建 VO 和领域事件

| 任务 | 文件 | 说明 | 状态 |
|------|------|------|------|
| 创建 FileRelations VO | `file-srv-common/.../vo/file/FileRelations.java` | 字段：sourceKey, currentMainKey, derivedKeys | ⬜ |
| 创建 CallbackTaskEvent | `file-srv-core/.../domain/events/CallbackTaskEvent.java` | 字段：taskId, messageId, deadline | ⬜ |

#### 1.2 数据库变更

| 任务 | 文件 | 说明 | 状态 |
|------|------|------|------|
| 创建 file_relations 表 | `迁移脚本（Flyway/Liquibase）` | 主键：(file_fkey, related_fkey, relation_type) | ⬜ |

#### 1.3 接口层修改（注释/注解）

| 任务 | 文件 | 说明 | 状态 |
|------|------|------|------|
| 更新接口注释 | `CallbackTaskPublisher.java` | 移除 Kafka 耦合描述 | ⬜ |
| Kafka Publisher 添加 Profile | `KafkaCallbackTaskPublisher.java` | 添加 @Profile("!test") | ⬜ |
| Kafka Consumer 添加 Profile | `KafkaCallbackTaskConsumer.java` | 添加 @Profile("!test") | ⬜ |

#### 1.4 配置文件

| 任务 | 文件 | 说明 | 状态 |
|------|------|------|------|
| 配置异步线程池 | `application-test.yml` | spring.task.execution.pool 配置 | ⬜ |

---

### 阶段 2：实现层（依赖阶段 1）

#### 2.1 Repository 层

| 任务 | 文件 | 说明 | 依赖 | 状态 |
|------|------|------|------|------|
| 创建 FileRelationRepository | `file-srv-core/.../repository/FileRelationRepository.java` | CRUD + findOrphanFiles | 1.2 数据库表 | ⬜ |

#### 2.2 消息发布-订阅实现

| 任务 | 文件 | 说明 | 依赖 | 状态 |
|------|------|------|------|------|
| 创建 SpringEventCallbackPublisher | `file-srv-core/.../executor/impl/SpringEventCallbackPublisher.java` | @Profile("test"), 使用 ApplicationEventPublisher | 1.1 CallbackTaskEvent | ⬜ |
| 创建 CallbackTaskEventListener | `file-srv-core/.../executor/impl/CallbackTaskEventListener.java` | @EventListener + @Async，调用 chainRunner.run() | 1.1 CallbackTaskEvent | ⬜ |

#### 2.3 DTO/Response 修改

| 任务 | 文件 | 说明 | 依赖 | 状态 |
|------|------|------|------|------|
| 修改 DerivedFile | `DerivedFile.java` | 添加 fKey, relations 字段 | 1.1 FileRelations VO | ⬜ |
| 修改 FileInfoResponse | `FileInfoResponse.java` | 添加 @JsonUnwrapped relations 字段 | 1.1 FileRelations VO | ⬜ |

---

### 阶段 3：核心业务逻辑（依赖阶段 2）

#### 3.1 TaskAggregate 修改

| 任务 | 文件 | 说明 | 依赖 | 状态 |
|------|------|------|------|------|
| 修复 buildParams() bug | `TaskAggregate.java` | 遍历 cfg.params() 填充 Map | 无 | ⬜ |
| 扩展 create() 方法签名 | `TaskAggregate.java` | 添加 filename, contentType, size 参数 | 无 | ⬜ |
| 实现 populateContextForPlugins() | `TaskAggregate.java` | 在 completeUpload() 中注入元数据 | 无 | ⬜ |
| 自动维护 FileRelations | `TaskAggregate.java` | Plugin 创建衍生文件时设置关联关系 | 2.1 FileRelationRepository | ⬜ |

#### 3.2 TaskService 修改

| 任务 | 文件 | 说明 | 依赖 | 状态 |
|------|------|------|------|------|
| 修改 createTask() | `TaskService.java` | 传递 filename, contentType, size | 3.1 TaskAggregate | ⬜ |

---

### 阶段 4：测试验证（依赖阶段 3）

| 任务 | 文件 | 说明 | 依赖 | 状态 |
|------|------|------|------|------|
| 修改 E2E 测试 | `PluginCallbackScenarioTest.java` | 移除手动 run()，使用 Awaitility 轮询 | 2.2 消息发布-订阅 | ⬜ |
| 验证消息自动触发 | - | 确认 Spring Event 自动执行 callback | 阶段 3 全部完成 | ⬜ |
| 验证 Context 注入 | - | filename 不为 null，参数正确 | 阶段 3 全部完成 | ⬜ |
| 验证 FileRelations 功能 | - | 双向关系创建、切换、事务一致性 | 阶段 3 全部完成 | ⬜ |

---

## P0 依赖关系图

```
阶段 1（可并行）
├─ 1.1 创建 VO 和事件
├─ 1.2 数据库表
├─ 1.3 接口注释/注解
└─ 1.4 配置文件
       │
       ▼
阶段 2（依赖阶段 1）
├─ 2.1 Repository ← 依赖 1.2
├─ 2.2 消息发布-订阅 ← 依赖 1.1
└─ 2.3 DTO 修改 ← 依赖 1.1
       │
       ▼
阶段 3（依赖阶段 2）
├─ 3.1 TaskAggregate ← 依赖 2.1
└─ 3.2 TaskService ← 依赖 3.1
       │
       ▼
阶段 4（依赖阶段 3）
└─ 测试验证 ← 依赖 2.2 + 阶段 3
```

---

## P1 - 短期优化（1-2 周内）

> **依赖**：P0 全部完成后开始

### 阶段 5：配置与文档（P0 完成后可并行）

| 任务 | 文件 | 依赖 | 状态 |
|------|------|------|------|
| 创建生产环境配置 | `application-prod.yml`（新建） | 无 | ⬜ |
| 添加孤儿清理配置项 | `application.yml` | 无 | ⬜ |
| 添加配置文档 | `docs/configuration-guide.md`（新建） | 无 | ⬜ |

### 阶段 6：孤儿文件清理（依赖阶段 5 配置）

| 任务 | 文件 | 依赖 | 状态 |
|------|------|------|------|
| FileRelationRepository 添加孤儿查询 | `FileRelationRepository.java` | P0 阶段 2.1 | ⬜ |
| 实现孤儿清理定时任务 | `OrphanFileCleanupTask.java`（新建） | 孤儿查询方法 + 阶段 5 配置 | ⬜ |
| 添加监控指标（Micrometer） | `OrphanFileCleanupTask.java` | 清理任务 | ⬜ |

### 阶段 7：并发控制与缓存（可并行）

| 任务 | 文件 | 依赖 | 状态 |
|------|------|------|------|
| 实现 @Version 乐观锁 | `TaskAggregate.java` | P0 完成 | ⬜ |
| TaskService 添加重试逻辑 | `TaskService.java` | 乐观锁 | ⬜ |
| 实现 Redis 缓存层 | `TaskCacheService.java`（新建） | P0 完成 | ⬜ |

### 阶段 8：可观测性（可并行）

| 任务 | 文件 | 依赖 | 状态 |
|------|------|------|------|
| 实现 AOP 日志切面 | `TaskContextLoggingAspect.java`（新建） | P0 完成 | ⬜ |

---

## P2 - 中期迭代（1 个月内）

> **依赖**：P1 核心功能完成后开始

### 阶段 9：开发体验优化（可并行）

| 任务 | 文件 | 依赖 | 状态 |
|------|------|------|------|
| 定义 TaskContextKeys 常量类 | `TaskContextKeys.java`（新建） | P0 Context 注入完成 | ⬜ |

### 阶段 10：Plugin Storage API（顺序执行）

| 任务 | 文件 | 依赖 | 状态 |
|------|------|------|------|
| 设计 PluginStorageService 接口 | `PluginStorageService.java`（新建） | 无 | ⬜ |
| 实现 uploadLargeFile() | `PluginStorageServiceImpl.java`（新建） | 接口定义 | ⬜ |
| 实现 downloadFile() | `PluginStorageServiceImpl.java` | 接口定义 | ⬜ |
| 实现 deleteFile() | `PluginStorageServiceImpl.java` | 接口定义 | ⬜ |
| 实现 getTemporaryUrl() | `PluginStorageServiceImpl.java` | 接口定义 | ⬜ |
| 集成到 CallbackChainRunner | `DefaultCallbackChainRunner.java` | 实现完成 | ⬜ |

### 阶段 11：测试插件重构（依赖阶段 10）

| 任务 | 文件 | 依赖 | 状态 |
|------|------|------|------|
| 重构 TestThumbnailPlugin | `TestThumbnailPlugin.java` | Plugin Storage API | ⬜ |
| 重构 TestRenamePlugin | `TestRenamePlugin.java` | Plugin Storage API | ⬜ |
| 重构其他测试插件 | 其他 TestPlugin | Plugin Storage API | ⬜ |

---

## P3 - 长期优化（2-3 个月）

> **依赖**：P2 完成后开始

### 阶段 12：注解驱动（顺序执行）

| 任务 | 文件 | 依赖 | 状态 |
|------|------|------|------|
| 创建 @ContextKey 注解 | `ContextKey.java`（新建） | 无 | ⬜ |
| 实现注解处理器 | `ContextKeyProcessor.java`（新建） | 注解定义 | ⬜ |
| 配置 SPI | `META-INF/services/javax.annotation.processing.Processor` | 处理器 | ⬜ |
| 使用 JavaPoet 生成代码 | `ContextKeyProcessor.java` | 处理器 | ⬜ |

### 阶段 13：诊断与调试（可并行）

| 任务 | 文件 | 依赖 | 状态 |
|------|------|------|------|
| 添加 getAvailableKeys() | `TaskContext.java` | 无 | ⬜ |
| 添加 getDiagnosticInfo() | `TaskContext.java` | 无 | ⬜ |
| 添加 getHistory()（可选） | `TaskContext.java` | 无 | ⬜ |

---

## 完整依赖关系图

```
P0 - 立即实施
├─ 阶段 1：基础设施层（可并行）
│   ├─ 1.1 VO 和事件
│   ├─ 1.2 数据库表
│   ├─ 1.3 接口注解
│   └─ 1.4 配置文件
│
├─ 阶段 2：实现层（依赖阶段 1）
│   ├─ 2.1 Repository
│   ├─ 2.2 消息发布-订阅
│   └─ 2.3 DTO 修改
│
├─ 阶段 3：核心业务逻辑（依赖阶段 2）
│   ├─ 3.1 TaskAggregate
│   └─ 3.2 TaskService
│
└─ 阶段 4：测试验证（依赖阶段 3）
       │
       ▼
P1 - 短期优化（依赖 P0）
├─ 阶段 5：配置与文档（可并行）
├─ 阶段 6：孤儿文件清理（依赖阶段 5）
├─ 阶段 7：并发控制与缓存（可并行）
└─ 阶段 8：可观测性（可并行）
       │
       ▼
P2 - 中期迭代（依赖 P1）
├─ 阶段 9：开发体验优化（可并行）
├─ 阶段 10：Plugin Storage API（顺序）
└─ 阶段 11：测试插件重构（依赖阶段 10）
       │
       ▼
P3 - 长期优化（依赖 P2）
├─ 阶段 12：注解驱动（顺序）
└─ 阶段 13：诊断与调试（可并行）
```

---

## 快速参考：全部文件清单

### 新建文件（共 18 个）

#### P0（7 个）
1. `file-srv-common/.../vo/file/FileRelations.java`
2. `file-srv-core/.../domain/events/CallbackTaskEvent.java`
3. `file-srv-core/.../executor/impl/SpringEventCallbackPublisher.java`
4. `file-srv-core/.../executor/impl/CallbackTaskEventListener.java`
5. `file-srv-core/.../repository/FileRelationRepository.java`
6. `数据库迁移脚本（file_relations 表）`
7. `application-test.yml`（或修改现有）

#### P1（5 个）
8. `application-prod.yml`
9. `docs/configuration-guide.md`
10. `OrphanFileCleanupTask.java`
11. `TaskCacheService.java`
12. `TaskContextLoggingAspect.java`

#### P2（3 个）
13. `TaskContextKeys.java`
14. `PluginStorageService.java`（接口）
15. `PluginStorageServiceImpl.java`（实现）

#### P3（3 个）
16. `ContextKey.java`（注解）
17. `ContextKeyProcessor.java`（注解处理器）
18. `META-INF/services/javax.annotation.processing.Processor`

### 修改文件（共 16 个）

#### P0（9 个）
1. `CallbackTaskPublisher.java`
2. `KafkaCallbackTaskPublisher.java`
3. `KafkaCallbackTaskConsumer.java`
4. `DerivedFile.java`
5. `FileInfoResponse.java`
6. `TaskAggregate.java`
7. `TaskService.java`
8. `PluginCallbackScenarioTest.java`
9. `application-test.yml`

#### P1（3 个）
10. `FileRelationRepository.java`（添加孤儿查询）
11. `TaskAggregate.java`（乐观锁）
12. `TaskService.java`（重试逻辑）

#### P2（4 个）
13. `DefaultCallbackChainRunner.java`（集成 Storage API）
14. `TestThumbnailPlugin.java`
15. `TestRenamePlugin.java`
16. 其他测试插件

#### P3（1 个）
17. `TaskContext.java`（诊断方法）

---

## 工期估算

| 优先级 | 阶段 | 预计工期 | 累计 |
|--------|------|---------|------|
| **P0** | 阶段 1-4 | 3-5 天 | 3-5 天 |
| **P1** | 阶段 5-8 | 3-5 天 | 6-10 天 |
| **P2** | 阶段 9-11 | 5-7 天 | 11-17 天 |
| **P3** | 阶段 12-13 | 3-5 天 | 14-22 天 |

**总预计工期**：14-22 天（约 3-4 周）

---

## 里程碑

| 里程碑 | 完成标志 | 预计时间 |
|--------|---------|---------|
| **M1: P0 完成** | E2E 测试通过，消息自动触发，FileRelations 功能正常 | 第 1 周末 |
| **M2: P1 完成** | 孤儿清理运行，乐观锁生效，缓存层可用 | 第 2 周末 |
| **M3: P2 完成** | Plugin Storage API 可用，测试插件重构完成 | 第 3 周末 |
| **M4: P3 完成** | 注解处理器工作，诊断方法可用 | 第 4 周末 |
