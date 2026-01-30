package tech.icc.filesrv.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only Spring Boot application for integration testing.
 * <p>
 * This application is only used by tests in the file-srv-test module
 * for application-level integration testing. It:
 * - Loads full application context with all auto-configuration
 * - Provides real beans for integration testing
 * - Uses test-specific configuration (application-test.yml)
 * </p>
 *
 * <p>Note: This class is intentionally in src/test/java, not src/main/java</p>
 */
@SpringBootApplication
public class IntegrationTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationTestApplication.class, args);
    }
}
