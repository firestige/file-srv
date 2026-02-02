# JPA 乐观锁版本号同步问题

## 问题标签
`JPA` `Hibernate` `乐观锁` `缓存` `并发控制`

## 问题现象

### 错误堆栈
在运行 `MultipartUploadScenarioTest.shouldCompleteMultipartUploadWithoutCallback()` 测试时，出现以下异常：

```
org.springframework.orm.ObjectOptimisticLockingFailureException: 
Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect)
[tech.icc.filesrv.adapter.persistence.TaskEntity#xxx]; 
SQL [update upload_task set ... where task_id=? and version=?]
```

### 错误演化过程
1. **第一阶段**：`DuplicateKeyException: A different object with the same identifier value was already associated with the session`
   - 表现：保存实体时，Session中已存在相同ID的另一个对象实例
   - 初步修复：使用 `EntityManager.merge()` 处理 detached 对象

2. **第二阶段**：`ObjectOptimisticLockingFailureException: Row was updated or deleted`
   - 表现：版本号（@Version）不匹配，导致更新失败
   - 触发场景：对同一任务进行多次操作（创建 → 上传分片 → 完成上传）

### 业务场景
```java
// 1. 创建任务
TaskAggregate task = TaskAggregate.create(...);
taskRepository.save(task);  // version: null → 1

// 2. 从缓存获取任务并上传第一个分片
TaskAggregate cached = taskCache.get(taskId);  // version = null (旧对象)
cached.recordPart(...);
taskRepository.save(cached);  // 💥 版本号冲突！期望 version=1，实际 version=null

// 3. 继续上传后续分片时再次失败
```

## 根本原因

### 问题分析

#### JPA @Version 工作机制
```java
@Entity
public class TaskEntity {
    @Version
    private Long version;  // Hibernate 自动管理
}
```

1. **首次保存**：`version = null` → 数据库生成 `version = 1`
2. **更新操作**：检查当前 `version` 是否匹配，匹配则递增 `version++`
3. **冲突检测**：如果 `version` 不匹配，抛出 `ObjectOptimisticLockingFailureException`

#### 版本号传播链路
```
数据库 (version=1)
    ↓ (SELECT)
TaskEntity (version=1)
    ↓ (toDomain)
TaskAggregate (version=1)
    ↓ (缓存)
缓存层 (version=1)  ✅ 正确
```

#### 问题代码模式
```java
// ❌ 错误：忽略 save() 返回值
TaskAggregate task = TaskAggregate.create(...);
taskRepository.save(task);  // 返回新对象 (version=1)，但被丢弃
taskCache.put(taskId, task);  // 缓存旧对象 (version=null)

// 下一次操作
TaskAggregate cached = taskCache.get(taskId);  // version=null
cached.recordPart(...);
taskRepository.save(cached);  // 💥 版本冲突！
```

### 根因总结
**Repository.save() 返回的是新对象（包含更新后的 @Version），而代码继续使用旧对象进行缓存和后续操作，导致版本号不同步。**

关键点：
1. ✅ @Version 字段已添加到 TaskEntity 和 TaskAggregate
2. ✅ EntityManager.merge() 正确处理 detached 对象
3. ❌ **但 save() 返回值被忽略，版本号更新丢失**

## 修复方案

### 核心原则
**始终使用 Repository.save() 的返回值，确保版本号同步。**

### 代码修改

#### 修改前（8处错误）
```java
// TaskService.java

public void createTask(...) {
    TaskAggregate task = TaskAggregate.create(...);
    taskRepository.save(task);  // ❌ 忽略返回值
    taskCache.put(taskId, task);  // 缓存的对象版本号为 null
}

public void uploadPart(String taskId, ...) {
    TaskAggregate task = getTask(taskId);
    task.recordPart(...);
    taskRepository.save(task);  // ❌ 忽略返回值
    taskCache.put(taskId, task);
}

public void completeUpload(String taskId, ...) {
    TaskAggregate task = getTask(taskId);
    task.completeUpload(...);
    taskRepository.save(task);  // ❌ 忽略返回值
    taskCache.put(taskId, task);
}

public void abortUpload(String taskId, ...) {
    TaskAggregate task = getTask(taskId);
    task.abort(...);
    taskRepository.save(task);  // ❌ 忽略返回值
}
```

#### 修改后（所有 save() 都使用返回值）
```java
// TaskService.java - 修复后的正确写法

public void createTask(...) {
    TaskAggregate task = TaskAggregate.create(...);
    task = taskRepository.save(task);  // ✅ 使用返回值
    taskCache.put(taskId, task);  // 缓存更新后的对象 (version=1)
}

public void uploadPart(String taskId, ...) {
    TaskAggregate task = getTask(taskId);
    task.recordPart(...);
    task = taskRepository.save(task);  // ✅ 使用返回值
    taskCache.put(taskId, task);
}

public void completeUpload(String taskId, ...) {
    TaskAggregate task = getTask(taskId);
    task.completeUpload(...);
    task = taskRepository.save(task);  // ✅ 使用返回值
    taskCache.put(taskId, task);
}

public void abortUpload(String taskId, ...) {
    TaskAggregate task = getTask(taskId);
    task.abort(...);
    task = taskRepository.save(task);  // ✅ 使用返回值
}
```

### 修改位置统计
| 文件 | 方法 | 行号 | 说明 |
|------|------|------|------|
| TaskService.java | `createTask()` | 132 | 创建任务后保存 |
| TaskService.java | `uploadPart()` | 164 | 状态变更为 IN_PROGRESS |
| TaskService.java | `uploadPart()` | 179 | 设置 uploadSessionId |
| TaskService.java | `uploadPart()` | 189 | 记录分片信息 |
| TaskService.java | `completeUpload()` | 236 | 设置 uploadSessionId |
| TaskService.java | `completeUpload()` | 256 | 标记任务完成 |
| TaskService.java | `completeUpload()` | 274 | 标记任务失败 |
| TaskService.java | `abortUpload()` | 341 | 中止任务 |

**共计 8 处修改，确保完整的版本号传播链路。**

### 版本号流转示例
```
创建任务:
  TaskAggregate (version=null)
    → save()
      → DB INSERT (version=1)
      → 返回 TaskEntity (version=1)
      → toDomain()
    → TaskAggregate (version=1) ✅
    → 缓存 (version=1)

上传分片:
  从缓存获取 (version=1) ✅
    → recordPart()
    → save()
      → DB UPDATE (version=1 → 2)
      → 返回 TaskEntity (version=2)
      → toDomain()
    → TaskAggregate (version=2) ✅
    → 更新缓存 (version=2)

完成上传:
  从缓存获取 (version=2) ✅
    → completeUpload()
    → save()
      → DB UPDATE (version=2 → 3)
      → 返回 TaskEntity (version=3)
    → TaskAggregate (version=3) ✅
```

## 技术启示

### 1. JPA 最佳实践

#### ✅ 正确使用 Repository.save()
```java
// 始终使用返回值
entity = repository.save(entity);

// 原因：
// 1. save() 可能返回新的代理对象
// 2. @Version 等数据库生成字段只在返回对象中有效
// 3. 持久化上下文可能创建新的托管对象
```

#### ✅ 理解实体生命周期
```
Transient (瞬时)
    ↓ save()
Persistent (持久) ← merge()
    ↓ clear() / evict()
Detached (游离)
    ↓ merge()
Persistent (持久)
```

#### ✅ @Version 字段必须完整传播
```java
// Entity 层
@Entity
public class TaskEntity {
    @Version
    private Long version;  // Hibernate 管理
}

// Domain 层
public class TaskAggregate {
    private Long version;  // 必须有此字段
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

// Adapter 层
public TaskEntity fromDomain(TaskAggregate aggregate) {
    entity.setVersion(aggregate.getVersion());  // ✅ 必须复制
}

public TaskAggregate toDomain(TaskEntity entity) {
    aggregate.setVersion(entity.getVersion());  // ✅ 必须复制
}
```

### 2. 缓存与 JPA 集成

#### 缓存策略
```java
// ❌ 错误：缓存 save() 前的对象
TaskAggregate task = ...;
taskRepository.save(task);
cache.put(id, task);  // 旧对象，version 不正确

// ✅ 正确：缓存 save() 返回的对象
TaskAggregate task = ...;
task = taskRepository.save(task);  // 获取更新后的对象
cache.put(id, task);  // 新对象，version 正确
```

#### 缓存失效策略
```java
// 任何修改操作后都需要更新缓存
task = taskRepository.save(task);
taskCache.put(task.getTaskId(), task);  // 同步缓存
```

### 3. 领域模型设计

#### 版本号在领域模型中的位置
```java
// 版本号虽然是技术字段，但在 DDD 中属于聚合根的一部分
public class TaskAggregate {
    // 业务标识
    private String taskId;
    
    // 技术字段（并发控制）
    private Long version;  // ✅ 必须包含，用于乐观锁
    
    // 业务字段
    private TaskStatus status;
    private List<PartInfo> parts;
}
```

### 4. 测试与调试技巧

#### 添加日志追踪版本号
```java
log.debug("Before save: taskId={}, version={}", task.getTaskId(), task.getVersion());
task = taskRepository.save(task);
log.debug("After save: taskId={}, version={}", task.getTaskId(), task.getVersion());
```

#### 单元测试验证版本号传播
```java
@Test
void shouldPropagateVersionThroughSave() {
    TaskAggregate task = TaskAggregate.create(...);
    assertThat(task.getVersion()).isNull();
    
    task = taskRepository.save(task);
    assertThat(task.getVersion()).isEqualTo(1L);
    
    task.recordPart(...);
    task = taskRepository.save(task);
    assertThat(task.getVersion()).isEqualTo(2L);
}
```

## 相关资源

### 官方文档
- [JPA 2.2 Specification - 3.4.2 Optimistic Locking](https://jakarta.ee/specifications/persistence/2.2/jakarta-persistence-spec-2.2.html#a2540)
- [Hibernate User Guide - Locking](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#locking)

### 最佳实践参考
- [Spring Data JPA - Best Practices](https://www.baeldung.com/spring-data-jpa-best-practices)
- [Vlad Mihalcea - JPA and Hibernate Tutorial](https://vladmihalcea.com/tutorials/hibernate/)

### 相关问题
- [StackOverflow: ObjectOptimisticLockingFailureException](https://stackoverflow.com/questions/tagged/optimistic-locking+jpa)
- [Hibernate Forum: Detached entity handling](https://discourse.hibernate.org/)

## 总结

### 问题本质
**忽略 JPA Repository.save() 返回值，导致 @Version 字段更新丢失，引发乐观锁冲突。**

### 修复要点
1. ✅ 所有 `repository.save()` 调用必须使用返回值
2. ✅ @Version 字段必须在所有层（Entity/Domain/Cache）完整传播
3. ✅ 使用 `EntityManager.merge()` 正确处理 detached 对象

### 预防措施
1. 代码审查：检查所有 `save()` 调用是否使用返回值
2. 静态分析：添加 Checkstyle/PMD 规则检测 `save()` 返回值未使用
3. 单元测试：验证版本号在多次操作后正确递增
4. 集成测试：模拟真实并发场景，验证乐观锁机制

---

**文档创建日期**: 2026-02-02  
**问题解决日期**: 2026-02-02  
**影响范围**: TaskService 所有修改任务状态的方法  
**修复版本**: 待发布
