# Spring Boot 自动配置边界问题

## 问题标签
`Spring Boot` `AutoConfiguration` `依赖方向` `模块化` `装配层`

## 问题现象

### 依赖关系异常
在 `file-srv-spi-redis` 模块的 `pom.xml` 中，发现了对 `file-srv-autoconfiguration` 模块的依赖：

```xml
<!-- file-srv-spi-redis/pom.xml -->
<dependencies>
    <dependency>
        <groupId>tech.icc</groupId>
        <artifactId>file-srv-autoconfiguration</artifactId>  <!-- ❌ SPI 反向依赖装配层 -->
        <version>${revision}</version>
    </dependency>
</dependencies>
```

### 问题代码
SPI 模块内部包含了 `@AutoConfiguration` 配置类：

```java
// file-srv-spi-redis/.../config/RedisSpiAutoConfiguration.java
package tech.icc.filesrv.spi.redis.config;

@AutoConfiguration(after = FileServiceAutoConfiguration.class)  // ❌ 引用装配层类
@EnableConfigurationProperties(FileServiceProperties.class)     // ❌ 引用装配层配置
public class RedisSpiAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(IdempotencyChecker.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public IdempotencyChecker redisIdempotencyChecker(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyChecker(redisTemplate);
    }
    
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnProperty(name = "file-srv.bloom-filter.use-redis", havingValue = "true")
    public TaskIdValidator redisBloomTaskIdValidator(RedissonClient client, 
                                                     FileServiceProperties props) {
        return new RedisBloomTaskIdValidator(client, ...);
    }
}
```

同样的问题也出现在 `file-srv-adapter-hcs` 模块：

```java
// file-srv-adapter-hcs/.../config/ObsAutoConfiguration.java
@Configuration
@ConditionalOnProperty(prefix = "storage.obs", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ObsProperties.class)
public class ObsAutoConfiguration {
    // Bean 定义...
}
```

### 边界破坏的表现
1. **依赖方向错误**：SPI/adapter 模块依赖 autoconfiguration 模块
2. **职责混乱**：实现模块承担了装配层职责
3. **灵活性丧失**：无法独立替换或组合 SPI 实现

## 根本原因

### Spring Boot 装配哲学

Spring Boot 的自动配置遵循"**装配层依赖被装配模块**"的原则：

```
┌─────────────────────────────────────────────────────────────┐
│                    autoconfiguration                         │
│                       （装配层）                              │
│  - @AutoConfiguration                                        │
│  - @ConditionalOnClass / @ConditionalOnBean                 │
│  - @EnableConfigurationProperties                           │
│  - 选择性加载、条件装配                                       │
└─────────────────────┬───────────────────────────────────────┘
                      │ 依赖（可选）
    ┌─────────────────┼─────────────────┐
    ▼                 ▼                 ▼
┌─────────┐    ┌───────────┐    ┌─────────────┐
│  core   │    │  spi-*    │    │  adapter-*  │
│ 业务逻辑 │    │  SPI实现  │    │  适配器实现  │
└─────────┘    └───────────┘    └─────────────┘
```

### 错误的依赖关系
```
file-srv-spi-redis
    ├── 依赖 file-srv-common           ✅ 正确（获取契约）
    └── 依赖 file-srv-autoconfiguration ❌ 错误！（反向依赖装配层）

file-srv-adapter-hcs
    ├── 依赖 file-srv-common           ✅ 正确
    └── 内置 @AutoConfiguration        ❌ 错误！（职责越界）
```

### 根因总结

**将"装配逻辑"放在了"被装配模块"中，违背了 Spring Boot 的设计哲学。**

Spring Boot 的核心理念：
1. **自动配置是可选的装配层**：由最终应用决定是否引入
2. **被装配模块应保持纯净**：只提供实现类，不关心如何被装配
3. **条件装配由装配层控制**：`@ConditionalOnClass` 检测类是否存在，决定是否加载

## 解决方案

### 设计思路
将所有 `@AutoConfiguration` 迁移到 `file-srv-autoconfiguration` 模块，SPI/adapter 模块只保留实现类。

```
┌────────────────────────────────────────────────────────────────┐
│                  file-srv-autoconfiguration                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  FileServiceAutoConfiguration   (核心服务装配)            │  │
│  │  ExecutorAutoConfiguration      (执行器装配)              │  │
│  │  RedisSpiAutoConfiguration      (Redis SPI 条件装配)      │  │
│  │  ObsAutoConfiguration           (OBS 适配器条件装配)      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  依赖（optional）:                                              │
│  - file-srv-core                                               │
│  - file-srv-spi-redis      (optional)                          │
│  - file-srv-adapter-hcs    (optional)                          │
└────────────────────────────────────────────────────────────────┘

┌─────────────────────────┐    ┌─────────────────────────────────┐
│   file-srv-spi-redis    │    │     file-srv-adapter-hcs        │
│                         │    │                                  │
│  - RedisIdempotency     │    │  - HcsObsAdapter                │
│    Checker              │    │  - ObsProperties                │
│  - RedisBloomTaskId     │    │                                  │
│    Validator            │    │  只依赖 common，不依赖装配层      │
│                         │    └─────────────────────────────────┘
│  只依赖 common           │
└─────────────────────────┘
```

### 代码实现

#### 1. 迁移自动配置到装配层

```java
// file-srv-autoconfiguration/.../config/RedisSpiAutoConfiguration.java
package tech.icc.filesrv.config;

@AutoConfiguration(after = FileServiceAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)  // 类存在时才加载
@EnableConfigurationProperties(FileServiceProperties.class)
public class RedisSpiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyChecker.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public IdempotencyChecker redisIdempotencyChecker(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyChecker(redisTemplate);
    }

    @Bean
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnProperty(name = "file-srv.bloom-filter.use-redis", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(TaskIdValidator.class)
    public TaskIdValidator redisBloomTaskIdValidator(RedissonClient redissonClient,
                                                     FileServiceProperties properties) {
        FileServiceProperties.BloomFilterProperties bloomProps = properties.getBloomFilter();
        return new RedisBloomTaskIdValidator(
                redissonClient,
                bloomProps.getExpectedInsertions(),
                bloomProps.getFpp()
        );
    }
}
```

#### 2. 装配层使用 optional 依赖

```xml
<!-- file-srv-autoconfiguration/pom.xml -->
<dependencies>
    <!-- 核心依赖 -->
    <dependency>
        <groupId>tech.icc</groupId>
        <artifactId>file-srv-core</artifactId>
        <version>${revision}</version>
    </dependency>
    
    <!-- 可选 SPI/适配器依赖 - 用于条件装配 -->
    <dependency>
        <groupId>tech.icc</groupId>
        <artifactId>file-srv-spi-redis</artifactId>
        <version>${revision}</version>
        <optional>true</optional>  <!-- 关键：不传递依赖 -->
    </dependency>
    <dependency>
        <groupId>tech.icc</groupId>
        <artifactId>file-srv-adapter-hcs</artifactId>
        <version>${revision}</version>
        <optional>true</optional>
    </dependency>
</dependencies>
```

#### 3. SPI 模块移除装配层依赖

```xml
<!-- file-srv-spi-redis/pom.xml -->
<dependencies>
    <dependency>
        <groupId>tech.icc</groupId>
        <artifactId>file-srv-common</artifactId>  <!-- 只依赖契约层 -->
        <version>${revision}</version>
    </dependency>
    <!-- ❌ 移除 file-srv-autoconfiguration 依赖 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
</dependencies>
```

#### 4. 注册自动配置入口

```text
# file-srv-autoconfiguration/.../META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
tech.icc.filesrv.config.FileServiceAutoConfiguration
tech.icc.filesrv.config.ExecutorAutoConfiguration
tech.icc.filesrv.config.RedisSpiAutoConfiguration
tech.icc.filesrv.config.ObsAutoConfiguration
```

### 最终依赖关系

应用按需引入：
```xml
<!-- 最终应用的 pom.xml -->
<dependencies>
    <!-- 必选：核心 + 自动配置 -->
    <dependency>
        <groupId>tech.icc</groupId>
        <artifactId>file-srv-autoconfiguration</artifactId>
    </dependency>
    
    <!-- 可选：按需引入 Redis SPI -->
    <dependency>
        <groupId>tech.icc</groupId>
        <artifactId>file-srv-spi-redis</artifactId>
    </dependency>
    
    <!-- 可选：按需引入 HCS 适配器 -->
    <dependency>
        <groupId>tech.icc</groupId>
        <artifactId>file-srv-adapter-hcs</artifactId>
    </dependency>
</dependencies>
```

当应用引入 `file-srv-spi-redis` 时：
- `RedisSpiAutoConfiguration` 中的 `@ConditionalOnClass(StringRedisTemplate.class)` 检测通过
- Redis 相关 Bean 被自动装配

当应用**不**引入 `file-srv-spi-redis` 时：
- `@ConditionalOnClass` 检测失败
- `RedisSpiAutoConfiguration` 整个配置类被跳过
- 无需任何代码修改

## 技术启示

### 1. Spring Boot 装配层的职责

| 层级 | 职责 | 典型注解 |
|-----|------|---------|
| **装配层** (autoconfiguration) | 条件装配、Bean 组装 | `@AutoConfiguration`, `@ConditionalOnClass` |
| **实现层** (spi/adapter) | 提供具体实现 | `@Component`（可选）, 纯 POJO |
| **契约层** (common) | 定义接口和配置类 | `@ConfigurationProperties` |

### 2. optional 依赖的作用

```xml
<dependency>
    <artifactId>file-srv-spi-redis</artifactId>
    <optional>true</optional>
</dependency>
```

- **编译时可用**：装配层可以引用 SPI 类
- **运行时可选**：不传递给最终应用，由应用显式决定是否引入
- **配合条件注解**：`@ConditionalOnClass` 检测类是否存在

### 3. 依赖方向黄金法则

```
❌ 错误：SPI/Adapter → Autoconfiguration（实现依赖装配）
✅ 正确：Autoconfiguration → SPI/Adapter（装配依赖实现）
```

**记忆口诀**：**被装配的不应知道谁在装配它**。

### 4. 灵活组装的实现模式

```
最终应用
    ├── file-srv-autoconfiguration (必选)
    ├── file-srv-spi-redis         (可选 - Redis 幂等)
    ├── file-srv-spi-kafka         (可选 - Kafka 消息)
    └── file-srv-adapter-hcs       (可选 - 华为云存储)
```

每个 SPI/Adapter 都可以独立引入或移除，装配层通过条件注解自动适应。

### 5. 问题识别信号

当你看到以下迹象时，说明装配边界可能已被破坏：

| 信号 | 说明 |
|-----|------|
| SPI 模块 import 了 autoconfiguration 包 | 依赖方向错误 |
| SPI 模块中有 `@AutoConfiguration` | 职责越界 |
| 移除 SPI 模块后编译失败 | 耦合过紧 |
| 无法单独测试 SPI 实现 | 依赖链过长 |

### 6. 重构检查清单

- [ ] 所有 `@AutoConfiguration` 是否都在 autoconfiguration 模块？
- [ ] SPI/Adapter 模块是否只依赖 common？
- [ ] autoconfiguration 对 SPI/Adapter 的依赖是否为 optional？
- [ ] 是否使用了 `@ConditionalOnClass` 进行类存在检测？
- [ ] 移除任一 SPI/Adapter 后应用是否仍能编译运行？

### 7. 与 Kafka SPI 边界问题的关系

本问题与 [Kafka SPI 边界划分不明确问题](kafka-spi-boundary-issue.md) 相关但不同：

| 问题 | Kafka SPI 边界问题 | Spring Boot 装配边界问题 |
|-----|-------------------|------------------------|
| 核心问题 | 业务逻辑侵入 ACL | 装配逻辑放错位置 |
| 依赖方向 | SPI → Core | SPI → Autoconfiguration |
| 解决方案 | 抽象 Handler 到 common | 迁移配置到装配层 |
| 关注点 | 领域边界 | 模块边界 |

两者共同体现了**依赖倒置原则**在不同层面的应用。
