# 迭代 2 详细设计（可执行级别）

> 目标：在迭代 1 的同步小文件闭环基础上，补齐大文件分片上传（OBS），实现可恢复的分片任务生命周期、元数据与任务状态持久化，并提供对外 API。设计粒度可直接驱动编码。

## 1. 范围与产出

- 在现有 `StorageAdapter` 分片接口上完成华为 OBS 分片实现：initiate/uploadPart/complete/abort。
- 新增上传任务管理（UploadTask）：统一管理分片与“单片大文件”两类场景，涵盖任务创建、分片上传、完成合并、终止/超时清理、状态查询与回调占位。
- 对外 REST API：分片任务创建、上传分片、完成、取消、任务状态查询、已上传分片列表查询。
- 业务校验：分片大小、分片序号、最大分片数、并发与幂等约束。
- 元数据落库：分片任务表 + 分片明细表（或 JSON 持久化），在 complete 时写入 FileEntity 并清理分片记录。
- 观测性：最少日志，关键阶段 info + error；任务状态推进时打点。
- 向后兼容：同步小文件接口保持不变，10MB 阈值仍由 Handler 控制，>10MB 走分片流程；若客户端不显式分片且 size>5MB，可视为单片分片任务（partNumber=1）。

## 2. 非目标

- 分片并行度调度与速率控制（由客户端控制）。
- 分片下载/断点续传下载（留迭代 3）。
- 跨云复制、服务端合并校验强一致性（仅校验分片计数和 ETag 列表）。

## 3. 接口与模型

### 3.1 StorageAdapter（现有签名，需落实现）

```java
String initiateMultipartUpload(FileMetadata meta);
String uploadPart(String uploadId, int partNumber, InputStream in);
void completeMultipartUpload(String uploadId, List<PartETag> parts);
void abortMultipartUpload(String uploadId);
```

- `initiateMultipartUpload`：返回 uploadId，同时约定 objectKey = meta.objectKey 或服务端生成后持久化。
- `uploadPart`：返回 part ETag（OBS 返回 `etag`）。
- `completeMultipartUpload`：调用 OBS complete；失败抛 FileServiceException。
- `abortMultipartUpload`：调用 OBS abort，幂等。

### 3.2 DTO

- `FileMetadata`（沿用迭代 1）：filename, size, contentType, creator, tags, bucket, objectKey(optional)。
- `PartETag`：partNumber, eTag。
- UploadTask 视图（新建 DTO，统一分片/单片）：
  - `uploadId`，`fkey/objectKey`，`status`（PENDING/UPLOADING/COMPLETED/ABORTED/FAILED），`totalParts`（可选），`uploadedParts`，`partSize`, `expiresAt`，`callbackUrl`（占位，可选），`createdAt/updatedAt`。
  - `PartItem`：partNumber, etag, size, uploadedAt；当为单片大文件且客户端未分片时，视为 partNumber=1。

### 3.3 对外接口说明

- 不新增 Restful API 路由，复用现有 `/api/v1/files/...` 体系。迭代 2 仅扩展内部接口（Handler/Service/Adapter）以支撑分片能力。
- 如需暴露分片能力，可在后续迭代单独补充 Controller 层路由，本迭代不作为必需项。

## 4. 业务规则与校验

- 大小阈值：Handler 继续使用 10MB 阈值，>10MB 走分片。
- 分片大小：默认 8MB；最小 5MB（OBS 要求除最后一片）；最大 5GB（与 OBS 官方一致，可通过配置收紧）。
- 分片序号：正整数，允许乱序上传；Complete 需按 partNumber 排序提交。
- 最大分片数：10000（OBS 上限），超出拒绝。
- 任务超时：默认 24h 过期；过期后后台可定时清理（迭代 2 实现手动 abort，定时清理可选）。
- 幂等性：
  - initiate：同一 objectKey 可允许重复创建 uploadId（由客户端管理唯一性）；服务端记录 status=PENDING。
  - uploadPart：同一 uploadId + partNumber 重传覆盖旧 etag/size。
  - complete：只允许在 UPLOADING 状态执行；重复 complete 返回首个成功结果；非法状态抛异常。
  - abort：允许 PENDING/UPLOADING 调用；COMPLETED/ABORTED/FAILED 返回幂等。
- 完成校验：
  - 至少 1 片；
  - parts 列表必须连续覆盖 1..N；
  - 记录中所有已上传片的 etag 必须在 complete 请求中出现。

## 5. 数据模型与持久化

- 新表/实体 `UploadTask`（示例字段）：
  - id (PK), uploadId (unique), objectKey(fkey), bucket, filename, size, contentType, creator, tags, partSize, totalParts(optional), status, expiresAt, callbackUrl(optional), createdAt, updatedAt。
- 新表/实体 `UploadPart`（或 JSON 存于 task.row）：
  - id (PK), uploadId (FK), partNumber, etag, size, uploadedAt, checksum(optional)。
  - 当为“单片大文件”时，只有 partNumber=1 记录。
- 完成流程：
  - 调用 Adapter.complete 成功后，写入 FileEntity（复用 MetaService.save），状态置 COMPLETED，随后删除 `MultipartPart` 记录或标记归档。
- 终止流程：
  - 调用 Adapter.abort 后，将任务标记 ABORTED，清理 part 记录。

## 6. 模块职责与变更

### 6.1 Handler 层

- 新增 UploadTaskHandler（或扩展 FilesHandler）：
  - initiateUploadTask(FileInfo req, partSize?): 创建 UploadTask，持久化，调用 adapter.initiate 返回 uploadId/fkey。
  - uploadPart(uploadId, partNumber, MultipartFile part): 校验 size/序号，调用 adapter.uploadPart，保存 etag；若是单片大文件，则 partNumber=1。
  - completeUpload(uploadId, parts): 校验状态与分片完整性，调用 adapter.complete，写入 FileEntity，更新状态。
  - abortUpload(uploadId): 调用 adapter.abort，更新状态。
  - getUploadStatus(uploadId): 查询任务+已上传分片列表。

### 6.2 Service 层

- StorageService 扩展分片方法签名（对齐 Handler）：
  - initiateUploadTask(FileInfo, partSize?)
  - uploadPart(uploadId, partNumber, InputStream, size)
  - completeUpload(uploadId, List<PartETag>)
  - abortUpload(uploadId)
- MetaService 保持不变；新增 UploadTaskService/Repository 管理任务与分片记录。

### 6.3 Adapter 实现（OBS）

- initiateMultipartUpload：
  - 构造 `InitiateMultipartUploadRequest(bucket, objectKey)`；返回 uploadId。
- uploadPart：
  - 使用 `UploadPartRequest`，设置 partNumber、uploadId、input、partSize（从 request.contentLength 透传）。
  - 返回 etag。
- completeMultipartUpload：
  - 将 `List<PartETag>` 转为 `List<PartEtag>`（OBS SDK）并调用 `completeMultipartUpload`。
- abortMultipartUpload：直接调用 `abortMultipartUpload`，404/NoSuchUpload 视为幂等成功。
- 异常转换：捕获 ObsException -> FileServiceException(Failures.NOT_FOUND | INTERNAL_ERROR)。

## 7. 状态机（UploadTask）

- 状态：PENDING -> UPLOADING -> COMPLETED | ABORTED | FAILED
- 迁移规则：
  - initiate: PENDING
  - 首次 uploadPart: UPLOADING（若 PENDING 则置为 UPLOADING）
  - complete: COMPLETED（成功），失败则 FAILED（可重试 complete）
  - abort: ABORTED（终态）
- 单片大文件：视为只有 partNumber=1 的 UploadTask，流程同上。
- 过期：EXPIRED 视为 ABORTED（可在定时任务中实现）。

## 8. 配置

```yaml
storage:
  multipart:
    part-size: 8MB # 默认分片大小
    min-part-size: 5MB # OBS 要求（除最后一片）
    max-part-size: 5GB # 与 OBS 官方保持一致
    max-parts: 10000
    expire-hours: 24
```

## 9. 校验与错误码

- 400：partSize < min 或 > max；partNumber <=0；parts 列表缺失或不连续。
- 404：uploadId 不存在或已过期；对象不存在（complete/download 场景）。
- 409：状态不允许当前操作（如 COMPLETED 后再次 complete/abort）。
- 500：OBS SDK 调用异常。

## 10. 观测与日志

- initiate/uploadPart/complete/abort 成功记录 info：uploadId, fkey, partNumber, etag, cost。
- 失败记录 error，包含 responseCode/errorCode。

## 11. 测试计划

- Handler/Service 单测：
  - initiate -> state=PENDING，生成 uploadId。
  - uploadPart 覆盖重传同 partNumber，etag 更新。
  - complete 校验缺片、乱序、重复 complete。
  - abort 幂等。
- Adapter 集成（可选 MinIO 仿 OBS）：
  - end-to-end 分片上传 3 片，complete 后下载校验大小与 etag。
- 过期策略：手动模拟 expiresAt < now 时拒绝上传/complete。

## 12. 编码顺序建议

1. 定义实体/Repository：MultipartTask, MultipartPart。
2. 定义 DTO + 新的 Handler/Service 接口。
3. 实现 OBS 分片方法。
4. 实现 Service/Handler + Controller 路由与校验。
5. 增加定时清理（可选），单测与简单集成验证。

## 13. DoD（Definition of Done）

- mvn test -DskipIT 通过。
- 通过模拟（或 MinIO）完成 10MB+ 文件分片上传合并验证。
- REST API 文档与实现一致，错误码覆盖主要非法场景。
- 分片任务状态可查询，complete/abort 幂等。
