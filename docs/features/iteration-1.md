# 迭代1 详细设计（可执行级别）

> 目标：打通最小可用链路——同步上传/下载 + HcsObsAdapter 基础实现 + 元数据持久化骨架 + 基础异常与配置。文档细化到可直接驱动编码。

## 1. 范围与产出
- 完成 `StorageAdapter` 接口定义与默认契约（异常/幂等/参数校验）。
- 实现 `HcsObsAdapter` 的 Happy Path（同步上传/下载/删除/exists/预签名，分片接口签名先占位返回 `UnsupportedOperationException`）。
- 打通同步上传/下载链路：Controller → Handler → Service → Adapter → OBS。
- 落库元数据：定义 `FileEntity`、`FileRepository`，实现 `MetaService.save/get/delete` 基础流程。
- 基础配置：`storage.type=hcs-obs` 自动装配；OBS 连接属性；数据源与 Redis 可留占位默认。
- 统一异常：SDK/IO 异常转换为 `FileServiceException`，错误码用 `Failures`。

## 2. 接口定义（代码需补全）
### 2.1 StorageAdapter
```java
public interface StorageAdapter {
    /** 同步上传小文件（≤10MB），返回对象键 fkey */
    String uploadFile(InputStream in, FileMetadata meta);

    /** 直连下载，返回输入流（调用方负责关闭） */
    InputStream downloadFile(String fkey);

    /** 生成预签名 URL，expiration 可为 null 表示使用默认配置 */
    String generatePresignedUrl(String fkey, Duration expiration);

    /** 删除对象，不存在时应幂等返回 */
    void deleteFile(String fkey);

    /** 存在性检查 */
    boolean exists(String fkey);

    // 分片相关：迭代1占位，抛 UnsupportedOperationException
    String initiateMultipartUpload(FileMetadata meta);
    String uploadPart(String uploadId, int partNumber, InputStream in);
    void completeMultipartUpload(String uploadId, List<PartETag> parts);
    void abortMultipartUpload(String uploadId);
}
```

> 辅助 DTO：
> - `FileMetadata`：filename, size, contentType, creator, tags(List<String>), bucket, objectKey(optional, = fkey)。
> - `PartETag`：partNumber, eTag。

### 2.2 Service 层
- `StorageService`
  - `String upload(FileInfo fileInfo, InputStream in)` → 返回 fkey
  - `InputStream download(String fkey)`
  - `String presign(String fkey, Duration expiration)`
  - `void delete(String fkey)`
  - `boolean exists(String fkey)`

- `MetaService`
  - `FileInfo save(FileInfo fileInfo)`
  - `FileInfo findByFkey(String fkey)`
  - `void deleteByFkey(String fkey)`

> 迭代1 仅实现上述方法；查询/分页留空实现或 `UnsupportedOperationException`。

### 2.3 Handler 层
- `FilesHandler`
  - `FileInfo upload(FileInfo req, MultipartFile file)`
  - `Resource download(String fkey)`
  - `void delete(String fkey)`
  - `String presign(String fkey, Duration expiration)`

> 参数校验：file 非空、大小 ≤10MB；contentType 透传；fkey 生成策略参见 4.2。

### 2.4 Controller 层（补齐实现）
- `FilesController`
  - GET `/api/v1/files/{fkey}` → 调用 handler.download，包装 ResponseEntity，设置 `Content-Disposition` filename、`Content-Type`、`Content-Length`（如可用）。
  - POST `/api/v1/files/upload` → 调用 handler.upload，返回 Result<FileInfo>。
  - DELETE `/api/v1/files/{fkey}` → handler.delete → Result<Void>。
  - POST `/api/v1/files/metadata` → 迭代1可返回 `Result.failure(501, "Not implemented")`。

## 3. 数据流与模块通信（同步上传/下载）
### 3.1 同步上传（Happy Path）
1. Controller 接收 MultipartFile + FileInfo（部分字段可缺省）。
2. Handler 校验大小 ≤10MB，补齐元数据（contentType、filename、size）。
3. Handler 调用 StorageService.upload：
   - 构造 FileMetadata（含 bucket、objectKey/fkey）。
   - 调用 Adapter.uploadFile，返回 fkey。
4. Handler 构造完整 FileInfo（写入 fkey、url 如果适配器可返回；或使用 `presign` 生成短期 URL）。
5. MetaService.save：转换为 FileEntity 落库。
6. 返回 FileInfo。

### 3.2 下载
1. Controller 调用 Handler.download(fkey)。
2. Handler 先尝试 MetaService.findByFkey（可选，获取 contentType/filename/size）。
3. 调用 StorageService.download 获取 InputStream，包装为 Resource。
4. 构建 ResponseEntity，设置 headers：
   - `Content-Disposition: attachment; filename="{filename}"`
   - `Content-Type: {contentType or application/octet-stream}`
   - `Content-Length: {size if known}`。

### 3.3 删除
1. Handler.delete 调用 StorageService.delete。
2. 调用 MetaService.deleteByFkey。

### 3.4 预签名
1. Handler.presign → StorageService.presign → Adapter.generatePresignedUrl。

## 4. 设计细节
### 4.1 异常与错误码
- 适配器层捕获 SDK/IO 异常，转换为 `FileServiceException(Failures.INTERNAL_ERROR)`。
- 不存在的对象：delete/exists/download 时若 404，应转为 `FileServiceException(Failures.NOT_FOUND)` 或返回空（exists=false）。
- 控制器统一返回 `Result.failure(code, message)`。

### 4.2 fkey 生成策略（迭代1）
- 如果请求带 fkey，直接使用。
- 否则由 StorageAdapter 决定：
  - 默认使用 `UUID.randomUUID()` 作为对象键；
  - OBS 侧 objectKey = fkey；可选前缀 `{yyyy/MM/dd}/`。

### 4.3 HcsObsAdapter 伪代码（Happy Path）
```java
class HcsObsAdapter implements StorageAdapter {
    private final ObsClient client;
    private final String bucket;
    private final Duration defaultPresignTtl;

    public String uploadFile(InputStream in, FileMetadata meta) {
        String key = ensureKey(meta);
        PutObjectRequest req = new PutObjectRequest(bucket, key, in);
        req.setContentType(meta.getContentType());
        req.setMetadata(buildUserMeta(meta));
        client.putObject(req);
        return key; // 作为 fkey 返回
    }

    public InputStream downloadFile(String fkey) {
        ObsObject obj = client.getObject(bucket, fkey);
        return obj.getObjectContent();
    }

    public String generatePresignedUrl(String fkey, Duration expiration) {
        long ttl = Optional.ofNullable(expiration).orElse(defaultPresignTtl).toSeconds();
        TemporarySignatureRequest req = new TemporarySignatureRequest(HttpMethodEnum.GET, ttl);
        req.setBucketName(bucket);
        req.setObjectKey(fkey);
        TemporarySignatureResponse resp = client.createTemporarySignature(req);
        return resp.getSignedUrl();
    }

    public void deleteFile(String fkey) {
        client.deleteObject(bucket, fkey);
    }

    public boolean exists(String fkey) {
        return client.doesObjectExist(bucket, fkey);
    }

    // 分片相关抛 UnsupportedOperationException (迭代1)
}
```

### 4.4 MetaService 实体与映射
- `FileEntity` 字段：id(PK), fkey(unique), filename, size, contentType, creator, tags(json/text), url, storageType, bucket, createdAt, updatedAt。
- Repository：`Optional<FileEntity> findByFkey(String fkey); void deleteByFkey(String fkey);`
- DTO 转换：在 MetaService 内部完成 Entity ↔ FileInfo。

### 4.5 Handler 关键校验
- 上传：file 非空，size ≤10MB；contentType 取自 MultipartFile；filename 取上传名或请求体；
- 下载：若 MetaService 未找到，可继续尝试存储下载，找不到则 404。
- 删除：先存储层 delete，再删除元数据；存储 404 时抛 NOT_FOUND。

## 5. 活动图（文本版）
### 5.1 同步上传
```
[Client] -> (POST /files/upload)
Controller -> validate size<=10MB
Controller -> Handler.upload
Handler -> build FileMetadata & FileInfo
Handler -> StorageService.upload
StorageService -> Adapter.uploadFile -> OBS putObject
<- fkey
Handler -> MetaService.save(FileInfo)
<- saved FileInfo
Controller -> Result.success(FileInfo)
```

### 5.2 下载
```
[Client] -> (GET /files/{fkey})
Controller -> Handler.download
Handler -> MetaService.findByFkey? (optional)
Handler -> StorageService.download
StorageService -> Adapter.downloadFile -> OBS getObject
<- InputStream
Handler -> build Resource + headers
Controller -> ResponseEntity<Resource>
```

### 5.3 删除
```
[Client] -> (DELETE /files/{fkey})
Controller -> Handler.delete
Handler -> StorageService.delete -> Adapter.delete -> OBS deleteObject
Handler -> MetaService.deleteByFkey
Controller -> Result.success()
```

## 6. 状态机（分片任务占位）
> 迭代1仅同步上传，分片接口返回 UnsupportedOperationException。但预留状态机，迭代2实现。

状态：`PENDING -> IN_PROGRESS -> COMPLETED | FAILED | ABORTED`
- `createTask`：PENDING
- `uploadPart`：IN_PROGRESS（若未创建则异常）
- `complete`：COMPLETED（全部分片合并成功）
- `abort`：ABORTED
- 异常：任何步骤失败标记 FAILED（可重试策略后续定义）

## 7. 配置与自动装配
- `application.yml` 示例：
```yaml
storage:
  type: hcs-obs
  bucket: your-bucket
  endpoint: https://obs.xxx
  access-key: ${OBS_AK}
  secret-key: ${OBS_SK}
  presign-expiration: 3600s
spring:
  datasource: ...   # 占位
  redis: ...        # 占位
```
- AutoConfig：
  - 条件：`@ConditionalOnProperty(name="storage.type", havingValue="hcs-obs")`
  - Bean：`ObsClient`、`HcsObsAdapter`、`StorageService` 默认实现绑定该适配器。

## 8. 编码顺序建议
1) 定义辅助 DTO：`FileMetadata`, `PartETag`。
2) 完成 `StorageAdapter` 接口签名。
3) 实现 `HcsObsAdapter` Happy Path；分片方法抛 `UnsupportedOperationException`。
4) 实现 `StorageService` 默认实现（组合 Adapter）。
5) 定义 `FileEntity` + `FileRepository`，实现 `MetaService` 基础方法。
6) 补全 `FilesHandler` 与 `FilesController` 逻辑和校验。
7) 简单单测：Mock Adapter 验证 Handler/Service 交互；可选集成测试（需 OBS/MinIO）。

## 9. 完成定义（DoD）
- 本地可编译通过（mvn test -DskipIT）。
- 同步上传/下载/删除/存在性检查可在本地用 Mock Adapter 跑通单测。
- HcsObsAdapter 编译通过，具备配置占位，分片方法明确抛出 UnsupportedOperationException。
- 文档与代码接口一致，异常路径明确。
