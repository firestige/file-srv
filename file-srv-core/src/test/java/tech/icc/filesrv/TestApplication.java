package tech.icc.filesrv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 测试专用 Spring Boot 配置
 * <p>
 * 配置说明：
 * - 扫描 core 模块的所有组件（Service, Repository 实现类等）
 * - 启用 JPA Repository
 * - 扫描 JPA Entity
 * - 启用异步支持（用于回调链异步执行）
 */
@SpringBootApplication(scanBasePackages = "tech.icc.filesrv.core")
@EnableJpaRepositories(basePackages = "tech.icc.filesrv.core.infra.persistence.repository")
@EntityScan(basePackages = "tech.icc.filesrv.core.infra.persistence.entity")
@EnableAsync
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}

