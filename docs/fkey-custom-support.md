# fKey 自定义支持功能实现

## 概述

实现用户自定义 fKey 功能，允许用户在上传文件时指定文件标识，若未指定则自动生成。基于"不信任原则"进行全面验证。

## 修改清单

### Phase 1: 创建验证服务

**新增文件**：`file-srv-core/src/main/java/tech/icc/filesrv/core/domain/services/FKeyValidator.java`

- 验证 fKey 格式（长度 8-64 字符，仅字母数字连字符下划线）
- 验证 fKey 唯一性（查询数据库查重）
- 返回 ValidationResult 包含验证结果和错误信息

### Phase 2: API 层扩展

**修改文件**：
1. `FileUploadRequest.java` - 添加可选的 `fKey` 字段（带 @Pattern 校验）
2. `CreateTaskRequest.java` - 添加可选的 `fKey` 字段（带 @Pattern 校验）

### Phase 3: Assembler 层传递

**修改文件**：
- `FileInfoAssembler.java` - 在 `toDto()` 中传递 `request.getFKey()` 到 FileIdentity

### Phase 4: 领域层支持

**修改文件**：
- `FileReference.java` - 重构 `create()` 方法：
  - 新增带 `fKey` 参数的重载方法
  - 若 fKey 为 null 或空白则自动生成 UUID
  - 保留原方法以向后兼容

### Phase 5: Service 层验证

**修改文件**：
1. `FileService.java`：
   - 注入 `FKeyValidator`
   - 在 `upload()` 方法中验证用户提供的 fKey
   - 若验证失败抛出 IllegalArgumentException（返回 400）

2. `TaskService.java`：
   - 注入 `FKeyValidator`
   - 在 `createTask()` 方法中验证用户提供的 fKey
   - 若验证失败抛出 IllegalArgumentException（返回 400）

### Phase 6: VO 层扩展

**修改文件**：
1. `FileRequest.java` - 添加 `fKey` 字段
2. `TaskInfoAssembler.java` - 在 `toFileRequest()` 中传递 `request.getFKey()`

## 验证规则

| 验证项 | 规则 |
|--------|------|
| **空值** | 不能为 null 或只包含空白字符 |
| **长度** | 8-64 字符 |
| **字符集** | 仅字母、数字、连字符、下划线（正则：`^[a-zA-Z0-9_-]+$`） |
| **唯一性** | 查询 `FileReferenceRepository.existsByFKey()` |

## 错误响应示例

```json
{
  "code": 400,
  "message": "fKey 'my-file-123' 已被使用"
}
```

```json
{
  "code": 400,
  "message": "fKey 只能包含字母、数字、连字符和下划线"
}
```

## API 使用示例

### 同步上传（指定 fKey）

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "fKey=my-custom-file-2024" \
  -F "fileName=document.pdf" \
  -F "fileType=application/pdf" \
  -F "createdBy=user123" \
  -F "file=@/path/to/file.pdf"
```

### 异步上传（指定 fKey）

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "fKey": "my-large-file-2024",
    "filename": "video.mp4",
    "contentType": "video/mp4",
    "size": 104857600,
    "contentHash": "abc123...",
    "createdBy": "user123"
  }'
```

### 自动生成 fKey（不传或传空字符串）

```bash
# 不传 fKey 字段
curl -X POST http://localhost:8080/api/files/upload \
  -F "fileName=document.pdf" \
  -F "fileType=application/pdf" \
  -F "createdBy=user123" \
  -F "file=@/path/to/file.pdf"
```

## 测试建议

### 单元测试
- `FKeyValidatorTest` - 验证器单元测试
- 测试空值、长度、格式、唯一性等各种场景

### 集成测试
- 同步上传指定 fKey
- 异步上传指定 fKey
- 重复 fKey 返回 400
- 格式非法返回 400
- 不指定 fKey 自动生成

## 性能考虑

- 每次验证都查询数据库 `existsByFKey()`
- 建议：添加 Redis 布隆过滤器加速查重（后续优化）
- 影响：每个上传请求增加 1 次数据库查询

## 向后兼容

- ✅ fKey 字段完全可选
- ✅ 现有客户端无需修改
- ✅ 原有方法保持不变
- ✅ 自动生成逻辑保持不变

## 实现日期

2026-02-13
