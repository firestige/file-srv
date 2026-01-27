package tech.icc.filesrv.aspect;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * 切面模块自动配置
 * <p>
 * 在 Web 应用环境下自动启用请求日志切面。
 */
@AutoConfiguration
@ConditionalOnWebApplication
@Import(RequestLoggingAspect.class)
public class AspectAutoConfiguration {
}
