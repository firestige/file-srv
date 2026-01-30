package tech.icc.filesrv.core;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for module-internal integration tests.
 * <p>
 * Integration tests should:
 * - Test multiple components working together
 * - Use real database (Testcontainers PostgreSQL)
 * - Use @Transactional for automatic rollback
 * - Mock external services (storage adapters, callbacks)
 * </p>
 *
 * <p>Coverage target: core module >= 80%</p>
 *
 * <p>Example:</p>
 * <pre>
 * {@code
 * @SpringBootTest
 * class FileServiceIntegrationTest extends BaseIntegrationTest {
 *     @Autowired
 *     private FileService fileService;
 *
 *     @MockBean
 *     private StorageAdapter storageAdapter;
 *
 *     @Test
 *     void shouldCreateFileWithMetadata() {
 *         // Given
 *         when(storageAdapter.upload(any())).thenReturn(uploadResult);
 *         var request = FileUploadRequest.builder()...build();
 *
 *         // When
 *         var response = fileService.upload(request);
 *
 *         // Then
 *         assertNotNull(response.fkey());
 *         verify(storageAdapter).upload(any());
 *     }
 * }
 * }
 * </pre>
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@Transactional
public abstract class BaseIntegrationTest {

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
