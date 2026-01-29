# API 设计

> **最后更新**: 2026-01-29  
> **API 规范**: 详见 [api.yml](./api.yml)（OpenAPI 3.1 标准）

## 设计原则

### RESTful 风格
- 使用标准 HTTP 方法（GET/POST/PUT/DELETE）
- 资源导向的 URL 设计
- 状态码语义化（200/201/204/400/404/500）

### 版本管理
- 路径版本控制：`/api/v1/...`
- 向后兼容优先
- 废弃 API 保留至少 2 个版本周期

### 安全设计
- 所有端点需经过认证（网关层）
- 使用 HTTPS 传输
- 敏感操作（删除）需要额外授权
- 防止 CSRF/XSS 攻击

### 性能优化
- 支持 HTTP 缓存（ETag/Last-Modified）
- 分页查询默认限制（max 1000）
- 大文件使用流式传输
- 支持 Range 请求（断点续传）

## API 概览

### 路由结构

```
/api/v1/
├── files/                          # 文件操作
│   ├── {fkey}                      # GET(下载) / DELETE(删除)
│   ├── {fkey}/metadata             # GET(获取元数据)
│   ├── {fkey}/presign              # GET(预签名URL)
│   ├── metadata                    # POST(查询元数据)
│   ├── upload                      # POST(同步上传)
│   ├── upload_task                 # POST(创建上传任务)
│   └── upload_task/{taskID}        # PUT(上传分片) / GET(查询状态)
│       ├── complete                # POST(完成上传)
│       └── abort                   # POST(中止上传)
└── static/                         # 静态资源访问（公开文件）
```

### 核心端点

| 方法 | 路径 | 功能 | 文档链接 |
|------|------|------|---------|
| GET | `/files/{fkey}` | 下载文件 | [api.yml#L8](./api.yml#L8) |
| DELETE | `/files/{fkey}` | 删除文件 | [api.yml#L47](./api.yml#L47) |
| GET | `/files/{fkey}/metadata` | 获取文件元数据 | [api.yml#L73](./api.yml#L73) |
| GET | `/files/{fkey}/presign` | 获取预签名下载URL | [api.yml#L88](./api.yml#L88) |
| POST | `/files/metadata` | 查询文件元数据（分页） | [api.yml#L120](./api.yml#L120) |
| POST | `/files/upload` | 同步上传文件 | [api.yml#L223](./api.yml#L223) |
| POST | `/files/upload_task` | 创建异步上传任务 | [api.yml#L268](./api.yml#L268) |
| PUT | `/files/upload_task/{taskID}` | 上传文件分片 | [api.yml#L335](./api.yml#L335) |
| GET | `/files/upload_task/{taskID}` | 查询上传任务状态 | [api.yml#L382](./api.yml#L382) |
| POST | `/files/upload_task/{taskID}/complete` | 完成文件上传任务 | [api.yml#L398](./api.yml#L398) |
| POST | `/files/upload_task/{taskID}/abort` | 中止文件上传任务 | [api.yml#L438](./api.yml#L438) |

## 数据模型

### 核心模型

详细的数据模型定义请参考：
- **API 层模型**: [api.yml#components/schemas](./api.yml#L448)
- **领域层模型**: [06-领域模型设计.md](./06-领域模型设计.md)
- **数据层模型**: [03-数据模型设计.md](./03-数据模型设计.md)

### 关键 Schema

| Schema | 用途 | 定义位置 |
|--------|------|---------|
| `FileMeta` | 文件元数据（请求/响应） | [api.yml#L532](./api.yml#L532) |
| `FileInfoResponse` | 文件信息响应 | [api.yml#L867](./api.yml#L867) |
| `TaskResponse` | 任务响应（多态） | [api.yml#L650](./api.yml#L650) |
| `ApiResponse` | 统一响应包装 | [api.yml#L448](./api.yml#L448) |

## 响应格式

### 成功响应
```json
{
  "code": 200,
  "msg": "成功",
  "data": { ... }
}
```

### 错误响应
```json
{
  "code": 400,
  "msg": "请求参数错误",
  "data": null
}
```

### 标准错误码

| HTTP 状态码 | 业务码 | 说明 | 定义位置 |
|------------|-------|------|---------|
| 400 | 400 | 请求参数错误 | [api.yml#L960](./api.yml#L960) |
| 404 | 404 | 资源未找到 | [api.yml#L973](./api.yml#L973) |
| 413 | 413 | 请求体过大 | [api.yml#L992](./api.yml#L992) |
| 429 | 429 | 请求过于频繁 | [api.yml#L1005](./api.yml#L1005) |
| 500 | 500 | 服务器内部错误 | [api.yml#L1018](./api.yml#L1018) |
| 501 | 501 | 功能未实现 | [api.yml#L984](./api.yml#L984) |
| 503 | 503 | 服务不可用 | [api.yml#L1030](./api.yml#L1030) |

## 特殊功能说明

### 文件下载

#### 直接下载（GET /files/{fkey}）
- 服务端代理流式传输
- 支持 Range 请求（断点续传）
- 自动设置 Content-Type/Content-Disposition
- 文件元数据通过响应头返回（`X-ICC-META`）

#### 预签名下载（GET /files/{fkey}/presign）
- 适用于存储支持预签名 URL 的场景（如 S3/OBS）
- 返回临时 URL，客户端直接访问存储
- 有效期可配置（默认 1 小时）
- 存储不支持时返回 501

### 分片上传

#### 流程
1. **创建任务** (`POST /upload_task`)
   - 返回 `taskId` 和 `uploadId`
   - 设置过期时间（默认 24 小时）

2. **上传分片** (`PUT /upload_task/{taskID}?partNumber=N`)
   - 分片编号从 1 开始
   - 每个分片返回 `eTag`
   - 支持并发上传

3. **完成上传** (`POST /upload_task/{taskID}/complete`)
   - 提交所有分片的 `partNumber` 和 `eTag`
   - 服务端合并分片
   - 触发回调插件（如有配置）

4. **查询状态** (`GET /upload_task/{taskID}`)
   - 返回任务当前状态（PENDING/IN_PROGRESS/COMPLETED/FAILED/ABORTED）
   - 包含已上传分片信息

#### 任务状态机

```
PENDING ──upload──> IN_PROGRESS ──complete──> COMPLETED
   │                     │
   │                     │──fail──> FAILED
   │                     │
   └──────abort──────────┴────────> ABORTED
```

### 元数据查询

支持多种查询条件组合：
- **精确匹配**: `name`, `creator`, `size`
- **前缀匹配**: `name.prefix`, `creator.prefix`
- **范围查询**: `size.ge`, `size.le`, `created.before`, `created.after`
- **标签查询**: `tag.either`（包含任一）, `tag.both`（包含全部）
- **日期查询**: `created.at`, `created.before`, `created.after`

详见 [api.yml#L120-L218](./api.yml#L120-L218)

## 扩展点

### 自定义元数据
- `customMetaData` 字段支持任意 JSON 字符串
- 最大 10KB
- 可用于业务扩展（如：图片尺寸、视频时长等）

### 回调插件
- 上传完成后自动执行配置的插件
- 支持链式执行
- 插件可生成衍生文件（缩略图、转码等）
- 详见 [08-Callback执行器设计.md](./08-Callback执行器设计.md)

### 响应头扩展
- `X-ICC-META-*`: 自定义元数据头
- `X-File-Checksum`: 文件校验和
- `ETag`: 文件版本标识

## 使用示例

### 同步上传小文件

```bash
curl -X POST http://localhost:8080/file/api/v1/files/upload \
  -F "file=@/path/to/file.pdf" \
  -F "fileName=document.pdf" \
  -F "fileType=application/pdf" \
  -F "createdBy=user123" \
  -F "creatorName=张三" \
  -F "tag=work,important"
```

### 分片上传大文件

```bash
# 1. 创建任务
TASK_ID=$(curl -X POST http://localhost:8080/file/api/v1/files/upload_task \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "large_video.mp4",
    "fileType": "video/mp4",
    "fileSize": 104857600,
    "createdBy": "user123",
    "creatorName": "张三",
    "callbacks": [
      {"name": "thumbnail", "params": [{"key": "size", "value": "200x200"}]}
    ]
  }' | jq -r '.data.taskId')

# 2. 上传分片（示例：第1片）
ETAG=$(curl -X PUT "http://localhost:8080/file/api/v1/files/upload_task/$TASK_ID?partNumber=1" \
  --data-binary @part1.bin \
  -H "Content-Type: application/octet-stream" \
  | jq -r '.data.eTag')

# 3. 完成上传
curl -X POST "http://localhost:8080/file/api/v1/files/upload_task/$TASK_ID/complete" \
  -H "Content-Type: application/json" \
  -d "{
    \"completedParts\": [
      {\"partNumber\": 1, \"eTag\": \"$ETAG\"}
    ]
  }"
```

### 查询文件元数据

```bash
curl -X POST http://localhost:8080/file/api/v1/files/metadata \
  -H "Content-Type: application/json" \
  -d '{
    "name.prefix": "report",
    "created.after": "2024-01-01",
    "tag.both": "finance,2024"
  }' \
  -G --data-urlencode "page=0" \
       --data-urlencode "size=20" \
       --data-urlencode "sort=createdAt,desc"
```

## 注意事项

### 性能考虑
1. **文件大小限制**：
   - 同步上传：≤ 10MB
   - 分片上传：推荐 ≥ 5MB
   - 单分片：5MB - 5GB

2. **并发限制**：
   - 查询接口：最大 1000 条/页
   - 分片上传：建议并发 ≤ 10

3. **缓存策略**：
   - 元数据缓存：30 分钟
   - 任务状态缓存：24 小时
   - 使用 ETag 支持客户端缓存

### 安全建议
1. 网关层统一认证授权
2. 敏感操作记录审计日志
3. 限制上传文件类型（白名单）
4. 设置合理的速率限制
5. 定期清理过期任务

---

**完整 API 规范请参考**: [api.yml](./api.yml)
