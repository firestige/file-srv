# Task Hash 字段持久化问题

## 问题标签
`JPA` `领域模型` `字段映射` `数据持久化`

## 问题现象

### 错误堆栈（第一次）
```
org.springframework.dao.InvalidDataAccessApiUsageException: The given id must not be null
	at org.springframework.orm.jpa.EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible
```

触发位置：`FileService.activateFile()` → `FileInfo.createPending(contentHash, ...)` → `fileInfoRepository.save(newInfo)`

日志显示：
```
09:30:34.339 [ForkJoinPool-1-worker-1] DEBUG t.i.f.c.a.service.FileService - Activating file: fKey=7016bc81-9650-480f-810a-3f1dfd90fd5f, contentHash=null
```

### 错误堆栈（第二次 - 修复后）
```
org.springframework.dao.DataIntegrityViolationException: could not execute statement [Value too long for column "CONTENT_HASH CHARACTER VARYING(32)": "'21e9cef915254a38aab6d43d28e618822f29e569b1868557ca6e3eacc391f697' (64)"; SQL statement:
insert into file_info (content_type,created_at,ref_count,size,status,content_hash) values (?,?,?,?,?,?) [22001-224]]
```

SHA-256 hash 长度为 64 字符，但数据库列定义为 VARCHAR(32)。

## 根本原因

### 问题 1：TaskAggregate 字段设计冗余

**初始设计**：TaskAggregate 同时包含两个 hash 字段
```java
public class TaskAggregate {
    private String contentHash;  // 客户端传入的 hash
    private String hash;         // 服务端计算的 hash
}
```

**TaskEntity 映射**：只有一个 `hash` 列
```java
@Entity
public class TaskEntity {
    @Column(name = "hash", length = 64)
    private String hash;
    // 没有 contentHash 字段
}
```

**持久化问题**：
```java
// fromDomain() - 保存时
TaskEntity.builder()
    .hash(task.getHash())  // 只保存 hash，contentHash 丢失
    .build()

// toDomain() - 读取时
task.setHash(entity.getHash());  
// contentHash 未设置，保持为 null
```

**数据流转**：
```
1. 创建任务：
   客户端 contentHash → TaskAggregate.contentHash ✅
   TaskAggregate.hash = null

2. 保存到 DB：
   TaskEntity.hash = task.getHash() = null ❌
   数据库 hash 列为 null

3. 从 DB 读取：
   TaskAggregate.hash = entity.getHash() = null
   TaskAggregate.contentHash = null ❌

4. 完成上传：
   fileService.activateFile(fKey, task.getContentHash(), ...)
   → contentHash = null ❌
   → FileInfo.createPending(null, ...) 
   → save() → 主键为 null 💥
```

### 问题 2：FileInfo 和 FileReference 主键列长度不足

**FileInfoEntity**：
```java
@Entity
public class FileInfoEntity {
    @Id
    @Column(name = "content_hash", length = 32)  // ❌ 长度不够
    private String contentHash;
}
```

**FileReferenceEntity**：
```java
@Entity
public class FileReferenceEntity {
    @Column(name = "content_hash", length = 32)  // ❌ 长度不够
    private String contentHash;
}
```

SHA-256 hash 十六进制表示为 64 个字符，但列只定义了 32。

## 修复方案

### 修复 1：简化领域模型，统一 hash 字段

**设计原则**：
- 客户端传入的 contentHash 就是文件的唯一标识
- 服务端无需重新计算，直接使用客户端提供的值
- 如需验证，可在完成上传时比对实际内容与 hash

**修改后的 TaskAggregate**：
```java
public class TaskAggregate {
    private String hash;  // 统一字段，初始值为客户端 contentHash
    // 移除 contentHash 字段
}
```

**修改后的创建逻辑**：
```java
public static TaskAggregate create(String fKey, String contentHash, ...) {
    TaskAggregate task = new TaskAggregate(...);
    task.hash = contentHash;  // 直接赋值给 hash
    return task;
}
```

**持久化映射**：
```java
// fromDomain() - 保存
TaskEntity.builder()
    .hash(task.getHash())  // hash 包含客户端值
    .build()

// toDomain() - 读取
task.setHash(entity.getHash());  // 正确恢复
```

**完整数据流转**：
```
1. 创建任务：
   客户端 contentHash → TaskAggregate.hash ✅

2. 保存到 DB：
   TaskEntity.hash = task.getHash() = contentHash ✅
   数据库 hash 列 = contentHash ✅

3. 从 DB 读取：
   TaskAggregate.hash = entity.getHash() = contentHash ✅

4. 完成上传：
   fileService.activateFile(fKey, task.getHash(), ...)
   → hash = contentHash ✅
   → FileInfo.createPending(contentHash, ...) ✅
```

### 修复 2：调整 FileInfo 和 FileReference 主键列长度

**FileInfoEntity**：
```java
@Entity
public class FileInfoEntity {
    @Id
    @Column(name = "content_hash", length = 64)  // ✅ SHA-256 需要 64 字符
    private String contentHash;
}
```

**FileReferenceEntity**：
```java
@Entity
public class FileReferenceEntity {
    @Column(name = "content_hash", length = 64)  // ✅ SHA-256 需要 64 字符
    private String contentHash;
}
```

## 代码变更

### 变更 1：TaskAggregate.java

```java
// 移除 contentHash 字段声明
- private String contentHash;
  private String hash;

// 修改 create 方法
public static TaskAggregate create(String fKey, String contentHash, ...) {
    TaskAggregate task = new TaskAggregate(...);
-   task.contentHash = contentHash;
+   task.hash = contentHash;  // 直接赋值
    return task;
}

// 移除 getContentHash() 和 setContentHash() 方法
- public String getContentHash() { return contentHash; }
- public void setContentHash(String contentHash) { this.contentHash = contentHash; }
```

### 变更 2：TaskService.java

```java
// 修改 completeUpload 调用
- completeUpload(taskId, parts, task.getContentHash(), ...);
+ completeUpload(taskId, parts, task.getHash(), ...);
```

### 变更 3：TaskEntity.java

```java
// fromDomain - 无需特殊处理
.hash(task.getHash())

// toDomain - 只设置 hash
task.setHash(hash);
```

### 变更 4：FileInfoEntity.java 和 FileReferenceEntity.java

**FileInfoEntity.java**：
```java
@Id
- @Column(name = "content_hash", length = 32)
+ @Column(name = "content_hash", length = 64)
private String contentHash;
```

**FileReferenceEntity.java**：
```java
- @Column(name = "content_hash", length = 32)
+ @Column(name = "content_hash", length = 64)
private String contentHash;
```

## 技术启示

### 1. 领域模型设计原则

#### ✅ 避免冗余字段
```java
// ❌ 错误：两个字段表示同一概念
private String contentHash;  // 客户端值
private String hash;         // 服务端值

// ✅ 正确：单一字段，语义清晰
private String hash;  // 统一的文件哈希
```

#### ✅ 字段语义一致性
- 如果客户端和服务端 hash 必须相同（验证用），不需要分开存储
- 如果允许不同，应该明确命名差异（如 `clientHash` vs `serverHash`）

### 2. 持久化映射完整性

#### ✅ 确保字段完整映射
```java
// 领域层字段必须与持久层一一对应
TaskAggregate:  hash, contentType, totalSize
       ↕
TaskEntity:     hash, contentType, totalSize
```

#### ✅ fromDomain/toDomain 对称性
```java
// fromDomain - 所有领域字段都要保存
entity.setHash(task.getHash());

// toDomain - 所有持久化字段都要恢复
task.setHash(entity.getHash());
```

### 3. 数据库列设计

#### ✅ 列长度匹配数据类型
```java
// SHA-256: 64 字符
@Column(length = 64)

// MD5: 32 字符
@Column(length = 32)

// UUID: 36 字符（带连字符）
@Column(length = 36)
```

#### ✅ 预留适当空间
对于可能变化的字段（如文件名），适当预留长度：
```java
@Column(name = "filename", length = 512)  // 而不是 255
```

### 4. 调试技巧

#### 添加日志追踪字段值
```java
log.debug("Creating task: hash={}", task.getHash());
task = taskRepository.save(task);
log.debug("Saved task: hash={}", task.getHash());
```

#### 验证持久化前后一致性
```java
@Test
void shouldPersistHashCorrectly() {
    TaskAggregate task = TaskAggregate.create(..., "test-hash", ...);
    assertThat(task.getHash()).isEqualTo("test-hash");
    
    task = taskRepository.save(task);
    assertThat(task.getHash()).isEqualTo("test-hash");
    
    TaskAggregate loaded = taskRepository.findById(task.getTaskId()).get();
    assertThat(loaded.getHash()).isEqualTo("test-hash");
}
```

## 相关资源

### JPA 最佳实践
- [Hibernate User Guide - Basic Types](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#basic)
- [JPA Column Length Best Practices](https://www.baeldung.com/jpa-column-definition)

### 领域模型设计
- [Domain-Driven Design: Tackling Complexity in the Heart of Software](https://www.domainlanguage.com/ddd/)
- [Effective Aggregate Design](https://www.dddcommunity.org/library/vernon_2011/)

## 总结

### 问题本质
**领域模型字段冗余 + 持久化映射不完整，导致关键字段未被保存和恢复。**

### 修复要点
1. ✅ 简化领域模型：contentHash → hash（统一字段）
2. ✅ 客户端 contentHash 作为 hash 的初始值直接保存
3. ✅ 调整两个实体的列长度：FileInfoEntity 和 FileReferenceEntity 都从 32 → 64

### 预防措施
1. 领域模型设计时避免语义重复的字段
2. 确保 fromDomain/toDomain 方法的对称性
3. 数据库列长度与实际数据类型匹配
4. 添加单元测试验证持久化前后一致性

---

**文档创建日期**: 2026-02-02  
**问题解决日期**: 2026-02-02  
**影响范围**: TaskAggregate, TaskEntity, FileInfoEntity  
**修复版本**: 待发布
