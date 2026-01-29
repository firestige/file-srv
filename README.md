# ICC File Service - Iteration 1

## 完成功能

- ✅ StorageAdapter 接口定义（同步上传/下载/删除/exists/预签名）
- ✅ HcsObsAdapter 实现（华为云 OBS 适配器）
- ✅ StorageService 实现（存储服务层）
- ✅ FileEntity 和 FileRepository（JPA 元数据持久化）
- ✅ MetaService 实现（元数据服务）
- ✅ FilesHandler 实现（业务处理层）
- ✅ FilesController 实现（REST API）
- ✅ 全局异常处理
- ✅ 配置文件（MySQL + H2 测试）

## 运行要求

### 数据库

- **开发环境**: MySQL 8.0+
- **测试环境**: H2 内存数据库（自动配置）

### 环境变量

```bash
# MySQL
export DB_PASSWORD=your_password

# Redis (可选，iteration 1未使用)
export REDIS_PASSWORD=your_password

# 华为云OBS
export OBS_BUCKET=your-bucket
export OBS_ENDPOINT=https://obs.cn-north-4.myhuaweicloud.com
export OBS_ACCESS_KEY=your-access-key
export OBS_SECRET_KEY=your-secret-key
```

## 快速开始

### 1. 编译项目

```bash
mvn clean install
```

### 2. 运行应用

```bash
cd icc-file-srv-bootstrap
mvn spring-boot:run
```

### 3. 测试接口

#### 上传文件

```bash
curl -X POST http://localhost:8080/api/v1/files/upload \
  -F "file=@test.txt" \
  -F "createdBy=user123" \
  -F "tags=document,test"
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fkey": "e7b8c9d0-1234-5678-90ab-cdef12345678",
    "fileName": "test.txt",
    "fileSize": 1024,
    "fileType": "text/plain",
    "fileUrl": "/api/v1/files/e7b8c9d0-1234-5678-90ab-cdef12345678",
    "createdBy": "user123",
    "tags": "document,test"
  }
}
```

#### 下载文件

```bash
curl -O -J http://localhost:8080/api/v1/files/e7b8c9d0-1234-5678-90ab-cdef12345678
```

#### 删除文件

```bash
curl -X DELETE http://localhost:8080/api/v1/files/e7b8c9d0-1234-5678-90ab-cdef12345678
```

## API 端点

| 方法   | 路径                     | 说明                  | 状态           |
| ------ | ------------------------ | --------------------- | -------------- |
| POST   | `/api/v1/files/upload`   | 同步上传文件（≤10MB） | ✅             |
| GET    | `/api/v1/files/{fkey}`   | 下载文件              | ✅             |
| DELETE | `/api/v1/files/{fkey}`   | 删除文件              | ✅             |
| POST   | `/api/v1/files/metadata` | 查询文件元数据        | ⏳ Iteration 2 |

## 架构说明

```
Controller (REST API)
    ↓
Handler (业务编排)
    ↓
Service 层
  ├─ MetaService (元数据: DB)
  └─ StorageService (存储: Adapter)
      ↓
  StorageAdapter (SPI)
      ↓
  HcsObsAdapter (华为云OBS)
```

## 已知限制（Iteration 1）

1. 分片上传接口已定义但未实现（抛 UnsupportedOperationException）
2. 元数据查询接口返回 501 Not Implemented
3. Redis 缓存未启用
4. 回调插件机制未实现

## 下一步（Iteration 2）

- [ ] 实现分片上传完整链路
- [ ] 实现上传任务管理
- [ ] 实现回调插件框架
- [ ] 启用 Redis 缓存
- [ ] 完善元数据查询功能
