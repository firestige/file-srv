# 测试策略文档

## 1. 测试分层原则

### 1.1 测试金字塔与执行频率

```
        ┌─────────────────┐
        │ 应用级集成测试   │  ← 执行频率最低（手动/发布前）
        │  微基准测试      │
        └─────────────────┘
              ▲
              │
        ┌─────────────────┐
        │ 模块内集成测试   │  ← package 时执行
        └─────────────────┘
              ▲
              │
        ┌─────────────────┐
        │   单元测试       │  ← 每次 build 都执行（最频繁）
        └─────────────────┘
```

**执行时机**:
- **单元测试**: `mvn compile` / `mvn test` - 每次构建必须执行
- **模块内集成测试**: `mvn package` / `mvn verify` - 打包时执行
- **应用级集成测试**: 手动触发 / CI 发布流水线 - 执行频次最低
- **微基准测试**: 手动触发 / 性能回归检测 - 按需执行

---

## 2. 测试分层详解

### 2.1 单元测试 (Unit Tests)

#### 定位与职责
- **目标**: 验证单个类/方法的业务逻辑正确性
- **位置**: 各模块 `src/test/java`
- **基类**: `BaseUnitTest`
- **执行频率**: ⭐⭐⭐⭐⭐ (最高，每次 build)
- **执行时机**: `mvn compile` / `mvn test`

#### 特点
- ✅ 不依赖 Spring 容器（纯 JUnit + Mockito）
- ✅ 不依赖数据库/外部系统
- ✅ 不依赖 Docker/Testcontainers
- ✅ 执行速度极快 (< 100ms/test)
- ✅ 专注业务逻辑验证
- ✅ 任何环境都能运行（包括无 Docker 的 CI）

#### 适用场景
- Domain 领域对象逻辑（聚合根、值对象）
- Service 业务逻辑（通过 Mock 依赖）
- Util 工具类、Validator 验证器
- Mapper/Converter 转换逻辑

#### 示例：Domain 层单元测试
```java
class TaskAggregateTest extends BaseUnitTest {
    
    @Test
    @DisplayName("应该成功创建新任务")
    void shouldCreateNewTask() {
        // Given
        String taskId = "task-123";
        String uploadId = "upload-456";
        UploadContext context = UploadContext.builder()
            .fileName("test.pdf")
            .fileSize(1024L)
            .build();

        // When
        TaskAggregate task = TaskAggregate.createNew(taskId, uploadId, context);

        // Then
        assertThat(task.getTaskId()).isEqualTo(taskId);
        assertThat(task.getUploadId()).isEqualTo(uploadId);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(task.getTotalParts()).isNull();
    }
}
```

#### 示例：Service 层单元测试（Mock 依赖）
```java
class FileServiceTest extends BaseUnitTest {
    
    private FileService fileService;
    
    @Mock
    private FileReferenceRepository repository;
    
    @Mock
    private StorageAdapter storageAdapter;
    
    @Mock
    private DeduplicationService deduplicationService;
    
    @BeforeEach
    void setUp() {
        fileService = new FileService(
            repository, 
            storageAdapter, 
            deduplicationService
        );
    }

    @Test
    @DisplayName("应该拒绝超过大小限制的文件")
    void shouldRejectOversizedFile() {
        // Given
        MockMultipartFile largeFile = new MockMultipartFile(
            "file", "large.bin", "application/octet-stream",
            new byte[11 * 1024 * 1024] // 11MB
        );
        OwnerInfo owner = new OwnerInfo("user123", "Test User");

        // When & Then
        assertThatThrownBy(() -> 
            fileService.upload(largeFile, owner, null, null)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("文件大小超过限制");
        
        // 验证没有调用存储适配器
        verifyNoInteractions(storageAdapter);
    }
}
```

---

### 2.2 模块内集成测试 (Module Integration Tests)

#### 定位与职责
- **目标**: 验证模块内多个组件协作的正确性
- **位置**: 各模块 `src/test/java`
- **基类**: `BaseIntegrationTest`
- **执行频率**: ⭐⭐⭐ (中等，package 时执行)
- **执行时机**: `mvn package` / `mvn verify`

#### 特点
- ✅ 使用 H2 内存数据库（快速、无需 Docker）
- ✅ 加载部分 Spring 容器（模块相关 Bean）
- ✅ 使用 `@MockBean` 模拟外部依赖（通过防腐层）
- ✅ 使用 `@Transactional` 自动回滚
- ⚠️ **不使用 Testcontainers**（任何情况下都不允许）
- ⚠️ 执行速度较快 (< 1s/test)

#### 架构原则：防腐层隔离外部依赖
**核心理念**: 
- 所有外部系统依赖必须通过防腐层（Anti-Corruption Layer）访问
- 防腐层提供接口抽象，模块内代码依赖接口而非具体实现
- 测试时通过 `@MockBean` 替换防腐层实现，无需真实外部系统

**防腐层示例**:
```
┌─────────────────────────────────────┐
│       file-srv-core 模块            │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   FileService                │  │
│  │   (业务逻辑)                  │  │
│  └─────────┬────────────────────┘  │
│            │ 依赖接口               │
│            ▼                        │
│  ┌──────────────────────────────┐  │
│  │   StorageAdapter (接口)      │  │ ← 防腐层接口
│  │   - upload()                 │  │
│  │   - download()               │  │
│  └──────────────────────────────┘  │
└─────────────────────────────────────┘
            ▲
            │ 实现
┌───────────┴─────────────────────────┐
│   file-srv-adapters 模块            │
│  ┌──────────────────────────────┐  │
│  │   HcsStorageAdapter (实现)   │  │
│  │   - 调用 HCS HTTP API        │  │
│  └──────────────────────────────┘  │
└─────────────────────────────────────┘
```

**测试时的隔离**:
```java
@SpringBootTest
@Transactional
class FileServiceIntegrationTest {
    
    @Autowired
    private FileService fileService;  // 真实的 Service
    
    @Autowired
    private FileReferenceRepository repository;  // 真实的 Repository (H2)
    
    @MockBean
    private StorageAdapter storageAdapter;  // Mock 防腐层接口，不需要真实 HCS
    
    @Test
    void shouldUploadFile() {
        // 通过 Mock 隔离外部依赖
        when(storageAdapter.upload(any(), any(), anyLong()))
            .thenReturn(UploadResult.builder().storageKey("mock-key").build());
        
        // 测试业务逻辑 + 数据持久化
        FileInfoDto result = fileService.upload(mockFile, owner, null, null);
        
        // 验证数据库操作
        assertThat(repository.findByFKey(result.getFkey())).isPresent();
    }
}
```

**如果没有防腐层怎么办？**
- ❌ **不允许**: 直接在业务代码中依赖具体的外部实现（如 HcsClient）
- ✅ **必须**: 重构代码，抽取接口，建立防腐层
- 📝 **记录**: 架构设计疏漏，需要重构技术债

#### 适用场景
- Repository 层数据库操作测试（H2 内存数据库）
- Service 层与 Repository 协作测试
- 模块内完整业务流程验证（外部依赖 Mock）
- 事务边界验证

#### 示例：Repository 集成测试
```java
@DataJpaTest
@Import(FileReferenceRepositoryImpl.class)
class FileReferenceRepositoryTest extends BaseIntegrationTest {
    
    @Autowired
    private FileReferenceRepository repository;

    @Test
    @DisplayName("应该保存并查询文件引用")
    void shouldSaveAndFindFileReference() {
        // Given
        FileReference file = FileReference.builder()
            .fKey("file-123")
            .fileName("test.pdf")
            .ownerId("user123")
            .size(1024L)
            .build();

        // When
        repository.save(file);
        Optional<FileReference> found = repository.findByFKey("file-123");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getFileName()).isEqualTo("test.pdf");
        assertThat(found.get().getOwnerId()).isEqualTo("user123");
    }
}
```

#### 示例：Service 集成测试（含数据库）
```java
@SpringBootTest
@Transactional
class FileServiceIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private FileReferenceRepository repository;
    
    @MockBean
    private StorageAdapter storageAdapter;

    @Test
    @DisplayName("应该完整保存文件到数据库")
    void shouldSaveFileWithAllMetadata() {
        // Given
        when(storageAdapter.upload(any(), any(), anyLong()))
            .thenReturn(UploadResult.builder()
                .storageKey("storage-key-123")
                .etag("etag-abc")
                .size(1024L)
                .build());
        
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.pdf", "application/pdf", 
            "test content".getBytes()
        );
        OwnerInfo owner = new OwnerInfo("user123", "Test User");

        // When
        FileInfoDto result = fileService.upload(file, owner, null, null);

        // Then
        assertThat(result.getFkey()).isNotBlank();
        assertThat(result.getFileName()).isEqualTo("test.pdf");
        
        // 验证数据库中存在
        Optional<FileReference> saved = repository.findByFKey(result.getFkey());
        assertThat(saved).isPresent();
        assertThat(saved.get().getOwnerId()).isEqualTo("user123");
    }
}
```

---

### 2.3 应用级集成测试 (Application Integration Tests)

#### 定位与职责
- **目标**: 验证完整的 HTTP 端到端流程
- **位置**: `file-srv-test` 模块（独立模块）
- **基类**: `ApplicationIntegrationTestBase`
- **执行频率**: ⭐ (最低，手动/发布前)
- **执行时机**: 手动触发 / CI 发布流水线

#### 特点
- ✅ **唯一允许使用 Testcontainers 的地方**
- ✅ 使用 Testcontainers + PostgreSQL（真实数据库）
- ✅ 使用 `MockMvc` 模拟 HTTP 请求
- ✅ 加载完整 Spring Boot 应用
- ✅ 验证完整业务流程（从 HTTP 到数据库）
- ⚠️ 需要 Docker 环境
- ⚠️ 执行速度较慢 (2-5s/test)
- ⚠️ CI 环境可选择性执行（通过 Profile 控制）

#### 为什么只在这里使用 Testcontainers？

**1. 真实性验证**
- 验证应用与 **真实 PostgreSQL** 的兼容性
- 验证 SQL 方言、事务、索引等生产环境特性
- 发现 H2 与 PostgreSQL 的行为差异

**2. 端到端保证**
- 完整的 HTTP 请求 → 业务处理 → 数据持久化 → HTTP 响应
- 验证多个模块协作的正确性
- 模拟生产环境的真实场景

**3. 执行频率低**
- 不在每次 build 时执行（避免 Docker 依赖）
- 仅在发布前或手动触发
- CI 可通过 Profile 灵活控制

#### 架构边界清晰

```
┌────────────────────────────────────────────────┐
│  应用级集成测试 (file-srv-test)                  │
│                                                │
│  ✅ 使用 Testcontainers                        │
│  ✅ 真实 PostgreSQL                            │
│  ✅ 完整 Spring Boot 应用                       │
│  ✅ HTTP API 端到端验证                         │
│                                                │
│  执行频率: ⭐ (最低)                            │
│  执行时机: 手动 / CI 发布流水线                  │
└────────────────────────────────────────────────┘
                      ▲
                      │
┌────────────────────────────────────────────────┐
│  模块内集成测试 (各模块 src/test/java)          │
│                                                │
│  ❌ 不使用 Testcontainers                      │
│  ✅ H2 内存数据库                               │
│  ✅ 部分 Spring 容器                            │
│  ✅ @MockBean 隔离外部依赖（通过防腐层）         │
│                                                │
│  执行频率: ⭐⭐⭐ (中等)                        │
│  执行时机: mvn package                         │
└────────────────────────────────────────────────┘
                      ▲
                      │
┌────────────────────────────────────────────────┐
│  单元测试 (各模块 src/test/java)                │
│                                                │
│  ❌ 不依赖 Spring                               │
│  ❌ 不依赖数据库                                │
│  ✅ 纯 JUnit + Mockito                         │
│  ✅ Mock 所有依赖                               │
│                                                │
│  执行频率: ⭐⭐⭐⭐⭐ (最高)                     │
│  执行时机: mvn test                            │
└────────────────────────────────────────────────┘
```

#### 适用场景
- HTTP API 端到端测试
- 完整业务流程验证
- 跨模块集成验证
- 生产环境回归测试
- PostgreSQL 特性验证（如 JSON 字段、全文搜索）

#### 示例：HTTP API 端到端测试
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class FileUploadIntegrationTest extends ApplicationIntegrationTestBase {

    @Container
    private static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private StorageAdapter storageAdapter;

    @Test
    @DisplayName("应该通过 HTTP 成功上传文件")
    void shouldUploadFileViaHttpSuccessfully() throws Exception {
        // Given
        when(storageAdapter.upload(any(), any(), anyLong()))
            .thenReturn(UploadResult.builder()
                .storageKey("storage-key-123")
                .etag("etag-abc")
                .build());
        
        String requestJson = """
            {
              "fileName": "test.txt",
              "size": 1024,
              "ownerId": "user@example.com",
              "ownerName": "Test User",
              "accessControl": {"accessLevel": "public_read"},
              "tags": ["document", "important"]
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/files/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fkey").exists())
            .andExpect(jsonPath("$.fileName").value("test.txt"))
            .andExpect(jsonPath("$.size").value(1024))
            .andExpect(jsonPath("$.ownerId").value("user@example.com"))
            .andExpect(jsonPath("$.accessControl.accessLevel").value("public_read"));
        
        verify(storageAdapter, times(1)).upload(any(), any(), anyLong());
    }

    @Test
    @DisplayName("应该返回 400 当文件名过长")
    void shouldReturn400WhenFileNameIsTooLong() throws Exception {
        // Given
        String longName = "a".repeat(256) + ".txt";
        String requestJson = String.format("""
            {
              "fileName": "%s",
              "size": 1024,
              "ownerId": "user@example.com"
            }
            """, longName);

        // When & Then
        mockMvc.perform(post("/api/v1/files/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("文件名长度不能超过 255 个字符"));
    }
}
```

---

### 2.4 微基准测试 (Micro Benchmarks)

#### 定位与职责
- **目标**: 测量关键代码路径的性能
- **位置**: `file-srv-test` 模块 `src/jmh/java`
- **工具**: JMH (Java Microbenchmark Harness)
- **执行频率**: ⭐ (最低，按需执行)
- **执行时机**: 性能优化 / 回归检测

#### 适用场景
- 热点方法性能测量
- 不同实现方案对比
- 性能回归检测
- 优化效果验证

#### 示例：文件去重算法性能测试
```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class HashCalculationBenchmark {

    private byte[] fileContent;

    @Setup
    public void setup() {
        fileContent = new byte[1024 * 1024]; // 1MB
        new Random().nextBytes(fileContent);
    }

    @Benchmark
    public String md5Hash() {
        return DigestUtils.md5Hex(fileContent);
    }

    @Benchmark
    public String sha256Hash() {
        return DigestUtils.sha256Hex(fileContent);
    }
}
```

---

## 3. Maven 生命周期与测试集成

### 3.1 测试执行策略

```xml
<!-- pom.xml 配置 -->
<build>
    <plugins>
        <!-- Surefire: 单元测试（test 阶段） -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <includes>
                    <include>**/*Test.java</include>
                </includes>
                <excludes>
                    <exclude>**/*IntegrationTest.java</exclude>
                </excludes>
            </configuration>
        </plugin>

        <!-- Failsafe: 集成测试（verify 阶段） -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <configuration>
                <includes>
                    <include>**/*IntegrationTest.java</include>
                </includes>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>integration-test</goal>
                        <goal>verify</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 3.2 执行命令对照

| 命令 | 执行阶段 | 包含测试 | 用途 |
|------|---------|---------|------|
| `mvn compile` | compile | 无 | 仅编译代码 |
| `mvn test` | test | 单元测试 | 快速反馈（开发时） |
| `mvn package` | package + verify | 单元测试 + 模块集成测试 | 构建 JAR 包 |
| `mvn verify` | verify | 单元测试 + 模块集成测试 | 完整验证 |
| `mvn install` | install | 单元测试 + 模块集成测试 | 安装到本地仓库 |

**应用级集成测试**:
```bash
# 在 file-srv-test 模块单独执行
cd file-srv-test
mvn verify -Pintegration-tests
```

---

## 4. 覆盖率目标

### 4.1 模块覆盖率要求
| 模块 | 行覆盖率 | 分支覆盖率 | 说明 |
|------|---------|-----------|------|
| file-srv-common | ≥ 60% | ≥ 50% | 工具类、枚举、常量 |
| file-srv-core | ≥ 80% | ≥ 70% | 核心业务逻辑 |
| file-srv-adapters | ≥ 70% | ≥ 60% | 外部集成适配器 |
| file-srv-aspect | ≥ 70% | ≥ 60% | 切面逻辑 |

### 4.2 JaCoCo 排除规则
以下类型自动排除，不计入覆盖率:
- 常量类 (`*Constants.class`)
- 配置类 (`*Configuration.class`, `*Config.class`)
- 简单 DTO/VO (`entrypoint/dto/**`, `entrypoint/vo/**`)
- 自动配置 (`autoconfiguration/**`)

### 4.3 覆盖率检查
```bash
# 运行测试并生成覆盖率报告
mvn clean verify

# 查看覆盖率报告
open file-srv-core/target/site/jacoco/index.html
```

---

## 5. 测试命名约定

### 5.1 测试类命名
```
<被测试类名> + Test/IntegrationTest

例如:
- FileServiceTest (单元测试)
- FileServiceIntegrationTest (模块集成测试)
- FileUploadIntegrationTest (应用集成测试)
- HashCalculationBenchmark (微基准测试)
```
---

## 6. 架构决策记录

### 6.1 为什么模块内集成测试不使用 Testcontainers？

**决策**: 模块内集成测试**严格禁止**使用 Testcontainers，只能使用 H2 内存数据库

**核心原则**:
> **外部依赖必须通过防腐层（Anti-Corruption Layer）隔离。没有防腐层意味着架构设计存在疏漏。**

**理由**:
1. **架构强制约束**: 
   - 防腐层是 DDD 的核心模式，保护领域模型不受外部系统污染
   - 所有外部依赖（存储、消息队列、缓存等）必须有接口抽象
   - 测试时通过 `@MockBean` 替换实现，无需真实外部系统
   - **如果无法 Mock，说明缺少防腐层，必须重构**

2. **CI 兼容性**: 
   - 避免强制要求 CI 环境提供 Docker 上下文
   - 模块测试应该在任何环境都能运行
   - 降低 CI 配置复杂度和维护成本

3. **执行速度**: 
   - H2 启动速度 < 100ms，Testcontainers 启动需要 2-5s
   - 模块集成测试在 `mvn package` 时执行，需要快速反馈
   - 开发体验优先：本地测试不应该等待容器启动

4. **职责分离**:
   - 模块测试关注：模块内组件协作 + 业务逻辑正确性
   - 不关注：与真实外部系统的兼容性（这是应用级测试的职责）

**防腐层示例**:
```java
// ❌ 错误：直接依赖具体实现
public class FileService {
    private final HcsStorageClient hcsClient;  // 紧耦合，无法测试
    
    public void upload(File file) {
        hcsClient.uploadToHcs(file);  // 测试时必须有真实 HCS
    }
}

// ✅ 正确：依赖防腐层接口
public class FileService {
    private final StorageAdapter storageAdapter;  // 依赖接口
    
    public void upload(File file) {
        storageAdapter.upload(file);  // 测试时 Mock 接口即可
    }
}
```

**测试策略**:
```java
// 模块内集成测试：Mock 防腐层
@SpringBootTest
class FileServiceIntegrationTest {
    @MockBean
    private StorageAdapter storageAdapter;  // Mock 接口
    
    @Test
    void shouldUploadFile() {
        when(storageAdapter.upload(any())).thenReturn(...);  // 无需真实存储
        // 测试业务逻辑
    }
}

// 应用级集成测试：真实外部系统
@SpringBootTest
@Testcontainers
class FileUploadE2ETest {
    // 这里可以用 Testcontainers 启动真实 PostgreSQL
    // 验证与真实数据库的兼容性
}
```

### 6.2 Testcontainers 的唯一使用场景

**决策**: Testcontainers **仅且仅允许**在 `file-srv-test` 模块的应用级集成测试中使用

**使用场景**:
1. **HTTP API 端到端测试**
   - 验证完整的请求-响应流程
   - 从 Controller → Service → Repository → PostgreSQL

2. **数据库兼容性验证**
   - 验证 SQL 在真实 PostgreSQL 中的执行
   - 发现 H2 与 PostgreSQL 的行为差异
   - 验证索引、约束、触发器等生产特性

3. **发布前回归测试**
   - 模拟生产环境
   - 验证多个模块的端到端协作

**执行控制**:
```xml
<!-- 通过 Maven Profile 控制 -->
<profile>
    <id>integration-tests</id>
    <properties>
        <skipIntegrationTests>false</skipIntegrationTests>
    </properties>
</profile>

<!-- 默认跳过 -->
<properties>
    <skipIntegrationTests>true</skipIntegrationTests>
</properties>
```

**执行命令**:
```bash
# 日常开发：跳过应用级集成测试
mvn clean verify

# 发布前：执行完整测试
mvn clean verify -Pintegration-tests
```

### 6.3 为什么单元测试不使用 Spring？

**决策**: 单元测试完全不依赖 Spring 容器

**理由**:
1. **执行速度**: 纯 JUnit 测试 < 100ms，Spring 容器启动需要 1-2s
2. **频繁执行**: 单元测试在每次 `mvn test` 时执行，必须极快
3. **隔离性**: 单元测试应该只测试业务逻辑，不测试 Spring 配置
4. **环境无关**: 任何 Java 环境都能运行，包括最小化的 CI 环境

**使用 Spring 的场景**:
- 模块集成测试：需要验证 Spring Bean 的组装和协作
- 应用集成测试：需要验证完整的 Spring Boot 应用

---

## 7. 当前测试状态

### 7.1 已完成 ✅

#### Phase 1: 测试基础设施
- [x] Maven Surefire/Failsafe 插件配置
- [x] JaCoCo 覆盖率插件配置
- [x] 基础测试类创建（BaseUnitTest, BaseIntegrationTest）
- [x] 测试策略文档编写

#### Phase 2: Domain 层单元测试
- [x] `TaskAggregateTest` (31 tests) - 任务聚合根逻辑
- [x] `PartInfoTest` (6 tests) - 分片信息值对象

**覆盖场景**:
- 任务创建、状态流转
- 分片管理（添加/完成/查询）
- Callback 流程
- 任务中止/失败
- 任务过期
- 上下文更新

---

### 7.2 进行中 🔄

#### Phase 2: Service 层测试（需要清理和重构）

**当前问题**:
1. `FileServiceIntegrationTest` 配置过于复杂
   - 错误地尝试使用 @SpringBootTest 加载完整容器
   - 遇到 Bean 依赖问题（StorageAdapterRegistry NPE）
   - 违反了"模块内测试不使用 Testcontainers"的原则

**根本原因**:
- 测试策略不清晰，混淆了单元测试和集成测试的边界
- 尝试在模块内测试中验证过多内容
- 忽略了防腐层的作用

**解决方案**:
1. **删除当前的 FileServiceIntegrationTest**（过度设计）
2. **创建纯单元测试** `FileServiceTest`（优先）:
   - 使用 Mockito Mock 所有依赖
   - 不依赖 Spring 容器
   - 不依赖数据库
   - 聚焦业务逻辑验证

3. **可选：创建轻量集成测试** `FileReferenceRepositoryTest`（如需要）:
   - 使用 @DataJpaTest + H2
   - 只测试 Repository 层数据库操作
   - 不涉及 Service 层

**决策**:
- 优先实现单元测试（FileServiceTest, TaskServiceTest）
- 暂时不做模块内 Spring 集成测试
- 后续根据需要再补充 Repository 测试

---

### 7.3 待办事项 📋

#### Phase 2: Core 模块核心测试（优先级：⭐⭐⭐⭐⭐）

**单元测试**:
- [ ] `FileServiceTest` - 文件服务业务逻辑（Mock 依赖）
- [ ] `TaskServiceTest` - 任务服务业务逻辑（Mock 依赖）
- [ ] `DeduplicationServiceTest` - 去重服务逻辑
- [ ] `FileReferenceTest` - 文件引用领域对象

**模块集成测试**（如果需要）:
- [ ] `FileReferenceRepositoryTest` - Repository 数据库操作
- [ ] `TaskRepositoryTest` - Repository 数据库操作
- [ ] `FileServiceIntegrationTest` - Service + Repository 协作（H2）
- [ ] `TaskServiceIntegrationTest` - Service + Repository 协作（H2）

#### Phase 3: Common 模块工具测试（优先级：⭐⭐⭐）
- [ ] `FileKeyGeneratorTest` - fKey 生成逻辑
- [ ] `HashCalculatorTest` - Hash 计算逻辑
- [ ] `FileSizeFormatterTest` - 文件大小格式化
- [ ] VO 验证测试（AccessControl, OwnerInfo 等）

#### Phase 4: Adapter 模块测试（优先级：⭐⭐）
- [ ] `HcsStorageAdapterTest` - HCS 适配器（Mock HTTP 客户端）
- [ ] `HcsStorageAdapterIntegrationTest` - HCS 真实调用（可选）

#### Phase 5: 应用级集成测试（优先级：⭐）
- [ ] 在 `file-srv-test` 模块创建完整的 HTTP API 测试
- [ ] 使用 Testcontainers + PostgreSQL
- [ ] 测试完整的文件上传/下载/删除流程
- [ ] 测试完整的多段上传流程

---

## 8. 测试执行建议

### 8.1 开发阶段
```bash
# 快速反馈（只运行单元测试）
mvn clean test

# 单独运行某个测试类
mvn test -Dtest=TaskAggregateTest

# 单独运行某个测试方法
mvn test -Dtest=TaskAggregateTest#shouldCreateNewTask
```

### 8.2 提交前
```bash
# 完整验证（单元测试 + 模块集成测试）
mvn clean verify

# 查看覆盖率报告
open file-srv-core/target/site/jacoco/index.html
```

### 8.3 发布前
```bash
# 运行应用级集成测试（需要 Docker）
cd file-srv-test
mvn clean verify -Pintegration-tests

# 或者在根目录
mvn clean verify -Pintegration-tests -pl file-srv-test
```

---

## 9. 测试最佳实践

### 9.1 单元测试原则
✅ **DO**:
- 测试单一职责
- Mock 所有外部依赖
- 保持测试独立性（不依赖执行顺序）
- 使用有意义的测试方法名
- 遵循 Given-When-Then 结构

❌ **DON'T**:
- 不要在单元测试中启动 Spring 容器
- 不要访问真实数据库
- 不要依赖外部系统（文件系统、网络）
- 不要在测试间共享状态

### 9.2 集成测试原则
✅ **DO**:
- 使用事务回滚保持数据库干净
- 使用 `@MockBean` 隔离外部系统
- 测试组件协作而非单一逻辑
- 验证数据持久化正确性

❌ **DON'T**:
- 不要测试框架本身（如 Spring Data JPA）
- 不要过度依赖集成测试（应该是单元测试的补充）
- 不要在集成测试中测试所有边界条件

---

## 附录 A：测试工具链

### A.1 核心依赖
```xml
<dependencies>
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Mockito -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- AssertJ -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Spring Boot Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- H2 Database（模块集成测试） -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers（应用集成测试） -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### A.2 工具推荐
- **IDE 插件**: JUnit Jupiter (IntelliJ IDEA 内置)
- **覆盖率可视化**: JaCoCo + SonarQube
- **性能测试**: JMH (Java Microbenchmark Harness)
- **Mock 服务器**: WireMock (HTTP API 集成测试)

---

**文档版本**: v2.0  
**最后更新**: 2026-01-30  
**维护者**: 文件服务团队

## 附录 B：代码示例

### B.1 常用断言
```java
// JUnit 5 断言
assertEquals(expected, actual);
assertNotNull(value);
assertTrue(condition);
assertThrows(Exception.class, () -> method());

// AssertJ 断言 (推荐)
assertThat(list).hasSize(3);
assertThat(result).isNotNull()
    .extracting("name", "size")
    .containsExactly("test.txt", 1024L);
```

### B.2 MockMvc 常用操作
```java
// GET 请求
mockMvc.perform(get("/api/v1/files/{fkey}", fkey))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.name").value("test.txt"));

// POST 请求
mockMvc.perform(post("/api/v1/files/upload")
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestJson))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.fkey").exists());

// PUT 请求
mockMvc.perform(put("/api/v1/tasks/{taskId}", taskId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(updateJson))
    .andExpect(status().isAccepted());

// DELETE 请求
mockMvc.perform(delete("/api/v1/files/{fkey}", fkey))
    .andExpect(status().isNoContent());
```

### B.3 Testcontainers 配置
```java
@Container
protected static final PostgreSQLContainer<?> postgres = 
    new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("filesrv_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true); // 重用容器，加快测试速度

@DynamicPropertySource
static void configureTestDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
}
```

---

## 附录 C：测试基建实施方案

> **版本**: v2.0  
> **创建日期**: 2026-01-30  
> **状态**: 待实施

### C.1 方案目标

基于三层测试策略，构建一套务实、高 ROI 的测试基础设施：

1. **高效编写测试**：减少样板代码，Builder 预制数据
2. **测试可复现**：固定 seed 控制随机性
3. **无外部依赖**：Stub 替代真实外部系统
4. **CI 友好**：快速执行，无需 Docker（单元测试层）

### C.2 技术选型与决策

#### 测试工具清单

| 类别 | 工具 | 版本 | 作用域 | 决策 |
|-----|------|------|--------|------|
| 测试框架 | JUnit 5 | 5.10.0 | 全局 | ✅ 已有 |
| Mock 框架 | Mockito | 5.5.0 | 单元测试 | ✅ 已有 |
| 断言库 | AssertJ | 3.24.2 | 全局 | ✅ 已有 |
| 随机数据 | DataFaker | 2.0.2 | 单元测试 | ✅ 采用 |
| 异步测试 | Awaitility | 4.2.0 | 集成测试 | ✅ 采用 |
| 内存数据库 | H2 | - | 模块集成测试 | ✅ 已有 |
| 容器测试 | Testcontainers | 1.19.3 | 应用集成测试 | ✅ 仅 file-srv-test |

#### 随机数据工具对比与决策

**候选工具**：

| 工具 | 维护状态 | 特点 | 决策 |
|-----|---------|------|------|
| **DataFaker** | ✅ 活跃 | JavaFaker 社区分支，70+ 语言，1000+ 方法 | ✅ **采用** |
| JavaFaker | ❌ 停止 | 2019 年后停止，有安全漏洞 | ❌ 不采用 |
| EasyRandom | ⚠️ 一般 | 反射填充 Bean，数据无语义 | ❌ 不采用 |
| Instancio | ⚠️ 较新 | 现代 API，与 JUnit 5 集成 | ⏸️ 备选 |
| jFairy | ❌ 停止 | 数据类型少，更新慢 | ❌ 不采用 |

**决策理由**：
1. DataFaker 完美匹配文件服务场景（文件名、MIME 类型、大小）
2. 支持固定 seed，保证测试可复现
3. 活跃维护，安全可靠
4. API 简单，学习成本低

**使用规范**：
```java
// 全局统一 seed，保证可复现
private static final Faker faker = new Faker(new Random(42));
```

### C.3 测试数据构建器设计

#### 设计原则

1. **Builder 模式**：所有字段有合理默认值，支持链式调用
2. **固定数据优先**：只在必要时使用 Faker（需指定 seed）
3. **场景预设**：常用状态的快捷方法，提高可读性

#### 目录结构

```
file-srv-core/src/test/java/tech/icc/filesrv/core/
└── testdata/
    ├── TestDataBuilders.java      # 主入口，所有 Builder 静态工厂
    └── fixtures/
        ├── TaskFixtures.java      # 任务场景预设
        └── FileFixtures.java      # 文件场景预设
```

#### Builder API 设计

```java
// 基础用法：使用默认值
TaskAggregate task = TestDataBuilders.aTask().build();

// 自定义字段
TaskAggregate task = TestDataBuilders.aTask()
    .withFKey("custom-fkey")
    .withStatus(TaskStatus.IN_PROGRESS)
    .withSessionId("session-123")
    .build();

// 随机数据（固定 seed 可复现）
FileReference ref = TestDataBuilders.aFileReference()
    .withRandomFilename()
    .withRandomContentType()
    .build();

// 场景预设
TaskAggregate task = TaskFixtures.inProgressTask();
FileReference file = FileFixtures.imageFile();
```

#### 覆盖范围

| Builder | 对象类型 | 场景预设 |
|---------|---------|---------|
| `aTask()` | TaskAggregate | pendingTask, inProgressTask, completedTask, taskWithCallbacks |
| `aPart()` | PartInfo | - |
| `aFileReference()` | FileReference | imageFile, documentFile, largeFile |
| `aCallback()` | CallbackConfig | thumbnailCallback, hashVerifyCallback |
| `aFileReferenceEntity()` | FileReferenceEntity | - |
| `aFileInfoEntity()` | FileInfoEntity | - |

### C.4 Stub 类设计

#### 为什么用 Stub 而不是 Mock？

- **Mock**：每个测试都要配置 `when().thenReturn()`，重复代码多
- **Stub**：预实现类，开箱即用，有合理默认行为

#### Stub 清单

| Stub 类 | 替代接口 | 作用域 | 工作量 |
|---------|---------|--------|--------|
| `InMemoryStorageAdapterStub` | StorageAdapter | 文件上传/下载测试 | 3h |
| `InMemoryRedisStub` | RedisTemplate | 缓存/去重测试 | 2h |
| `MockCallbackExecutorStub` | CallbackExecutor | 回调执行测试 | 1h |

#### 设计示例

**InMemoryStorageAdapterStub**：
```java
public class InMemoryStorageAdapterStub implements StorageAdapter {
    private Map<String, byte[]> storage = new ConcurrentHashMap<>();
    private Map<String, String> uploadSessions = new ConcurrentHashMap<>();
    
    @Override
    public UploadSession initMultipartUpload(String bucket, String key) {
        String sessionId = "session-" + UUID.randomUUID();
        uploadSessions.put(sessionId, key);
        return new UploadSession(sessionId, bucket, key);
    }
    
    @Override
    public String uploadPart(UploadSession session, int partNumber, byte[] data) {
        storage.put(session.sessionId() + "-part-" + partNumber, data);
        return "etag-" + partNumber;
    }
    
    // 测试辅助方法
    public byte[] getStoredFile(String key) { return storage.get(key); }
    public void clear() { storage.clear(); uploadSessions.clear(); }
}
```

**InMemoryRedisStub**：
```java
public class InMemoryRedisStub {
    private Map<String, Object> data = new ConcurrentHashMap<>();
    private Map<String, Instant> expiry = new ConcurrentHashMap<>();
    
    public void set(String key, Object value, Duration ttl) { ... }
    public <T> T get(String key, Class<T> type) { ... }
    public boolean exists(String key) { ... }
    public void clear() { data.clear(); expiry.clear(); }
}
```

#### Repository 不需要 Stub

Repository 直接用 H2 数据库测试：
```java
@DataJpaTest
class TaskRepositoryTest {
    @Autowired TaskRepository taskRepository;
    // H2 自动配置，验证真实 JPA 映射
}
```

### C.5 实施计划与 ROI

#### 投资清单

| 组件 | 工作量 | ROI | 优先级 | 状态 |
|-----|-------|-----|--------|------|
| Maven 配置 | 1h | ⭐⭐⭐⭐⭐ | P0 | ⬜ 待实施 |
| 测试数据构建器 | 4h | ⭐⭐⭐⭐⭐ | P0 | ⬜ 待实施 |
| Stub 类 | 6h | ⭐⭐⭐⭐ | P0 | ⬜ 待实施 |
| 测试配置文件 | 1h | ⭐⭐⭐⭐ | P0 | ⬜ 待实施 |
| 测试基类 | 2h | ⭐⭐⭐ | P1 | ⏸️ 按需 |
| 自定义断言 | - | - | - | ❌ 不做 |
| 数据加载器 | - | - | - | ❌ 不做 |

**总投入**: 12h (P0) + 2h (P1 可选) = **14h**

#### ROI 分析

| 组件 | 投入 | 收益 | 投入产出比 |
|-----|------|------|-----------|
| Maven 配置 | 1h | 测试速度提升 70%，CI 统一 | 1:100+ |
| 测试数据构建器 | 4h | 节省 500+ 行代码，可维护性提升 | 1:125 |
| Stub 类 | 6h | 节省 850+ 行代码，避免外部依赖 | 1:142 |
| 测试配置文件 | 1h | 统一环境配置 | 1:50 |

#### 实施顺序

```
Day 1 (2h):
├── Maven 配置（父 POM + 模块 POM）
└── 测试配置文件（application-test.yml, logback-test.xml）
    ↓ 验证: mvn test 能正常执行

Day 2 (4h):
└── 测试数据构建器（TestDataBuilders + TaskFixtures）
    ↓ 验证: 重写 TaskAggregateTest 使用 Builder

Day 3 (3h):
└── InMemoryStorageAdapterStub
    ↓ 验证: FileService 第一个单元测试通过

Day 4 (3h):
├── InMemoryRedisStub
└── MockCallbackExecutorStub
    ↓ 验证: 所有 Stub 可用，开始批量编写测试

Day 5 (评估):
└── 观察重复代码，决定是否需要测试基类
```

#### 验收标准

- [ ] `mvn test` 单元测试并行执行，速度提升 50%+
- [ ] `mvn verify -Pintegration-tests` 能正确区分执行
- [ ] Builder 覆盖 TaskAggregate, FileReference, PartInfo, CallbackConfig
- [ ] 3 个 Stub 类可用（Storage, Redis, Callback）
- [ ] 现有 37 个测试全部通过
- [ ] FileServiceTest, TaskServiceTest 使用新基建编写完成

#### 不做的事（明确拒绝）

| 组件 | 原因 | 替代方案 |
|-----|------|---------|
| 自定义断言 | AssertJ 已足够 | `assertThat().isEqualTo()` |
| 数据加载器 | Builder 足够表达 | 用 Builder 组合 |
| 参数化数据源 | JUnit 5 @CsvSource 够用 | `@ValueSource`, `@MethodSource` |
| 测试监听器 | 增加复杂度 | `@BeforeEach/@AfterEach` |
