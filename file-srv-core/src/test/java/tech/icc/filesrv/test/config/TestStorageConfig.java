package tech.icc.filesrv.test.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;
import tech.icc.filesrv.config.FileControllerConfig;
import tech.icc.filesrv.core.domain.tasks.TaskRepository;
import tech.icc.filesrv.core.infra.event.TaskEventPublisher;
import tech.icc.filesrv.core.infra.executor.CallbackChainRunner;
import tech.icc.filesrv.core.infra.executor.CallbackTaskPublisher;
import tech.icc.filesrv.core.infra.executor.ExecutorProperties;
import tech.icc.filesrv.core.infra.executor.impl.DefaultCallbackChainRunner;
import tech.icc.filesrv.core.infra.file.LocalFileManager;
import tech.icc.filesrv.core.infra.plugin.PluginRegistry;
import tech.icc.filesrv.test.support.plugin.TestHashVerifyPlugin;
import tech.icc.filesrv.test.support.plugin.TestRenamePlugin;
import tech.icc.filesrv.test.support.plugin.TestThumbnailPlugin;
import tech.icc.filesrv.test.support.stub.CallbackTaskPublisherStub;
import tech.icc.filesrv.test.support.stub.ObjectStorageServiceStub;
import tech.icc.filesrv.test.support.stub.TaskEventPublisherStub;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 集成测试的基础设施配置
 * <p>
 * 提供测试环境的基础设施实现：
 * <ul>
 *   <li>内存存储 Stub：模拟对象存储（OBS/S3）</li>
 *   <li>事件发布器 Stub：模拟 Kafka 事件发布（领域事件）</li>
 *   <li>任务发布器 Stub：模拟 Kafka 任务队列（Callback 调度）</li>
 *   <li>自动注册到容器</li>
 *   <li>无需 Mock，提供真实的行为</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>{@code
 * @SpringBootTest
 * @Import(TestStorageConfig.class)
 * class FileServiceIntegrationTest {
 *     
 *     @Autowired
 *     private StorageAdapter storageAdapter;
 *     
 *     @Autowired
 *     private TaskEventPublisher eventPublisher;
 *     
 *     @Autowired
 *     private CallbackTaskPublisher callbackPublisher;
 *     
 *     @Test
 *     void shouldUploadFile() {
 *         // 直接使用，无需 Mock
 *     }
 * }
 * }</pre>
 */
@TestConfiguration
public class TestStorageConfig {
    
    /**
     * 创建内存存储适配器（模拟对象存储）
     * <p>
     * <strong>设计决策：</strong> 此 Stub 替代真实的对象存储服务（如华为云 OBS、AWS S3），用于测试环境中模拟文件存储。
     * <p>
     * <strong>为什么需要这个 Stub：</strong>
     * <ul>
     *   <li><strong>隔离外部依赖：</strong>测试不依赖真实的云存储服务，避免网络调用和凭证配置</li>
     *   <li><strong>快速执行：</strong>内存实现，读写速度快，测试执行效率高</li>
     *   <li><strong>数据隔离：</strong>每次测试独立的内存空间，测试间无污染</li>
     *   <li><strong>验证交互：</strong>可以验证分片上传、合并、下载等操作的正确性</li>
     *   <li><strong>无成本：</strong>不产生云服务费用，适合 CI/CD 环境</li>
     * </ul>
     * <p>
     * <strong>后期决策点：</strong>
     * <ul>
     *   <li><strong>保留：</strong>如果测试重点是"业务逻辑验证"，应保留此 Stub（推荐）</li>
     *   <li><strong>替换：</strong>如果需要验证云存储 SDK 集成问题，可使用 MinIO/LocalStack 等本地模拟服务</li>
     *   <li><strong>混合：</strong>单元/集成测试用 Stub，Contract 测试用真实云存储</li>
     * </ul>
     * <p>
     * <strong>功能支持：</strong>
     * <ul>
     *   <li>分片上传（multipart upload）</li>
     *   <li>文件合并（complete upload）</li>
     *   <li>文件下载（getObject）</li>
     *   <li>ETag 生成和校验</li>
     * </ul>
     * 
     * @return 内存存储 stub
     * @see ObjectStorageServiceStub
     */
    @Bean
    @Primary
    public ObjectStorageServiceStub testStorageAdapter() {
        return new ObjectStorageServiceStub();
    }
    
    /**
     * 创建任务事件发布器 Stub（模拟 Kafka 事件发布）
     * <p>
     * <strong>设计决策：</strong> 此 Stub 替代真实的 Kafka 事件发布器，用于测试环境中模拟领域事件发布。
     * <p>
     * <strong>为什么需要这个 Stub：</strong>
     * <ul>
     *   <li><strong>隔离外部依赖：</strong>测试不依赖真实的 Kafka 集群，简化测试环境</li>
     *   <li><strong>验证事件发布：</strong>可以验证领域事件是否被正确发布（任务完成/失败通知）</li>
     *   <li><strong>同步测试：</strong>集成测试关注业务逻辑，不需要真实的异步事件总线</li>
     *   <li><strong>轻量级：</strong>内存实现，无需额外资源和配置</li>
     * </ul>
     * <p>
     * <strong>后期决策点：</strong>
     * <ul>
     *   <li><strong>保留：</strong>如果测试重点是"领域逻辑验证"，应保留此 Stub（推荐）</li>
     *   <li><strong>替换：</strong>如果需要验证事件消费者集成，可引入 TestContainers + 真实 Kafka</li>
     *   <li><strong>混合：</strong>集成测试用 Stub（快速反馈），E2E 测试用真实 Kafka（全链路）</li>
     * </ul>
     * <p>
     * <strong>事件类型：</strong>
     * <ul>
     *   <li>任务创建事件（TaskCreatedEvent）</li>
     *   <li>任务完成事件（TaskCompletedEvent）</li>
     *   <li>任务失败事件（TaskFailedEvent）</li>
     * </ul>
     * 
     * @return 事件发布器 stub
     * @see TaskEventPublisherStub
     */
    @Bean
    @Primary
    public TaskEventPublisher testTaskEventPublisher() {
        return new TaskEventPublisherStub();
    }
    
    /**
     * 创建 Callback 任务发布器 Stub（模拟 Kafka 任务队列）
     * <p>
     * <strong>设计决策：</strong> 此 Stub 替代真实的 Kafka 消息发布器，用于测试环境中模拟异步任务调度。
     * <p>
     * <strong>为什么需要这个 Stub：</strong>
     * <ul>
     *   <li><strong>隔离外部依赖：</strong>测试不依赖真实的 Kafka 集群，避免环境配置复杂度</li>
     *   <li><strong>验证发布行为：</strong>可以验证消息是否被正确发布（isPublished），以及发布次数（getPublishedCount）</li>
     *   <li><strong>同步测试：</strong>集成测试需要同步验证结果，不需要真实的异步消息队列</li>
     *   <li><strong>轻量级：</strong>内存实现，无需额外资源，测试启动快速</li>
     * </ul>
     * <p>
     * <strong>后期决策点：</strong>
     * <ul>
     *   <li><strong>保留：</strong>如果测试策略是"隔离外部依赖 + 快速反馈"，应保留此 Stub</li>
     *   <li><strong>替换：</strong>如果需要端到端测试（E2E），可以引入 TestContainers + 真实 Kafka</li>
     *   <li><strong>混合：</strong>集成测试用 Stub（快速），E2E 测试用真实 Kafka（全链路）</li>
     * </ul>
     * <p>
     * <strong>当前架构：</strong>
     * <ul>
     *   <li>核心插件系统（CallbackChainRunner, PluginRegistry）使用真实实现</li>
     *   <li>外部依赖（Kafka, 存储, Redis）使用 Stub</li>
     *   <li>测试通过直接调用 {@code callbackRunner.run(task)} 验证插件执行</li>
     * </ul>
     * 
     * @return Callback 任务发布器 stub
     * @see CallbackTaskPublisherStub
     * @see tech.icc.filesrv.test.integration.PluginCallbackScenarioTest#shouldExecuteCompleteCallbackChainE2E()
     */
    @Bean
    @Primary
    public CallbackTaskPublisher testCallbackTaskPublisher() {
        return new CallbackTaskPublisherStub();
    }
    
    /**
     * 可选：创建文件系统存储适配器（模拟 NFS/NAS）
     * <p>
     * 如需测试文件系统场景，可取消注释此 Bean
     */
    // @Bean("filesystemStorageAdapter")
    // public StorageAdapter filesystemStorageAdapter() {
    //     return FileSystemStorageStub.createTemp();
    // }

    @Bean
    public FileControllerConfig fileControllerConfig() {
        return new FileControllerConfig(
                128,
                Duration.ofHours(1).toSeconds(),
                Duration.ofMinutes(1).toSeconds(),
                Duration.ofDays(7).toSeconds(),
                10,
                100
        );
    }

    @Bean
    Path tempBaseDir() {
        return Path.of("/");
    }

    // ==================== 测试用插件 Bean ====================

    /**
     * 注册测试用 Hash 验证插件
     * <p>
     * 模拟文件哈希验证功能，始终返回成功。
     * 
     * @return Hash 验证插件
     */
    @Bean
    public SharedPlugin testHashVerifyPlugin() {
        return new TestHashVerifyPlugin();
    }

    /**
     * 注册测试用缩略图生成插件
     * <p>
     * 模拟图片缩略图生成功能，始终返回成功。
     * 
     * @return 缩略图插件
     */
    @Bean
    public SharedPlugin testThumbnailPlugin() {
        return new TestThumbnailPlugin();
    }

    /**
     * 注册测试用文件重命名插件
     * <p>
     * 模拟文件重命名功能，始终返回成功。
     * 
     * @return 重命名插件
     */
    @Bean
    public SharedPlugin testRenamePlugin() {
        return new TestRenamePlugin();
    }

    // ==================== Callback 执行器配置 ====================

    /**
     * 测试用 ExecutorService（用于 callback 超时控制）
     * <p>
     * 模拟生产环境的超时执行器，但使用较小的线程池。
     *
     * @return ExecutorService 实例
     */
    @Bean(name = "callbackTimeoutExecutor", destroyMethod = "shutdown")
    public ExecutorService callbackTimeoutExecutor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "test-callback-executor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 测试用 ExecutorProperties
     * <p>
     * 提供适合测试环境的超时和重试配置。
     *
     * @return ExecutorProperties 实例
     */
    @Bean
    public ExecutorProperties testExecutorProperties() {
        return new ExecutorProperties(
                new ExecutorProperties.KafkaConfig("test-topic", "test-dlt", "test-group", 1),
                new ExecutorProperties.TimeoutConfig(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofHours(1)),
                new ExecutorProperties.RetryConfig(2, Duration.ofMillis(100), 2.0, Duration.ofSeconds(5)),
                new ExecutorProperties.IdempotencyConfig(Duration.ofHours(1))
        );
    }

//    /**
//     * 注册真实的 CallbackChainRunner
//     * <p>
//     * 使用真实的插件系统执行器，验证插件 API 的正确性。
//     * Stub 的是外部依赖（Kafka、存储），不 stub 核心插件系统。
//     *
//     * @return CallbackChainRunner 实例
//     */
//    @Bean
//    public CallbackChainRunner testCallbackChainRunner(
//            TaskRepository taskRepository,
//            PluginRegistry pluginRegistry,
//            LocalFileManager localFileManager,
//            TaskEventPublisher eventPublisher,
//            ExecutorService callbackTimeoutExecutor,
//            ExecutorProperties executorProperties) {
//        return new DefaultCallbackChainRunner(
//                taskRepository,
//                pluginRegistry,
//                localFileManager,
//                eventPublisher,
//                callbackTimeoutExecutor,
//                executorProperties
//        );
//    }
}
