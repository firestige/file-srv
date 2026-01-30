package tech.icc.filesrv.test;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for application-level integration tests.
 * <p>
 * These tests verify:
 * - Full HTTP request-response cycle
 * - All layers working together (Controller → Service → Repository)
 * - Complete business workflows
 * - Real database operations with Testcontainers
 * </p>
 *
 * <p>Test Strategy:</p>
 * <ul>
 *   <li>Use MockMvc for HTTP requests</li>
 *   <li>Mock external adapters (storage, callbacks)</li>
 *   <li>Use real database via Testcontainers</li>
 *   <li>Follow Given-When-Then pattern</li>
 *   <li>Each test is transactional and rolls back</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>
 * {@code
 * @SpringBootTest(classes = IntegrationTestApplication.class)
 * class FileUploadIntegrationTest extends ApplicationIntegrationTestBase {
 *
 *     @MockBean
 *     private StorageAdapter storageAdapter;
 *
 *     @Test
 *     void shouldUploadFileSuccessfully() throws Exception {
 *         // Given
 *         when(storageAdapter.upload(any())).thenReturn(uploadResult);
 *         String requestJson = """
 *             {
 *               "name": "test.txt",
 *               "size": 1024,
 *               "creator": "user@example.com"
 *             }
 *             """;
 *
 *         // When & Then
 *         mockMvc.perform(post("/api/v1/files/upload")
 *                 .contentType(MediaType.APPLICATION_JSON)
 *                 .content(requestJson))
 *             .andExpect(status().isOk())
 *             .andExpect(jsonPath("$.fkey").exists())
 *             .andExpect(jsonPath("$.name").value("test.txt"));
 *     }
 * }
 * }
 * </pre>
 */
@Tag("app-integration")
@Testcontainers
@SpringBootTest(
    classes = IntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@Transactional
public abstract class ApplicationIntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("filesrv_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
