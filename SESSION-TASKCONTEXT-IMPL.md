# TaskContext 实施会话文档

> **创建时间**：2026-02-01  
> **最后更新**：2026-02-01 11:13  
> **目的**：恢复会话上下文，跟踪实施进度  
> **当前阶段**：P0 - 所有核心功能已完成，待验证测试

---

## 会话恢复指令

### 新会话启动时必须执行

**Step 1: 阅读核心文档（按顺序）**

```
1. SESSION-TASKCONTEXT-IMPL.md     ← 本文档，了解当前进度
2. TASKCONTEXT-DECISIONS.md        ← 决策文档，技术方案详情
3. todo-list.md                    ← 实施清单，任务依赖关系
4. docs/01-架构设计.md              ← 架构规范，防止违反范式
5. docs/06-领域模型设计.md          ← 领域模型，聚合根边界
6. docs/08-Callback执行器设计.md    ← Callback 执行机制
7. .github/COPILOT_GUIDE.md        ← AI 协作规范，Git 提交约束
```

**Step 2: 检查当前分支和状态**

```bash
git status
git branch
git log --oneline -5
```

**Step 3: 根据本文档"当前进度"章节继续执行**

---

## 项目背景

### 核心问题

1. **TaskContext 元数据缺失**：Plugin 执行时无法获取完整的 task/file 信息
2. **buildParams() Bug**：callback 参数未正确填充
3. **测试架构缺陷**：Kafka Stub 不触发消息消费，无法验证异步流程
4. **FileRelations 缺失**：衍生文件与主文件缺少关联关系

### 解决方案摘要

| 问题 | 方案 | 关键决策 |
|------|------|---------|
| 元数据注入 | 懒加载，completeUpload() 时一次性注入 | 决策点 1 |
| buildParams Bug | 遍历 cfg.params() 填充 Map | 决策点 2 |
| 测试架构 | Spring Event 替代 Kafka Stub | 决策点 3 |
| FileRelations | 方案C 双重引用（sourceKey + currentMainKey + derivedKeys） | 决策点 8 |

---

## 架构约束（必须遵守）

### DDD 聚合根边界

```
┌─────────────────────┐     ┌─────────────────────┐
│   Task 聚合根        │     │   File 聚合根        │
│  ┌───────────────┐  │     │                     │
│  │ TaskContext   │  │     │  - fKey (主键)       │
│  │ (值对象)       │  │     │  - metadata         │
│  └───────────────┘  │     │  - relations        │
│  - 短生命周期        │     │  - 长生命周期         │
│  - callback 执行用   │     │  - 文件全生命周期     │
└─────────────────────┘     └─────────────────────┘
         │                           ▲
         │      fKey 引用            │
         └───────────────────────────┘
```

**约束**：
- Task 聚合根只通过 fKey 引用 File，不持有 File 实体
- FileRelations 属于 File 聚合根，通过 FileRelationRepository 管理
- 跨聚合操作必须通过 Domain Service 或 Application Service

### 消息发布-订阅模式

```
生产环境：
  TaskService → KafkaCallbackTaskPublisher → Kafka → @KafkaListener → CallbackChainRunner

测试环境：
  TaskService → SpringEventCallbackPublisher → ApplicationEventPublisher 
            → @EventListener + @Async → CallbackChainRunner
```

**约束**：
- 使用 `@Profile` 隔离环境
- 接口 `CallbackTaskPublisher` 不能耦合具体 MQ 实现

### Git 提交规范

- 短消息（≤100 字符）：`git commit -m "message"`
- 长消息（>100 字符）：使用 `create_file` 创建临时文件 + `git commit -F`
- 禁止通过命令行参数传递长内容

---

## 当前进度

### 状态标记说明

- ⬜ 未开始
- 🔄 进行中
- ✅ 已完成
- ❌ 阻塞/失败

---

### P0 阶段 1：基础设施层（当前）

| # | 任务 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 1.1.1 | 创建 FileRelations VO | `file-srv-common/.../vo/file/FileRelations.java` | ✅ | 字段：sourceKey, currentMainKey, derivedKeys |
| 1.1.2 | 创建 CallbackTaskEvent | `file-srv-core/.../domain/events/CallbackTaskEvent.java` | ✅ | 字段：taskId, messageId, deadline |
| 1.2.1 | 创建 file_relations 表 | FileRelationEntity.java | ✅ | 主键：(file_fkey, related_fkey, relation_type) |
| 1.3.1 | 更新 CallbackTaskPublisher 注释 | `CallbackTaskPublisher.java` | ✅ | 移除 Kafka 耦合描述 |
| 1.3.2 | KafkaPublisher 添加 Profile | `KafkaCallbackTaskPublisher.java` | ✅ | 添加 @Profile("!test") |
| 1.3.3 | KafkaConsumer 添加 Profile | `KafkaCallbackTaskConsumer.java` | ✅ | 添加 @Profile("!test") |
| 1.4.1 | 配置异步线程池 | `application-test.yml` | ✅ | spring.task.execution.pool |

---

### P0 阶段 2：实现层（待阶段 1 完成）

| # | 任务 | 文件 | 状态 | 依赖 |
|---|------|------|------|------|
| 2.1.1 | 创建 FileRelationRepository | `file-srv-core/.../repository/FileRelationRepository.java` | ✅ | 1.2.1 |
| 2.2.1 | 创建 SpringEventCallbackPublisher | `file-srv-core/.../executor/impl/SpringEventCallbackPublisher.java` | ✅ | 1.1.2 |
| 2.2.2 | 创建 CallbackTaskEventListener | `file-srv-core/.../executor/impl/CallbackTaskEventListener.java` | ✅ | 1.1.2 |
| 2.3.1 | 修改 DerivedFile | `DerivedFile.java` | ✅ | 1.1.1 |
| 2.3.2 | 修改 FileInfoResponse | `FileInfoResponse.java` | ✅ | 1.1.1 |

---

### P0 阶段 3：核心业务逻辑（待阶段 2 完成）

| # | 任务 | 文件 | 状态 | 依赖 |
|---|------|------|------|------|
| 3.1.1 | 修复 buildParams() bug | `TaskAggregate.java` | ✅ | - |
| 3.1.2 | 扩展 create() 方法签名 | `TaskAggregate.java` | ✅ | - |
| 3.1.3 | 实现 populateContextForPlugins() | `TaskAggregate.java` | ✅ | - |
| 3.1.4 | 自动维护 FileRelations | `DerivedFilesAddedEvent.java` + `FileRelationsEventHandler.java` | ✅ | 2.1.1 |
| 3.2.1 | 修改 createTask() | `TaskService.java` | ✅ | 3.1.2 |

---

### P0 阶段 4：测试验证（待阶段 3 完成）

| # | 任务 | 文件 | 状态 | 依赖 |
|---|------|------|------|------|
| 4.1.1 | 修改 E2E 测试 | `PluginCallbackScenarioTest.java` | ✅ | 2.2.x |
| 4.1.2 | 验证消息自动触发 | - | ⬜ | 阶段 3 |
| 4.1.3 | 验证 Context 注入 | - | ⬜ | 阶段 3 |
| 4.1.4 | 验证 FileRelations 功能 | - | ⬜ | 阶段 3 |

---

### P1-P3 进度（待 P0 完成后更新）

> P1-P3 详细任务见 [todo-list.md](todo-list.md)

---

## 关键文件位置

### 需要新建的文件（P0）

```
file-srv-common/src/main/java/tech/icc/filesrv/common/vo/file/
└── FileRelations.java                    ← 1.1.1 ✅

file-srv-core/src/main/java/tech/icc/filesrv/core/
├── domain/events/
│   ├── CallbackTaskEvent.java            ← 1.1.2 ✅
│   └── DerivedFilesAddedEvent.java       ← 3.1.4 ✅ (新增)
├── infra/persistence/
│   ├── entity/FileRelationEntity.java    ← 1.2.1 ✅
│   └── repository/FileRelationRepository.java ← 2.1.1 ✅
├── infra/event/impl/
│   ├── SpringEventCallbackPublisher.java ← 2.2.1 ✅
│   ├── CallbackTaskEventListener.java    ← 2.2.2 ✅
│   └── FileRelationsEventHandler.java    ← 3.1.4 ✅ (新增)
```

### 需要修改的文件（P0）

```
file-srv-core/src/main/java/tech/icc/filesrv/core/
├── infra/executor/
│   ├── CallbackTaskPublisher.java        ← 1.3.1 ✅ 更新注释
│   └── impl/
│       ├── KafkaCallbackTaskPublisher.java ← 1.3.2 ✅ 添加 @Profile
│       ├── KafkaCallbackTaskConsumer.java  ← 1.3.3 ✅ 添加 @Profile
│       └── DefaultCallbackChainRunner.java ← 3.1.4 ✅ 发布事件 (新增)
├── infra/event/
│   ├── TaskEventPublisher.java           ← 3.1.4 ✅ 添加方法 (新增)
│   └── impl/LoggingTaskEventPublisher.java ← 3.1.4 ✅ 实现方法 (新增)
├── domain/tasks/
│   └── TaskAggregate.java                ← 3.1.x ✅ 多处修改
└── application/service/
    └── TaskService.java                  ← 3.2.1 ✅

file-srv-core/src/test/java/tech/icc/filesrv/
├── test/support/stub/
│   └── TaskEventPublisherStub.java       ← 3.1.4 ✅ 添加支持 (新增)
└── test/integration/
    └── PluginCallbackScenarioTest.java   ← 4.1.1 ✅ Awaitility

file-srv-common/src/main/java/tech/icc/filesrv/common/
├── vo/task/DerivedFile.java              ← 2.3.1 ✅
└── response/FileInfoResponse.java        ← 2.3.2 ✅
```

---

## 执行指南

### 每个任务的标准流程

1. **开始任务前**
   - 更新本文档状态为 🔄
   - 确认依赖任务已完成 ✅

2. **执行任务**
   - 阅读决策文档中对应的技术方案
   - 按照架构约束实现
   - 编写/修改代码

3. **验证任务**
   - 编译检查：`mvn clean compile -DskipTests`
   - 单元测试：`mvn test -Dtest=相关测试类`

4. **完成任务**
   - 更新本文档状态为 ✅
   - Git 提交（遵循提交规范）

### 阶段完成检查点

**阶段 1 完成条件**：
- [ ] 所有 1.x.x 任务状态为 ✅
- [ ] `mvn clean compile -DskipTests` 通过
- [ ] Git 提交：`feat(core): add infrastructure for TaskContext implementation`

**阶段 2 完成条件**：
- [x] 所有 2.x.x 任务状态为 ✅
- [x] `mvn clean compile -DskipTests` 通过
- [ ] Git 提交：`feat(core): implement Spring Event message publishing`

**阶段 3 完成条件**：
- [x] 所有 3.x.x 任务状态为 ✅
- [x] `mvn clean compile -DskipTests` 通过
- [ ] Git 提交：`feat(core): implement TaskContext metadata injection and FileRelations`

**阶段 4 完成条件**：
- [x] 4.1.1 E2E 测试修改已完成 ✅
- [ ] 4.1.2-4.1.4 功能验证待执行
- [ ] `mvn test` 通过（所有测试）
- [ ] Git 提交：`test(core): update E2E tests for async callback flow`

---

## 当前进度总结

### ✅ P0 已完成功能

1. **基础设施层** (阶段 1) - 全部完成
   - FileRelations VO、CallbackTaskEvent、DerivedFilesAddedEvent
   - FileRelationEntity 和 Repository
   - Profile 注解隔离、异步线程池配置

2. **实现层** (阶段 2) - 全部完成
   - Spring Event 消息发布订阅机制
   - FileRelationsEventHandler（领域事件监听）
   - DerivedFile 和 FileInfoResponse 扩展

3. **核心业务逻辑** (阶段 3) - 全部完成
   - buildParams() 修复
   - create() 方法签名扩展
   - populateContextForPlugins() 实现
   - **FileRelations 自动维护**（领域事件方案）
   - TaskService.createTask() 更新

4. **测试修改** (阶段 4) - 部分完成
   - PluginCallbackScenarioTest 使用 Awaitility ✅
   - 功能验证待执行

### 🔄 下一步工作

P0 剩余任务：
- 4.1.2 验证消息自动触发
- 4.1.3 验证 Context 注入
- 4.1.4 验证 FileRelations 功能
- 运行完整测试套件
- Git 提交和推送

### 📊 P0 完成度统计

- **阶段 1**：7/7 任务完成 (100%)
- **阶段 2**：5/5 任务完成 (100%)
- **阶段 3**：5/5 任务完成 (100%)
- **阶段 4**：1/4 任务完成 (25%)
- **总计**：18/21 任务完成 (86%)

---

## 问题记录

> 在实施过程中遇到的问题记录在此

| 日期 | 任务 | 问题描述 | 解决方案 | 状态 |
|------|------|---------|---------|------|
| 2026-02-01 | P0.3.1.4 | TaskAggregate 不应依赖 Repository，违反 DDD 分层 | 采用领域事件方案 C，通过 FileRelationsEventHandler 监听 DerivedFilesAddedEvent | ✅ 已解决 |

---

## 变更历史

| 日期 | 变更内容 | 操作者 |
|------|---------|--------|
| 2026-02-01 11:13 | P0.3.1.4 和 P0.4.1.1 完成，更新进度文档 | AI |
| 2026-02-01 | 创建文档，初始化 P0 任务清单 | AI |

---

## 快速参考

### 常用命令

```bash
# 编译检查
mvn clean compile -DskipTests

# 运行特定测试
mvn test -Dtest=PluginCallbackScenarioTest

# 运行所有测试
mvn test

# 查看 Git 状态
git status && git diff --stat

# 提交（短消息）
git add -A && git commit -m "type(scope): message"

# 提交（长消息）
# 1. 使用 create_file 创建 /tmp/commit_msg.txt
# 2. git commit -F /tmp/commit_msg.txt && rm /tmp/commit_msg.txt
```

### 决策文档快速定位

| 决策点 | 主题 | 文档位置 |
|--------|------|---------|
| 决策点 1 | TaskContext 元数据注入机制 | TASKCONTEXT-DECISIONS.md#决策点1 |
| 决策点 2 | buildParams() Bug 修复 | TASKCONTEXT-DECISIONS.md#决策点2 |
| 决策点 3 | E2E 测试异步等待策略 | TASKCONTEXT-DECISIONS.md#决策点3 |
| 决策点 4 | 注解驱动方案 | TASKCONTEXT-DECISIONS.md#决策点4 |
| 决策点 5 | 持久化策略 | TASKCONTEXT-DECISIONS.md#决策点5 |
| 决策点 6 | 并发控制策略 | TASKCONTEXT-DECISIONS.md#决策点6 |
| 决策点 7 | 可观测性 | TASKCONTEXT-DECISIONS.md#决策点7 |
| 决策点 8 | FileRelations 双向关系 | TASKCONTEXT-DECISIONS.md#决策点8 |
