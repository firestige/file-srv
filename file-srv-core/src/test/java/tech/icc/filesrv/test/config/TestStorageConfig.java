package tech.icc.filesrv.test.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tech.icc.filesrv.config.FileControllerConfig;
import tech.icc.filesrv.core.infra.executor.CallbackTaskPublisher;
import tech.icc.filesrv.core.infra.event.TaskEventPublisher;
import tech.icc.filesrv.test.support.stub.CallbackTaskPublisherStub;
import tech.icc.filesrv.test.support.stub.ObjectStorageServiceStub;
import tech.icc.filesrv.test.support.stub.TaskEventPublisherStub;

import java.nio.file.Path;
import java.time.Duration;

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
     * 使用 @Primary 确保在测试环境中优先使用此实现，
     * 覆盖可能存在的生产环境 StorageAdapter Bean。
     * 
     * @return 内存存储 stub
     */
    @Bean
    @Primary
    public ObjectStorageServiceStub testStorageAdapter() {
        return new ObjectStorageServiceStub();
    }
    
    /**
     * 创建任务事件发布器 Stub（模拟 Kafka 事件发布）
     * <p>
     * 使用 @Primary 确保在测试环境中优先使用此实现。
     * 用于发布领域事件（任务完成/失败通知）。
     * 
     * @return 事件发布器 stub
     */
    @Bean
    @Primary
    public TaskEventPublisher testTaskEventPublisher() {
        return new TaskEventPublisherStub();
    }
    
    /**
     * 创建 Callback 任务发布器 Stub（模拟 Kafka 任务队列）
     * <p>
     * 使用 @Primary 确保在测试环境中优先使用此实现。
     * 用于发布 Callback 执行任务（调度消息）。
     * 
     * @return Callback 任务发布器 stub
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
}
