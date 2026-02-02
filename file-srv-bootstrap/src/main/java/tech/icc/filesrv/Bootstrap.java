package tech.icc.filesrv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 用于集成测试的引导类，启动 Spring Boot 应用程序。
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "tech.icc.filesrv.core.infra.persistence.repository")
@EntityScan(basePackages = "tech.icc.filesrv.core.infra.persistence.entity")
@EnableAsync
public class Bootstrap {
    public static void main(String[] args) {
        SpringApplication.run(Bootstrap.class, args);
    }
}
