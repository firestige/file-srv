package tech.icc.filesrv.core.application.entrypoint.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 验证 fKey 是否唯一
 * <p>
 * 用于 API 层请求参数验证，确保用户提供的 fKey 在数据库中不存在。
 * 
 * @see UniqueFKeyValidator
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueFKeyValidator.class)
@Documented
public @interface UniqueFKey {
    
    String message() default "fKey 已被使用";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
