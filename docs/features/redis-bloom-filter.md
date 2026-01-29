# Redis 布隆过滤器配置指南

## 概述

从本次更新开始，系统支持基于 Redis 的分布式布隆过滤器，用于在多节点部署时共享任务 ID 验证状态。

## 为什么需要 Redis 布隆过滤器？

### 问题背景

**本地布隆过滤器的局限性：**

```
节点 A: createTask(id=123) → 注册到本地布隆
节点 B: getTask(id=123)    → 本地布隆未注册 → 误判为不存在
```

在多节点部署时，本地布隆过滤器无法跨节点共享状态，导致：

- 其他节点无法快速过滤已存在的任务
- 布隆过滤器的防穿透效果大打折扣

### Redis 布隆过滤器的优势

| 特性           | 本地布隆               | Redis 布隆               |
| -------------- | ---------------------- | ------------------------ |
| **跨节点共享** | ❌ 各节点独立          | ✅ 全局一致              |
| **持久化**     | ❌ 进程重启丢失        | ✅ Redis 持久化          |
| **容量扩展**   | ⚠️ 受 JVM 内存限制     | ✅ Redis 内存可动态调整  |
| **性能**       | ✅ 本地内存访问 (~1ns) | ⚠️ 网络调用 (~1ms)       |
| **依赖**       | ✅ 无外部依赖          | ⚠️ 需要 Redis + Redisson |

## 配置方式

### 方式 1：使用 Redis 布隆（推荐）

**依赖要求：**

- Redis 服务可用
- Redisson 已配置

**application.yml:**

```yaml
# Redis 连接配置（使用 Redisson）
spring:
  redis:
    redisson:
      config: |
        singleServerConfig:
          address: "redis://localhost:6379"
          password: ${REDIS_PASSWORD:}
          database: 0
          connectionPoolSize: 32
          connectionMinimumIdleSize: 8

# 文件服务配置
file-service:
  bloom-filter:
    use-redis: true # 启用 Redis 布隆（默认）
    expected-insertions: 1000000 # 预期 100 万任务
    fpp: 0.01 # 1% 误判率
```

### 方式 2：使用本地布隆（单节点场景）

**application.yml:**

```yaml
file-service:
  bloom-filter:
    use-redis: false # 禁用 Redis，使用本地内存
    expected-insertions: 1000000
    fpp: 0.01
```

## 性能特性

### Redis 布隆过滤器的性能

**写入性能（register）：**

```
单次写入：~1ms（网络 RTT）
批量写入：支持 Pipeline，可达到 10,000 ops/s
```

**查询性能（mightExist）：**

```
单次查询：~1ms（网络 RTT）
缓存命中率 90% 时，实际影响：~0.1ms
```

### 降级策略

代码中内置了降级逻辑：

```java
@Override
public boolean mightExist(String taskId) {
    try {
        return bloomFilter.contains(taskId);
    } catch (Exception e) {
        log.error("Failed to check bloom filter: taskId={}", taskId, e);
        // 降级：Redis 故障时，认为可能存在（放行到下一层防护）
        return true;
    }
}
```

**降级行为：**

- Redis 故障时，布隆过滤器自动降级为"总是返回 true"
- 后续依赖空值缓存 + 数据库查询兜底
- 不影响业务可用性，只是失去防穿透优化

## 监控和调优

### 查看布隆过滤器状态

可以通过注入 `RedisBloomTaskIdValidator` 获取统计信息：

```java
@Autowired
private TaskIdValidator validator;

// 在管理接口或监控端点中暴露
if (validator instanceof RedisBloomTaskIdValidator redis) {
    long count = redis.getCount();       // 当前元素数量
    String info = redis.getInfo();       // 详细信息
}
```

### 调优参数

**预期插入数量 (expectedInsertions):**

- 设置为预期的最大任务数
- 过小：误判率升高
- 过大：浪费内存

**误判率 (fpp):**

- 推荐值：0.01（1%）
- 降低误判率会增加内存消耗
- 计算公式：`内存 ≈ -n * ln(p) / (ln2)^2`

**示例：**

```
100 万任务，1% 误判率：约 1.2MB 内存
100 万任务，0.1% 误判率：约 1.8MB 内存
1000 万任务，1% 误判率：约 12MB 内存
```

## 部署建议

### 单节点部署

```yaml
file-service:
  bloom-filter:
    use-redis: false # 使用本地布隆即可
```

### 多节点部署（无 Redis）

如果无法使用 Redis，可考虑：

1. **路由一致性：** 通过一致性哈希，确保相同 taskId 总是路由到相同节点
2. **直接移除布隆：** 依赖空值缓存 + 限流 + 数据库索引

### 多节点部署（有 Redis）

```yaml
file-service:
  bloom-filter:
    use-redis: true # 启用分布式布隆（推荐）
```

## 故障排查

### 问题 1：Redisson 连接失败

**错误日志：**

```
Unable to connect to Redis server: redis://localhost:6379
```

**解决方案：**

1. 检查 Redis 服务是否启动
2. 确认网络连通性
3. 验证 Redis 密码配置

### 问题 2：布隆过滤器初始化失败

**错误日志：**

```
Failed to initialize bloom filter: key=file-srv:task:bloom
```

**可能原因：**

- Redis 内存不足
- Redis 版本过低（需要 Redis 4.0+）
- 参数设置不合理（expectedInsertions 过大）

**解决方案：**

1. 检查 Redis 内存配置
2. 升级 Redis 版本
3. 调整布隆过滤器参数

### 问题 3：误判率过高

**症状：**

- 大量不存在的 taskId 通过了布隆过滤器检查

**解决方案：**

1. 检查是否达到预期插入数量上限
2. 降低 fpp 参数（增加内存换取更低误判率）
3. 重置布隆过滤器（清空 Redis key `file-srv:task:bloom`）

## 总结

- ✅ **多节点部署必须使用 Redis 布隆过滤器**
- ✅ 性能影响可接受（~1ms 网络延迟）
- ✅ 内置降级策略，不影响业务可用性
- ⚠️ 需要额外的 Redis 依赖和运维成本
