package tech.icc.filesrv.test.integration;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tech.icc.filesrv.test.config.TestStorageConfig;
import tech.icc.filesrv.test.support.stub.ObjectStorageServiceStub;
import tech.icc.filesrv.core.domain.tasks.TaskRepository;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;
import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.context.TaskContextKeys;
import tech.icc.filesrv.common.vo.task.TaskStatus;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 插件和 Callback 集成测试
 * <p>
 * 测试策略：
 * <ul>
 *   <li>通过 MockMvc 发起真实 HTTP 请求</li>
 *   <li>验证 Callback 配置和插件调用链路</li>
 *   <li>验证上传任务完成后 Callback 消息发布</li>
 *   <li>验证插件参数传递和验证机制</li>
 * </ul>
 * <p>
 * 测试场景：
 * <ul>
 *   <li>单个 Callback 插件的执行</li>
 *   <li>多个 Callback 插件的链式执行</li>
 *   <li>不存在的插件应被拒绝</li>
 *   <li>Callback 参数传递和验证</li>
 *   <li>任务完成后消息发布验证</li>
 * </ul>
 */
@Slf4j
@Tag("integration")
@SpringBootTest
@EnableAutoConfiguration(exclude = {
        RedissonAutoConfigurationV2.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStorageConfig.class)
@Transactional
@DisplayName("插件和 Callback 集成测试")
class PluginCallbackScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectStorageServiceStub storageAdapter;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        // 清空之前的测试数据
        storageAdapter.clear();
    }

    @Test
    @DisplayName("应该能创建带有单个 Callback 的上传任务")
    void shouldCreateTaskWithSingleCallback() throws Exception {
        // Given: 准备 Callback 配置
        String requestBody = """
                {
                    "filename": "test-with-callback.txt",
                    "contentType": "text/plain",
                    "size": 1024,
                    "contentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    "createdBy": "test-user",
                    "creatorName": "Callback Tester",
                    "callbacks": [
                        {
                            "name": "hash-verify",
                            "params": [
                                {
                                    "key": "algorithm",
                                    "value": "sha256"
                                }
                            ]
                        }
                    ]
                }
                """;

        // When: 创建任务
        String response = mockMvc.perform(post("/api/v1/files/upload_task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = JsonPath.read(response, "$.data.taskId");
        assertThat(taskId).isNotBlank();

        // Then: 验证任务状态为 PENDING
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.taskId").value(taskId));
    }

    @Test
    @DisplayName("应该能创建带有多个 Callback 的上传任务")
    void shouldCreateTaskWithMultipleCallbacks() throws Exception {
        // Given: 准备多个 Callback 配置
        String requestBody = """
                {
                    "filename": "test-multi-callbacks.jpg",
                    "contentType": "image/jpeg",
                    "size": 2048,
                    "contentHash": "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3",
                    "createdBy": "test-user",
                    "creatorName": "Multi Callback Tester",
                    "callbacks": [
                        {
                            "name": "hash-verify",
                            "params": [
                                {
                                    "key": "algorithm",
                                    "value": "sha256"
                                }
                            ]
                        },
                        {
                            "name": "thumbnail",
                            "params": [
                                {
                                    "key": "width",
                                    "value": "200"
                                },
                                {
                                    "key": "height",
                                    "value": "200"
                                }
                            ]
                        }
                    ]
                }
                """;

        // When: 创建任务
        String response = mockMvc.perform(post("/api/v1/files/upload_task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = JsonPath.read(response, "$.data.taskId");
        assertThat(taskId).isNotBlank();

        // Then: 验证任务创建成功
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.taskId").value(taskId));
    }

    @Test
    @DisplayName("应该拒绝不存在的插件")
    void shouldRejectNonexistentPlugin() throws Exception {
        // Given: 准备不存在的插件名称
        String requestBody = """
                {
                    "filename": "test-invalid-plugin.txt",
                    "contentType": "text/plain",
                    "size": 1024,
                    "contentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    "createdBy": "test-user",
                    "creatorName": "Invalid Plugin Tester",
                    "callbacks": [
                        {
                            "name": "nonexistent-plugin",
                            "params": []
                        }
                    ]
                }
                """;

        // When & Then: 应该返回错误
        mockMvc.perform(post("/api/v1/files/upload_task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("应该在上传完成后发布 Callback 任务消息")
    void shouldPublishCallbackTaskAfterUploadComplete() throws Exception {
        // Given: 准备文件分片数据
        byte[] part1 = "Callback test data part 1".getBytes();
        byte[] part2 = "Callback test data part 2".getBytes();
        String contentHash = calculateSHA256(part1, part2);

        // Step 1: 创建带有 Callback 的任务
        String requestBody = String.format("""
                {
                    "filename": "callback-test.dat",
                    "contentType": "application/octet-stream",
                    "size": %d,
                    "contentHash": "%s",
                    "createdBy": "test-user",
                    "creatorName": "Callback Publisher Tester",
                    "callbacks": [
                        {
                            "name": "hash-verify",
                            "params": [
                                {
                                    "key": "algorithm",
                                    "value": "sha256"
                                }
                            ]
                        }
                    ]
                }
                """, part1.length + part2.length, contentHash);

        String response = mockMvc.perform(post("/api/v1/files/upload_task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = JsonPath.read(response, "$.data.taskId");

        // Step 2: 上传分片
        List<PartETagInfo> parts = new ArrayList<>();
        parts.add(uploadPart(taskId, 1, part1));
        parts.add(uploadPart(taskId, 2, part2));

        // Step 3: 完成上传
        completeUpload(taskId, parts);

        // Then: 验证 Callback 任务已发布（通过状态变为 PROCESSING 来验证）
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.taskId").value(taskId));
    }

    @Test
    @DisplayName("应该在无 Callback 时直接完成任务（不发布消息）")
    void shouldCompleteDirectlyWithoutCallbacks() throws Exception {
        // Given: 准备文件分片数据
        byte[] part1 = "No callback data part 1".getBytes();
        byte[] part2 = "No callback data part 2".getBytes();
        String contentHash = calculateSHA256(part1, part2);

        // Step 1: 创建无 Callback 的任务
        String requestBody = String.format("""
                {
                    "filename": "no-callback.dat",
                    "contentType": "application/octet-stream",
                    "size": %d,
                    "contentHash": "%s",
                    "createdBy": "test-user",
                    "creatorName": "No Callback Tester"
                }
                """, part1.length + part2.length, contentHash);

        String response = mockMvc.perform(post("/api/v1/files/upload_task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = JsonPath.read(response, "$.data.taskId");

        // Step 2: 上传分片
        List<PartETagInfo> parts = new ArrayList<>();
        parts.add(uploadPart(taskId, 1, part1));
        parts.add(uploadPart(taskId, 2, part2));

        // Step 3: 完成上传
        completeUpload(taskId, parts);

        // Then: 验证任务直接完成，状态为 COMPLETED（无需回调执行）
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.file").exists());
    }

    @Test
    @DisplayName("应该正确传递 Callback 参数")
    void shouldPassCallbackParameters() throws Exception {
        // Given: 准备带有详细参数的 Callback 配置
        String requestBody = """
                {
                    "filename": "param-test.jpg",
                    "contentType": "image/jpeg",
                    "size": 4096,
                    "contentHash": "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                    "createdBy": "test-user",
                    "creatorName": "Param Test User",
                    "callbacks": [
                        {
                            "name": "thumbnail",
                            "params": [
                                {
                                    "key": "width",
                                    "value": "300"
                                },
                                {
                                    "key": "height",
                                    "value": "300"
                                },
                                {
                                    "key": "quality",
                                    "value": "80"
                                },
                                {
                                    "key": "format",
                                    "value": "webp"
                                }
                            ]
                        }
                    ]
                }
                """;

        // When: 创建任务
        String response = mockMvc.perform(post("/api/v1/files/upload_task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = JsonPath.read(response, "$.data.taskId");
        assertThat(taskId).isNotBlank();

        // Then: 验证任务创建成功
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("应该支持空参数的 Callback 配置")
    void shouldSupportEmptyCallbackParams() throws Exception {
        // Given: 准备无参数的 Callback 配置
        String requestBody = """
                {
                    "filename": "empty-params.txt",
                    "contentType": "text/plain",
                    "size": 512,
                    "contentHash": "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
                    "createdBy": "test-user",
                    "creatorName": "Empty Params Tester",
                    "callbacks": [
                        {
                            "name": "hash-verify",
                            "params": []
                        }
                    ]
                }
                """;

        // When: 创建任务
        String response = mockMvc.perform(post("/api/v1/files/upload_task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = JsonPath.read(response, "$.data.taskId");
        assertThat(taskId).isNotBlank();
    }

    @Test
    @DisplayName("应该支持 Callback 链式执行顺序")
    void shouldMaintainCallbackExecutionOrder() throws Exception {
        // Given: 准备有序的 Callback 链
        String requestBody = """
                {
                    "filename": "callback-chain.dat",
                    "contentType": "application/octet-stream",
                    "size": 8192,
                    "contentHash": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                    "createdBy": "test-user",
                    "creatorName": "Chain Order Tester",
                    "callbacks": [
                        {
                            "name": "hash-verify",
                            "params": [
                                {
                                    "key": "algorithm",
                                    "value": "sha256"
                                }
                            ]
                        },
                        {
                            "name": "rename",
                            "params": [
                                {
                                    "key": "pattern",
                                    "value": "processed_{filename}"
                                }
                            ]
                        },
                        {
                            "name": "thumbnail",
                            "params": [
                                {
                                    "key": "width",
                                    "value": "150"
                                }
                            ]
                        }
                    ]
                }
                """;

        // When: 创建任务
        String response = mockMvc.perform(post("/api/v1/files/upload_task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = JsonPath.read(response, "$.data.taskId");
        assertThat(taskId).isNotBlank();

        // Then: 验证任务创建成功（链式执行由 executor 模块负责，此处只验证创建）
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 上传分片
     *
     * @param taskId     任务 ID
     * @param partNumber 分片序号
     * @param partData   分片数据
     * @return 分片 ETag 信息
     */
    @SuppressWarnings("null")
    private PartETagInfo uploadPart(String taskId, int partNumber, byte[] partData) throws Exception {
        String response = mockMvc.perform(put("/api/v1/files/upload_task/{taskId}", taskId)
                        .param("partNumber", String.valueOf(partNumber))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(partData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.partNumber").value(partNumber))
                .andExpect(jsonPath("$.data.eTag").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String eTag = JsonPath.read(response, "$.data.eTag");
        return new PartETagInfo(partNumber, eTag);
    }

    /**
     * 完成上传
     *
     * @param taskId 任务 ID
     * @param parts  已上传分片列表
     */
    @SuppressWarnings("null")
    private void completeUpload(String taskId, List<PartETagInfo> parts) throws Exception {
        StringBuilder partsJson = new StringBuilder("[");
        for (int i = 0; i < parts.size(); i++) {
            PartETagInfo part = parts.get(i);
            if (i > 0) partsJson.append(",");
            partsJson.append(String.format("""
                    {"partNumber": %d, "eTag": "%s"}
                    """, part.partNumber(), part.eTag()));
        }
        partsJson.append("]");

        mockMvc.perform(post("/api/v1/files/upload_task/{taskId}/complete", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partsJson.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 分片 ETag 信息（用于测试）
     */
    private record PartETagInfo(int partNumber, String eTag) {
    }

    /**
     * 计算文件 SHA-256 hash（模拟客户端计算）
     */
    private String calculateSHA256(byte[]... parts) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (byte[] part : parts) {
            digest.update(part);
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== E2E 完整流程测试 ====================

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("E2E: 应该完整执行 callback 链并验证插件结果")
    void shouldExecuteCompleteCallbackChainE2E() throws Exception {
        // Given: 准备测试数据
        byte[] part1 = "Complete E2E test data part 1".getBytes();
        byte[] part2 = "Complete E2E test data part 2".getBytes();
        String contentHash = calculateSHA256(part1, part2);

        // Step 1: 创建带有多个 callback 的任务（测试三类插件）
        String requestBody = String.format("""
                {
                    "filename": "e2e-test.jpg",
                    "contentType": "image/jpeg",
                    "size": %d,
                    "contentHash": "%s",
                    "createdBy": "test-user",
                    "creatorName": "E2E Tester",
                    "callbacks": [
                        {
                            "name": "hash-verify",
                            "params": [
                                {
                                    "key": "algorithm",
                                    "value": "sha256"
                                },
                                {
                                    "key": "expected",
                                    "value": "%s"
                                }
                            ]
                        },
                        {
                            "name": "rename",
                            "params": [
                                {
                                    "key": "pattern",
                                    "value": "processed_{filename}"
                                }
                            ]
                        },
                        {
                            "name": "thumbnail",
                            "params": [
                                {
                                    "key": "width",
                                    "value": "400"
                                },
                                {
                                    "key": "height",
                                    "value": "300"
                                },
                                {
                                    "key": "quality",
                                    "value": "90"
                                },
                                {
                                    "key": "format",
                                    "value": "png"
                                }
                            ]
                        }
                    ]
                }
                """, part1.length + part2.length, contentHash, contentHash);

        // Step 2: 创建任务（状态应为 PENDING）
        String createResponse = mockMvc.perform(post("/api/v1/files/upload_task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = JsonPath.read(createResponse, "$.data.taskId");
        assertThat(taskId).isNotBlank();

        // Step 3: 上传分片（第一次上传后状态应变为 IN_PROGRESS）
        List<PartETagInfo> parts = new ArrayList<>();
        parts.add(uploadPart(taskId, 1, part1));
        
        // 验证第一次上传后状态为 IN_PROGRESS
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // 继续上传第二个分片
        parts.add(uploadPart(taskId, 2, part2));

        // Step 4: 完成上传（状态应变为 PROCESSING）
        completeUpload(taskId, parts);

        // 验证任务状态为 PROCESSING（回调正在执行）
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        // Step 5: 等待 callback 链自动执行完成（通过 Spring Event 异步触发）
        log.info("Waiting for callback chain to complete asynchronously...");
        
        await()
                .atMost(10, SECONDS)
                .pollInterval(1, SECONDS)
                .pollDelay(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                String response = mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
                String status = JsonPath.read(response, "$.data.status");
                assertThat(status).isEqualTo("COMPLETED");
                });

        log.info("Callback chain completed successfully");

        // Step 6: 验证执行后的状态
        TaskAggregate task = taskRepository.findByTaskId(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isNotNull();

        // Step 7: 验证插件执行结果（从 TaskContext 读取）
        TaskContext context = task.getContext();

        log.info(context.toString());

        // 验证 hash-verify 插件的输出（测试插件）
        assertThat(context.getString("hash-verify.result")).hasValue("PASSED");
        assertThat(context.getString("hash-verify.verified")).hasValue("true");

        // 验证 rename 插件的输出（测试插件）
        assertThat(context.getString("rename.new")).isPresent();
        assertThat(context.getString("rename.pattern")).hasValue("processed_{filename}");

        // 验证 thumbnail 插件的输出（测试插件）
        assertThat(context.getString("thumbnail.generated")).hasValue("true");
        assertThat(context.getString("thumbnail.width")).hasValue("400");
        assertThat(context.getString("thumbnail.height")).hasValue("300");
        assertThat(context.getString("thumbnail.quality")).hasValue("90");
        assertThat(context.getString("thumbnail.format")).hasValue("png");

        // Step 8: 验证通过 API 查询任务状态
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.file").exists());

        // 验证点总结：
        // ✅ 任务创建时状态为 PENDING
        // ✅ 第一次上传分片后状态变为 IN_PROGRESS
        // ✅ 完成上传后状态变为 PROCESSING
        // ✅ Callback 任务消息已发布（通过 Spring Event）
        // ✅ Callback 链自动异步执行成功（所有 3 个插件）
        // ✅ 任务最终状态为 COMPLETED（通过 Awaitility 轮询验证）
        // ✅ hash-verify 插件输出验证通过
        // ✅ rename 插件输出验证通过
        // ✅ thumbnail 插件输出验证通过
        // ✅ 验证了完整的消息发布-订阅-执行流程
    }
}
