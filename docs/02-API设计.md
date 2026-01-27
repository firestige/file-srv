# API 设计

## 路由前缀

| 功能域 | 路径前缀 | 对应 Controller |
|-------|---------|----------------|
| 文件操作 | `/api/v1/files` | `FileController` |
| 分片任务 | `/api/v1/files/upload_task` | `TaskController` |
| 静态资源 | `/api/v1/static` | `StaticResourceController` |

## 文件接口 (FileController)

### 下载文件
- **GET /api/v1/files/{fkey}**
  - 说明：直接下载文件，服务端代理输出流
  - 路径参数：`fkey` - 文件唯一标识
  - 响应：`ResponseEntity<Resource>`，设置 Content-Disposition/Type/Length
  - 错误码：404 - 文件不存在

### 同步上传
- **POST /api/v1/files/upload**
  - 说明：同步上传（≤10MB），不支持回调
  - 请求类型：`multipart/form-data`
  - 请求参数：
    - `file` (MultipartFile) - 上传的文件
    - `FileInfo` (ModelAttribute) - 文件元数据
  - 响应：`Result<FileInfo>`
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "fkey": "uuid-xxx",
      "fileName": "example.txt",
      "fileSize": 1024,
      "contentType": "text/plain",
      "creator": "user123",
      "tags": "doc,test"
    }
  }
  ```

### 删除文件
- **DELETE /api/v1/files/{fkey}**
  - 说明：删除文件及其元数据，缓存失效
  - 路径参数：`fkey` - 文件唯一标识
  - 响应：`Result<Void>`

### 元数据查询
- **POST /api/v1/files/metadata**
  - 说明：元数据查询，支持分页
  - 请求体：`MetaQueryParams`
  ```json
  {
    "fileName": "report",
    "creator": "user123",
    "contentType": "application/pdf",
    "createdFrom": "2024-01-01T00:00:00",
    "updatedTo": "2024-12-31T23:59:59",
    "tags": ["important", "finance"]
  }
  ```
  - 分页参数（Query）：`page`, `size`, `sort`
  - 响应：`Result<Page<FileInfo>>`

## 分片上传任务接口 (TaskController)

### 创建上传任务
- **POST /api/v1/files/upload_task**
  - 说明：创建分片上传任务，适用 ≥5MB 文件，支持回调
  - 请求体：`FileInfo` - 文件元数据
  - 可选参数：`callback` (String) - 逗号分隔的插件名
  - 响应：`Result<TaskInfo>`
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "taskId": "task-xxx",
      "uploadId": "upload-xxx",
      "status": "PENDING",
      "expiresAt": "2024-01-02T00:00:00",
      "callbacks": ["transcode", "thumbnail"]
    }
  }
  ```

### 上传分片
- **PUT /api/v1/files/upload_task/{taskId}**
  - 说明：上传单个分片
  - 路径参数：`taskId` - 任务ID
  - 查询参数：`partNumber` (int) - 分片序号（1-based）
  - 请求体：`InputStream` (原始二进制流)
  - 响应：`Result<PartUploadInfo>`
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "partNumber": 1,
      "eTag": "etag-xxx"
    }
  }
  ```

### 完成上传
- **POST /api/v1/files/upload_task/{taskId}**
  - 说明：完成分片上传，触发合并与回调插件
  - 路径参数：`taskId` - 任务ID
  - 请求体：`List<PartUploadInfo>` - 分片信息列表
  ```json
  [
    {"partNumber": 1, "eTag": "etag-1"},
    {"partNumber": 2, "eTag": "etag-2"}
  ]
  ```
  - 响应：`Result<Void>`

### 中止上传
- **POST /api/v1/files/upload_task/{taskId}/abort**
  - 说明：中止分片上传，清理已上传分片
  - 路径参数：`taskId` - 任务ID
  - 响应：`Result<Void>`

### 查询任务详情
- **GET /api/v1/files/upload_task/{taskId}**
  - 说明：查询任务详情，包含已上传分片信息
  - 路径参数：`taskId` - 任务ID
  - 响应：`Result<TaskInfo>`

## 静态资源接口 (StaticResourceController)

### 获取静态资源
- **GET /api/v1/static/{fkey}**
  - 说明：获取静态资源，适用于需要直接访问的场景
  - 路径参数：`fkey` - 文件唯一标识
  - 响应：`ResponseEntity<Resource>`

## 约束与校验

| 约束项 | 限制 | 说明 |
|-------|------|------|
| 同步上传大小 | ≤ 10MB | 超过需使用分片上传 |
| 分片数量 | ≤ 10,000 | 对象存储限制 |
| 单片大小 | 5MB ~ 1GB | 最后一片可小于 5MB |
| fkey | 可选 | 缺省时由系统生成 UUID |
| 回调 | 仅分片上传 | 同步上传不支持回调 |

## 响应规范

### 统一响应包装
```java
public record Result<E>(int code, String message, E data) {
    // code=0 表示成功
    // code!=0 表示失败，message 包含错误描述
}
```

### 错误码定义
| 错误码 | 含义 |
|-------|------|
| 0 | 成功 |
| 0x40400 | 文件不存在 |
| 400 | 参数校验失败 |
| 409 | 状态冲突（如任务已完成） |
| 500 | 内部错误 |

## 安全与鉴权

项目不涉及安全与鉴权，工作在可信域内，安全和鉴权由边界处的接入网关负责。