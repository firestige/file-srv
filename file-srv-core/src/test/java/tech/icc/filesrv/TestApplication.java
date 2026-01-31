package tech.icc.filesrv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 测试专用 Spring Boot 配置
 * <p>
 * 配置说明：
 * - 扫描 core 模块的所有组件（Service, Repository 实现类等）
 * - 启用 JPA Repository
 * - 扫描 JPA Entity
 */
@SpringBootApplication(scanBasePackages = "tech.icc.filesrv.core")
@EnableJpaRepositories(basePackages = "tech.icc.filesrv.core.infra.persistence.repository")
@EntityScan(basePackages = "tech.icc.filesrv.core.infra.persistence.entity")
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}

