# 迭代 3 详细设计（元数据与查询优化）

> 目标：完成元数据/任务/分片实体的持久化建模，提供可分页的元数据查询与筛选，落实 Redis + Caffeine 双层缓存策略与失效方案，补齐文件/任务查询 API，提升查询性能与一致性。设计粒度可直接驱动编码。

## 1. 范围与产出

- 完成 `FileEntity`、`UploadTaskEntity`、`UploadPartEntity` 的 JPA 实体、Repository、Flyway/Liquibase 建表脚本（若无迁移工具，可用 schema.sql）。
- 落实 `MetaService` 的 CRUD 与查询：支持文件名模糊、创建者、标签包含、时间范围、内容类型、排序、分页；缓存读写闭环。
- UploadTask/Part 查询能力：按 taskId 获取任务详情与分片列表，支持状态过滤 + 分页（用于运维/后台）。
- 双层缓存策略：热点元数据 Caffeine（短路径）、Redis（跨实例），失效与回填策略明确；查询结果可选分页缓存。
- Controller/Handler 补齐查询接口：`/api/v1/files/metadata`（分页）、`/api/v1/files/upload-tasks/{taskId}`、后台任务列表（可放 `/api/v1/files/upload-tasks` GET，带 status/时间范围过滤）。
- 指标与日志：缓存命中率、DB 查询耗时、热点回填、缓存击穿/雪崩防护日志。

## 2. 非目标

- 不做全文/前缀搜索引擎（如 ES）；优先用 DB 索引，若不足仅给出评估结论。
- 不处理分布式事务；采用最终一致性（写 DB → 失效缓存 → 回填）。
- 不实现高阶分析报表（下载量、热度排行）。

## 3. 接口与模型变更

### 3.1 DTO

- `FileInfo`：补充 `createdAt`、`updatedAt`、`bucket`、`storageType`（枚举字符串）。
- `MetaQueryParams`：
  - `filename`（前缀/模糊，默认前缀 LIKE）。
  - `creator`。
  - `tags`（列表，支持“包含任一”或“包含全部”模式，默认任一）。
  - `contentType`（前缀匹配，如 image/\*）。
  - `createdFrom`/`createdTo`。
  - `sortBy`（createdAt|filename|size，默认 createdAt），`sortOrder`（ASC|DESC，默认 DESC）。
  - `page`、`pageSize`（默认 1/20，pageSize 上限 100）。
- `TaskInfo`/`UploadTaskDetail`：补充 `bucket`、`uploadId`、`uploadedParts` 数量、`fileFkey`。
- `UploadPartInfo`：对外查询使用（partNumber, eTag, size, uploadedAt）。

### 3.2 API

- **POST /api/v1/files/metadata**：改为分页返回 `Result<Page<FileInfo>>`；兼容老调用（默认第一页）。
- **GET /api/v1/files/upload-tasks/{taskId}**：返回 `UploadTaskDetail`（含分片列表）。
- **GET /api/v1/files/upload-tasks**（后台/运维可选）：查询任务列表，过滤 `status`、`createdFrom/To`，分页返回。

## 4. 数据模型与索引

- `FileEntity`

  - `id (PK, bigint auto)`
  - `fkey (varchar, unique)`
  - `filename (varchar, idx filename_prefix)`
  - `size (bigint)`
  - `contentType (varchar, idx)`
  - `creator (varchar, idx)`
  - `tags (varchar)` // 逗号分隔或 JSON 数组（选型见下）
  - `bucket (varchar)`
  - `storageType (varchar)`
  - `url (varchar)`
  - `createdAt (timestamp, idx createdAt_desc)`
  - `updatedAt (timestamp)`
  - 索引建议：
    - `uk_fkey`
    - `idx_creator_createdAt`（creator + createdAt DESC）
    - `idx_contentType_createdAt`
    - `idx_filename_prefix`（前缀/like 优化，可用 btree 前缀）
    - 标签：若 tags 需包含查询，使用 JSON/ARRAY + GIN（Postgres）或 FIND_IN_SET（MySQL，性能一般）；默认 MySQL 场景使用逗号分隔并通过 LIKE '%tag%' + 限制规范。

- `UploadTaskEntity`

  - `id (PK)`
  - `taskId (varchar, unique)`
  - `uploadId (varchar)`
  - `fileFkey (varchar, fk -> FileEntity.fkey, 可为空直到完成)`
  - `bucket (varchar)`
  - `status (varchar)`
  - `expiresAt (timestamp)`
  - `callbackNames (varchar/json)`
  - `createdAt (timestamp)`
  - `updatedAt (timestamp, idx status_updatedAt)`

- `UploadPartEntity`
  - `id (PK)`
  - `taskId (fk -> UploadTask.taskId, idx)`
  - `partNumber (int, idx taskId_partNumber unique)`
  - `eTag (varchar)`
  - `size (bigint)`
  - `uploadedAt (timestamp)`

## 5. 缓存策略

- 采用「Cache Aside」：读优先缓存 → 失效 DB → 回填；写/删：先写 DB 成功后失效缓存。
- 键设计：
  - 文件元数据：`file:meta:{fkey}` → FileInfo，TTL 30 min，Caffeine 同步持久 5 min，容量 LRU 5k。
  - 查询分页：`file:query:{hash(params)}` → Page<FileInfo>，TTL 5 min，命中率低可开关配置（默认开启）。
  - 任务：`upload:task:{taskId}` TTL 24h；分片：`upload:task:{taskId}:parts`（可存列表 JSON，或每片键 `:part:{n}`）。
- 失效策略：
  - 上传完成：删除 task 缓存 + 分片缓存；写 FileEntity 后删除/刷新 `file:meta`，删除相关 query 缓存（可用前缀 tag 或版本号）。
  - 删除文件：先删存储 → 删 DB→ 删缓存（meta + query）。
- 防击穿/雪崩：
  - Caffeine 兜底防击穿；Redis TTL 加随机抖动（±10%）。
  - 可选「互斥锁」防并发回源：`mutex:file:meta:{fkey}`，短 TTL 5s。

## 6. Service 与 Handler 设计

- `MetaService`

  - `FileInfo save(FileInfo)`：写 DB，失效 meta/query 缓存。
  - `Optional<FileInfo> getByFkey(String fkey)`：Caffeine -> Redis -> DB，命中回填；落日志含命中来源。
  - `Page<FileInfo> query(MetaQueryParams)`：可选查询缓存；若命中返回缓存，未命中查 DB；结果回填缓存；排序分页由 DB 完成。
  - `void delete(String fkey)`：删除存储前检查存在性，删除 DB 后失效缓存。

- `UploadTaskService`

  - `UploadTaskDetail get(String taskId)`：Redis -> DB，附带分片列表查询。
  - `Page<UploadTaskDetail> query(TaskQueryParams)`（后台可选）：status/time 过滤 + 分页。
  - 内部更新：complete/abort 时刷新 Redis 缓存。

- `UploadPartService`（可并入 TaskService）

  - `List<UploadPartInfo> listByTask(String taskId)`：DB 读取 + 可选 Redis 列表缓存。

- Handler 层：
  - `FilesHandler.queryMeta(MetaQueryParams)`：调用 MetaService.query；对不合法 pageSize 返回 400。
  - `UploadTaskHandler.getTask(taskId)`：调用 TaskService.get，返回任务 + 分片。
  - 后台任务列表接口仅对内使用，默认关闭，需配置开关。

## 7. 查询实现要点（MySQL 假设）

- filename 模糊：`WHERE filename LIKE ?%`，避免前置 `%`；必要时引入前缀索引或倒排方案评估。
- tags：`tags LIKE '%tag1%'` 性能有限；建议规范标签格式（`,`包裹），或使用 JSON 数组 + GIN/索引（若数据库支持），在配置中声明所用策略。
- contentType 前缀：`contentType LIKE 'image/%'` 走普通索引前缀。
- 时间范围：`createdAt BETWEEN ...`，索引覆盖。

## 8. 一致性与并发

- 写路径：DB 成功后删除缓存；失败回滚不删缓存。
- 回填幂等：相同 fkey 并发读取时，通过 Caffeine 命中减少回源；若启用互斥锁防止缓存击穿。
- 任务完成：先完成存储合并 → 写 FileEntity → 更新 Task 状态 COMPLETED → 失效/删除 Task & Part 缓存 → 写 meta 缓存。

## 9. 监控与日志

- 指标（Micrometer）：
  - `file.meta.cache.hit/miss`（labels: tier=caffeine|redis）。
  - `file.meta.db.query.timer`、`file.meta.save.timer`。
  - `upload.task.cache.hit/miss`。
- 日志：
  - 缓存回源事件 warn（超阈值）；缓存批量失效 info。
  - 异常统一转 `FileServiceException`，附 errorCode。

## 10. 测试计划

- `MetaService` 单测：
  - get 命中缓存、未命中回源、缓存回填。
  - query 分页/排序/过滤组合；非法参数校验。
  - save/delete 后缓存失效验证。
- `UploadTaskService` 单测：任务查询附带分片列表；状态过滤分页。
- 集成测试（可用 H2+Redis mock）：
  - 写入文件后查询分页命中。
  - 并发读同一 fkey，验证互斥/无击穿。
- 性能冒烟：1k/10k 文件，查询 95 分位耗时 < 50ms（内网环境，缓存命中）。

## 11. 编码顺序建议

1. 编写 Flyway/Liquibase 迁移或 schema.sql，创建 3 张表与索引。
2. 定义实体 + Repository；补充 DTO/QueryParams。
3. 实现 MetaService with 缓存策略；实现 Task/Part 查询。
4. Controller/Handler API 补齐分页与任务查询；参数校验。
5. 指标埋点 + 单测/集成测试；必要时补充配置项（缓存开关、TTL、互斥锁开关）。

## 12. DoD

- mvn test -DskipIT 通过；新增单测覆盖查询与缓存失效。
- 本地/测试环境验证：分页查询、任务查询返回正确；缓存命中率指标可见。
- 数据表/索引创建并在 README/部署文档记录；默认配置可运行。
