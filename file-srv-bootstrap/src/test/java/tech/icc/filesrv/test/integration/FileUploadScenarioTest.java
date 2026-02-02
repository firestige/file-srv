package tech.icc.filesrv.test.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tech.icc.filesrv.common.constants.ResultCode;
import tech.icc.filesrv.core.application.service.FileService;
import tech.icc.filesrv.test.config.TestStorageConfig;
import tech.icc.filesrv.test.support.stub.ObjectStorageServiceStub;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件上传核心场景集成测试
 * <p>
 * 测试策略：
 * <ul>
 *   <li>通过 MockMvc 发起真实 HTTP 请求</li>
 *   <li>使用 @SpyBean 监控 Service 调用链路</li>
 *   <li>验证完整的请求-响应流程</li>
 *   <li>验证数据库持久化和存储层交互</li>
 * </ul>
 * <p>
 * Jacoco 覆盖率说明：
 * <ul>
 *   <li>@SpyBean 使用真实对象，不影响覆盖率统计</li>
 *   <li>仅在真实方法执行后添加验证，不改变执行路径</li>
 *   <li>覆盖范围：Controller → Service → Repository → Storage</li>
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
@DisplayName("文件上传核心场景集成测试")
class FileUploadScenarioTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    /**
     * SpyBean：监控 Service 调用，不影响真实执行
     * <p>
     * 与 @MockBean 的区别：
     * - @MockBean：完全替换为 Mock 对象，需要预设所有行为
     * - @SpyBean：包装真实对象，真实执行后可验证调用
     * <p>
     * Jacoco 影响：✅ 不影响覆盖率
     * - SpyBean 使用 CGLIB 代理包装真实对象
     * - 真实方法仍然执行，Jacoco 正常统计
     * - 仅在方法执行前后添加拦截器
     */
    @SpyBean
    private FileService fileService;
    
    @Autowired
    private ObjectStorageServiceStub storageAdapter;

    @Test
    @DisplayName("应该能在收到不存在的fkey时返回404")
    void shouldReturn404ForNonexistentFKey() throws Exception {
        String nonexistentFKey = "nonexistentFKey";
        mockMvc.perform(get("/api/v1/files/{fkey}", nonexistentFKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.FILE_NOT_FOUND))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/api/v1/static/{fkey}", nonexistentFKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.FILE_NOT_FOUND))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(delete("/api/v1/files/{fkey}", nonexistentFKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.FILE_NOT_FOUND))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/api/v1/files/{fkey}/metadata", nonexistentFKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.FILE_NOT_FOUND))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/api/v1/files/{fkey}/presign", nonexistentFKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.FILE_NOT_FOUND))
                .andExpect(jsonPath("$.message").exists());
    }
    
    @Test
    @DisplayName("应该成功上传文件并返回完整信息")
    void shouldUploadFileAndReturnInfo() throws Exception {
        // Given: 准备上传文件
        byte[] content = "Hello, Integration Test!".getBytes();
        MockMultipartFile file = new MockMultipartFile(
            "file",                          // 参数名（与 Controller @RequestParam 对应）
            "test-file.txt",                 // 原始文件名
            "text/plain",                    // Content-Type
            content
        );
        
        // When: 发起 HTTP multipart 请求
        mockMvc.perform(multipart("/api/v1/files/upload")
                .file(file)
                .param("fileName", "integration-test.txt")
                .param("fileType", "text/plain")
                .param("public", "true")
                .param("createdBy", "test-user-001")
                .param("creatorName", "Integration Tester"))
                
                // Then: 验证 HTTP 响应
                .andExpect(status().isCreated())                                    // 201 Created
                .andExpect(jsonPath("$.code").value(0))                             // 业务成功码
                .andExpect(jsonPath("$.data.fkey").exists())                        // 返回文件标识
                .andExpect(jsonPath("$.data.fileName").value("integration-test.txt"))
                .andExpect(jsonPath("$.data.fileSize").value(content.length))
                .andExpect(jsonPath("$.data.fileType").value("text/plain"))
                .andExpect(jsonPath("$.data.public").value(true))
                .andExpect(jsonPath("$.data.createdBy").value("test-user-001"))
                .andExpect(jsonPath("$.data.creatorName").value("Integration Tester"));
        
        // Then: 验证存储层交互
        // 注意：由于 storageAdapter 是 stub，我们可以直接验证状态
        // 真实环境中 stub 内部计数器会记录上传次数
        assertThat(storageAdapter.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("应该能下载文件（FileController）")
    void shouldDownloadFile() throws Exception {
        String fkey = uploadAndGetFKey(false);

        mockMvc.perform(get("/api/v1/files/{fkey}", fkey))
                .andExpect(status().isOk())
            .andExpect(header().exists("Content-Type"))
            .andExpect(header().exists("Content-Disposition"))
            .andExpect(header().exists("x-file-key"));
    }

    @Test
    @DisplayName("应该能查询文件元数据（FileController）")
    void shouldGetFileMetadata() throws Exception {
        String fkey = uploadAndGetFKey(false);

        mockMvc.perform(get("/api/v1/files/{fkey}/metadata", fkey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fkey").value(fkey))
                .andExpect(jsonPath("$.data.fileName").value("integration-test.txt"))
                .andExpect(jsonPath("$.data.fileType").value("text/plain"))
                .andExpect(jsonPath("$.data.public").value(false))
                .andExpect(jsonPath("$.data.createdBy").value("test-user-001"))
                .andExpect(jsonPath("$.data.creatorName").value("Integration Tester"));
    }

    @Test
    @DisplayName("应该能获取预签名 URL（FileController）")
    void shouldGetPresignedUrl() throws Exception {
        String fkey = uploadAndGetFKey(false);

        mockMvc.perform(get("/api/v1/files/{fkey}/presign", fkey)
                .param("expiresIn", "3600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("应该能删除文件（FileController）")
    void shouldDeleteFile() throws Exception {
        String fkey = uploadAndGetFKey(false);

        mockMvc.perform(delete("/api/v1/files/{fkey}", fkey))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("应该能访问公开文件（StaticResourceController）")
    void shouldAccessPublicFile() throws Exception {
        String fkey = uploadAndGetFKey(true);

        mockMvc.perform(get("/api/v1/static/{fkey}", fkey))
                .andExpect(status().isOk())
            .andExpect(header().exists("Content-Type"))
            .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @DisplayName("应该拒绝访问非公开文件（StaticResourceController）")
    void shouldDenyAccessToNonPublicFile() throws Exception {
        String fkey = uploadAndGetFKey(false);

        mockMvc.perform(get("/api/v1/static/{fkey}", fkey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ResultCode.ACCESS_DENIED))
                .andExpect(jsonPath("$.message").exists());
    }

    private String uploadAndGetFKey(boolean isPublic) throws Exception {
        byte[] content = "Hello, Integration Test!".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-file.txt",
                "text/plain",
                content
        );

        String response = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                        .param("fileName", "integration-test.txt")
                        .param("fileType", "text/plain")
                        .param("public", String.valueOf(isPublic))
                        .param("createdBy", "test-user-001")
                        .param("creatorName", "Integration Tester"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.data.fkey");
    }
}

