# 测试策略文档

## 1. 测试分层架构

### 1.1 单元测试 (Unit Tests)
- **位置**: 各模块 `src/test/java`
- **基类**: `BaseUnitTest`
- **范围**: 单个类/组件的逻辑测试
- **特点**:
  - 使用 Mockito 模拟依赖
  - 不加载 Spring 容器
  - 执行速度快 (< 100ms/test)
  - 专注于业务逻辑验证

**示例**:
```java
class FileNameValidatorTest extends BaseUnitTest {
    private FileNameValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileNameValidator();
    }

    @Test
    void shouldRejectInvalidCharacters() {
        // Given
        String invalidFileName = "file<name>.txt";

        // When
        boolean result = validator.isValid(invalidFileName);

        // Then
        assertFalse(result);
        assertThat(validator.getViolations())
            .contains("文件名不能包含特殊字符: <");
    }
}
```

### 1.2 模块集成测试 (Module Integration Tests)
- **位置**: 各模块 `src/test/java`
- **基类**: `BaseIntegrationTest`
- **范围**: 模块内多个组件协作测试
- **特点**:
  - 使用 `@SpringBootTest` 加载部分容器
  - 使用 Testcontainers 提供真实数据库
  - 使用 `@MockBean` 模拟外部依赖
  - 使用 `@Transactional` 自动回滚

**示例**:
```java
@SpringBootTest
class FileServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private FileService fileService;

    @Autowired
    private FileRepository fileRepository;

    @MockBean
    private StorageAdapter storageAdapter;

    @Test
    void shouldCreateFileWithMetadata() {
        // Given
        when(storageAdapter.upload(any())).thenReturn(
            new UploadResult("storage-key-123", "https://cdn.example.com/file")
        );
        var request = FileUploadRequest.builder()
            .name("test.pdf")
            .size(1024L)
            .creator("user@example.com")
            .tags("document,important")
            .build();

        // When
        var response = fileService.upload(request);

        // Then
        assertNotNull(response.fkey());
        assertEquals("test.pdf", response.name());
        
        var savedFile = fileRepository.findByFkey(response.fkey());
        assertTrue(savedFile.isPresent());
        assertEquals("user@example.com", savedFile.get().getCreator());
        
        verify(storageAdapter).upload(argThat(req -> 
            req.getFileName().equals("test.pdf")
        ));
    }
}
```

### 1.3 应用集成测试 (Application Integration Tests)
- **位置**: `file-srv-test` 模块
- **基类**: `ApplicationIntegrationTestBase`
- **范围**: 完整的 HTTP 请求-响应测试
- **特点**:
  - 使用 `MockMvc` 模拟 HTTP 请求
  - 加载完整 Spring 容器
  - 测试完整业务流程
  - 验证端到端功能

**示例**:
```java
@SpringBootTest(classes = IntegrationTestApplication.class)
class FileUploadIntegrationTest extends ApplicationIntegrationTestBase {

    @MockBean
    private StorageAdapter storageAdapter;

    @Test
    void shouldUploadFileSuccessfully() throws Exception {
        // Given
        when(storageAdapter.upload(any())).thenReturn(
            new UploadResult("storage-key-123", "https://cdn.example.com/file")
        );
        String requestJson = """
            {
              "name": "test.txt",
              "size": 1024,
              "creator": "user@example.com",
              "access": {"isPublic": true},
              "tags": "document",
              "customMetadata": {"department": "IT"}
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/files/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fkey").exists())
            .andExpect(jsonPath("$.name").value("test.txt"))
            .andExpect(jsonPath("$.size").value(1024))
            .andExpect(jsonPath("$.access.isPublic").value(true))
            .andExpect(jsonPath("$.tags").value("document"))
            .andExpect(jsonPath("$.customMetadata.department").value("IT"));
    }

    @Test
    void shouldReturn400WhenFileNameIsTooLong() throws Exception {
        // Given
        String longName = "a".repeat(256) + ".txt";
        String requestJson = String.format("""
            {
              "name": "%s",
              "size": 1024,
              "creator": "user@example.com"
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

## 2. 覆盖率目标

### 2.1 模块覆盖率要求
| 模块 | 行覆盖率 | 分支覆盖率 | 说明 |
|------|---------|-----------|------|
| file-srv-common | ≥ 60% | ≥ 50% | 工具类、枚举、常量 |
| file-srv-core | ≥ 80% | ≥ 70% | 核心业务逻辑 |
| file-srv-adapters | ≥ 70% | ≥ 60% | 外部集成适配器 |
| file-srv-aspect | ≥ 70% | ≥ 60% | 切面逻辑 |

### 2.2 JaCoCo 排除规则
以下类型自动排除，不计入覆盖率:
- 常量类 (`*Constants.class`)
- 配置类 (`*Configuration.class`, `*Config.class`)
- 简单 DTO/VO (`entrypoint/dto/**`, `entrypoint/vo/**`)
- 自动配置 (`autoconfiguration/**`)

### 2.3 覆盖率检查
```bash
# 运行测试并生成覆盖率报告
mvn clean verify

# 查看覆盖率报告
open file-srv-core/target/site/jacoco/index.html
```

## 3. 测试命名约定

### 3.1 测试类命名
```
<被测试类名> + Test/IntegrationTest

例如:
- FileServiceTest (单元测试)
- FileServiceIntegrationTest (集成测试)
- FileUploadIntegrationTest (应用集成测试)
```

### 3.2 测试方法命名
使用 `should...When...` 或 `should...` 模式:

```java
@Test
void shouldReturnFileInfoWhenFkeyExists() { }

@Test
void shouldThrowNotFoundExceptionWhenFkeyNotExists() { }

@Test
void shouldCreateFileSuccessfully() { }
```

## 4. Given-When-Then 模式

所有测试必须遵循 Given-When-Then 结构:

```java
@Test
void shouldCalculateDiscountCorrectly() {
    // Given - 准备测试数据和环境
    var product = new Product("book", 100.0);
    var discount = new Discount(0.2); // 20% off
    
    // When - 执行被测试的操作
    var finalPrice = discount.apply(product);
    
    // Then - 验证结果
    assertEquals(80.0, finalPrice);
}
```

## 5. 测试数据构造

### 5.1 使用 Builder 模式
```java
var request = FileUploadRequest.builder()
    .name("test.pdf")
    .size(1024L)
    .creator("user@example.com")
    .tags("document")
    .build();
```

### 5.2 创建测试数据工厂
```java
public class TestDataFactory {
    public static FileUploadRequest createDefaultUploadRequest() {
        return FileUploadRequest.builder()
            .name("test-file.txt")
            .size(1024L)
            .creator("test@example.com")
            .access(AccessControl.defaultAccess())
            .build();
    }

    public static FileUploadRequest createLargeFileRequest() {
        return createDefaultUploadRequest()
            .toBuilder()
            .size(100 * 1024 * 1024L) // 100MB
            .build();
    }
}
```

## 6. Mock 使用规范

### 6.1 单元测试中的 Mock
```java
class FileServiceTest extends BaseUnitTest {
    @Mock
    private FileRepository fileRepository;
    
    @Mock
    private StorageAdapter storageAdapter;
    
    @InjectMocks
    private FileService fileService;
    
    @Test
    void shouldUploadFile() {
        // Given
        when(storageAdapter.upload(any())).thenReturn(uploadResult);
        when(fileRepository.save(any())).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });
        
        // When
        var response = fileService.upload(request);
        
        // Then
        verify(storageAdapter).upload(any());
        verify(fileRepository).save(argThat(entity -> 
            entity.getName().equals("test.txt")
        ));
    }
}
```

### 6.2 集成测试中的 MockBean
```java
@SpringBootTest
class FileServiceIntegrationTest extends BaseIntegrationTest {
    @MockBean
    private StorageAdapter storageAdapter; // Mock 外部依赖
    
    @Autowired
    private FileService fileService; // 注入真实的 Service
    
    @Autowired
    private FileRepository fileRepository; // 使用真实的 Repository
}
```

## 7. 异常测试

### 7.1 使用 assertThrows
```java
@Test
void shouldThrowExceptionWhenFileNotFound() {
    // Given
    String nonExistentFkey = "non-existent";
    
    // When & Then
    var exception = assertThrows(
        FileNotFoundException.class,
        () -> fileService.getFile(nonExistentFkey)
    );
    
    assertEquals("文件不存在: " + nonExistentFkey, exception.getMessage());
}
```

### 7.2 验证异常场景
```java
@Test
void shouldReturn404WhenFileNotFound() throws Exception {
    // Given
    String fkey = "non-existent";
    
    // When & Then
    mockMvc.perform(get("/api/v1/files/{fkey}", fkey))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("文件不存在"));
}
```

## 8. 异步测试

### 8.1 测试异步任务
```java
@Test
void shouldCompleteAsyncUploadTask() throws Exception {
    // Given
    var taskId = "task-123";
    when(storageAdapter.upload(any())).thenReturn(uploadResult);
    
    // When
    taskService.createTask(request);
    
    // Wait for async completion
    await().atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> {
            var task = taskService.getTask(taskId);
            assertEquals(TaskStatus.COMPLETED, task.getStatus());
        });
    
    // Then
    verify(storageAdapter).upload(any());
}
```

## 9. 测试执行

### 9.1 运行所有测试
```bash
mvn test
```

### 9.2 运行特定模块测试
```bash
mvn test -pl file-srv-core
```

### 9.3 运行特定标签测试
```bash
# 只运行单元测试
mvn test -Dgroups=unit

# 只运行集成测试
mvn test -Dgroups=integration

# 只运行应用集成测试
mvn test -Dgroups=app-integration
```

### 9.4 跳过测试
```bash
mvn package -DskipTests
```

## 10. 持续集成配置

### 10.1 CI 流程
1. 执行所有测试
2. 生成覆盖率报告
3. 检查覆盖率阈值
4. 失败时阻止合并

### 10.2 GitHub Actions 配置示例
```yaml
name: Test and Coverage

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Run tests with coverage
        run: mvn clean verify
      
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          file: ./target/site/jacoco/jacoco.xml
```

## 11. 最佳实践总结

### 11.1 DO ✅
- 每个测试只验证一个行为
- 使用 Given-When-Then 结构
- 测试方法名清晰描述测试场景
- Mock 外部依赖，测试真实业务逻辑
- 使用 `@Transactional` 确保测试数据隔离
- 优先编写单元测试，补充集成测试
- 使用 Builder 模式构造测试数据
- 验证关键的业务规则和边界条件

### 11.2 DON'T ❌
- 不要在测试中使用 Thread.sleep()
- 不要测试 framework 代码 (如 Spring 本身)
- 不要在单元测试中加载 Spring 容器
- 不要在测试间共享可变状态
- 不要忽略测试失败
- 不要为了覆盖率而写无意义的测试
- 不要在测试中使用真实的外部服务

## 12. 附录

### 12.1 常用断言
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

### 12.2 MockMvc 常用操作
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

### 12.3 Testcontainers 配置
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
