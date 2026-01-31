package tech.icc.filesrv.test.integration;

import com.jayway.jsonpath.JsonPath;
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
import tech.icc.filesrv.common.constants.ResultCode;
import tech.icc.filesrv.test.config.TestStorageConfig;
import tech.icc.filesrv.test.support.stub.ObjectStorageServiceStub;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分片上传核心场景集成测试
 * <p>
 * 测试策略：
 * <ul>
 *   <li>通过 MockMvc 发起真实 HTTP 请求</li>
 *   <li>使用 @SpyBean 监控 Service 调用链路</li>
 *   <li>验证分片上传完整流程：创建任务 → 上传分片 → 完成上传</li>
 *   <li>验证任务状态流转：PENDING → IN_PROGRESS → PROCESSING/COMPLETED</li>
 * </ul>
 * <p>
 * 适用场景：
 * <ul>
 *   <li>大文件上传（>10MB）</li>
 *   <li>断点续传</li>
 *   <li>需要 callback 处理的上传</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest
@EnableAutoConfiguration(exclude = {
        RedissonAutoConfigurationV2.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStorageConfig.class)
@Transactional
@DisplayName("分片上传核心场景集成测试")
class MultipartUploadScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectStorageServiceStub storageAdapter;

    @Test
    @DisplayName("应该能完成完整的分片上传流程（无 callback）")
    void shouldCompleteMultipartUploadWithoutCallback() throws Exception {
        // Given: 准备文件分片数据
        byte[] part1 = "Part 1: This is the first chunk of data.".getBytes();
        byte[] part2 = "Part 2: This is the second chunk of data.".getBytes();
        byte[] part3 = "Part 3: This is the final chunk of data.".getBytes();

        // Step 1: 创建上传任务
        String taskId = createTask("large-file.bin", "application/octet-stream", 
                part1.length + part2.length + part3.length);

        // Step 2: 上传分片
        List<PartETagInfo> parts = new ArrayList<>();
        parts.add(uploadPart(taskId, 1, part1));
        parts.add(uploadPart(taskId, 2, part2));
        parts.add(uploadPart(taskId, 3, part3));

        // Step 3: 完成上传
        completeUpload(taskId, parts);

        // Step 4: 验证任务状态（无 callback 应直接完成）
        String statusResponse = mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.fKey").exists())
                .andExpect(jsonPath("$.data.file").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 验证文件信息
        String fKey = JsonPath.read(statusResponse, "$.data.summary.fKey");
        assertThat(fKey).isNotBlank();

        // Step 5: 验证存储层状态
        assertThat(storageAdapter.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("应该能查询上传中的任务进度")
    void shouldGetInProgressTaskStatus() throws Exception {
        // Given: 创建任务并上传部分分片
        byte[] part1 = "Part 1 data".getBytes();
        byte[] part2 = "Part 2 data".getBytes();

        String taskId = createTask("upload-in-progress.dat", "application/octet-stream", 
                part1.length + part2.length + 1000);

        // 上传第一个分片
        uploadPart(taskId, 1, part1);

        // When: 查询任务状态
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.summary.taskId").value(taskId))
                .andExpect(jsonPath("$.data.progress").exists())
                .andExpect(jsonPath("$.data.progress.uploadedParts").value(1))
                .andExpect(jsonPath("$.data.progress.uploadedBytes").exists())
                .andExpect(jsonPath("$.data.progress.parts").isArray())
                .andExpect(jsonPath("$.data.progress.parts[0].partNumber").value(1))
                .andExpect(jsonPath("$.data.progress.parts[0].eTag").exists());
    }

    @Test
    @DisplayName("应该能中止正在上传的任务")
    void shouldAbortUploadTask() throws Exception {
        // Given: 创建任务并上传部分分片
        byte[] part1 = "Part 1 data".getBytes();
        String taskId = createTask("to-be-aborted.dat", "application/octet-stream", 10000);
        uploadPart(taskId, 1, part1);

        // When: 中止任务
        mockMvc.perform(post("/api/v1/files/upload_task/{taskId}/abort", taskId)
                        .param("reason", "User cancelled upload"))
                .andExpect(status().isNoContent());

        // Then: 验证任务状态为 ABORTED
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ABORTED"))
                .andExpect(jsonPath("$.data.summary.taskId").value(taskId));
    }

    @Test
    @DisplayName("应该拒绝对不存在的任务上传分片")
    @SuppressWarnings("null")
    void shouldRejectUploadPartForNonexistentTask() throws Exception {
        String nonexistentTaskId = "nonexistent-task-id";
        byte[] partData = "Some data".getBytes();

        mockMvc.perform(put("/api/v1/files/upload_task/{taskId}", nonexistentTaskId)
                        .param("partNumber", "1")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(partData))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.TASK_NOT_FOUND))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("应该拒绝对不存在的任务完成上传")
    void shouldRejectCompleteForNonexistentTask() throws Exception {
        String nonexistentTaskId = "nonexistent-task-id";

        mockMvc.perform(post("/api/v1/files/upload_task/{taskId}/complete", nonexistentTaskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                    {"partNumber": 1, "eTag": "etag1"}
                                ]
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.TASK_NOT_FOUND))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("应该拒绝对不存在的任务查询状态")
    void shouldReturn404ForNonexistentTask() throws Exception {
        String nonexistentTaskId = "nonexistent-task-id";

        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", nonexistentTaskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.TASK_NOT_FOUND))
                .andExpect(jsonPath("$.message").exists());
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("应该拒绝分片序号超出范围")
    void shouldRejectInvalidPartNumber() throws Exception {
        String taskId = createTask("test.dat", "application/octet-stream", 1000);
        byte[] partData = "Some data".getBytes();

        // 分片序号为 0（最小为 1）
        mockMvc.perform(put("/api/v1/files/upload_task/{taskId}", taskId)
                        .param("partNumber", "0")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(partData))
                .andExpect(status().isBadRequest());

        // 分片序号超过 10000
        mockMvc.perform(put("/api/v1/files/upload_task/{taskId}", taskId)
                        .param("partNumber", "10001")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(partData))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("应该支持重复上传相同分片（覆盖）")
    void shouldAllowOverwriteSamePart() throws Exception {
        // Given: 创建任务
        String taskId = createTask("overwrite-test.dat", "application/octet-stream", 1000);

        // 首次上传分片 1
        byte[] part1v1 = "Version 1 of part 1".getBytes();
        PartETagInfo firstUpload = uploadPart(taskId, 1, part1v1);

        // 再次上传分片 1（覆盖）
        byte[] part1v2 = "Version 2 of part 1 with different content".getBytes();
        PartETagInfo secondUpload = uploadPart(taskId, 1, part1v2);

        // ETag 应该不同
        assertThat(secondUpload.eTag()).isNotEqualTo(firstUpload.eTag());

        // 查询任务应该只有一个分片记录
        mockMvc.perform(get("/api/v1/files/upload_task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.uploadedParts").value(1))
                .andExpect(jsonPath("$.data.progress.parts").isArray())
                .andExpect(jsonPath("$.data.progress.parts[0].eTag").value(secondUpload.eTag()));
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建上传任务
     *
     * @param filename    文件名
     * @param contentType MIME 类型
     * @param fileSize    文件大小
     * @return 任务 ID
     */
    @SuppressWarnings("null")
    private String createTask(String filename, String contentType, long fileSize) throws Exception {
        String requestBody = String.format("""
                {
                    "filename": "%s",
                    "contentType": "%s",
                    "size": %d,
                    "createdBy": "test-user",
                    "creatorName": "Integration Tester"
                }
                """, filename, contentType, fileSize);

        String response = mockMvc.perform(post("/api/v1/files/upload_task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.data.taskId");
    }

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
}
