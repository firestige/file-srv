package tech.icc.filesrv.core.application.entrypoint.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tech.icc.filesrv.core.domain.files.FileReferenceRepository;

/**
 * UniqueFKey 注解验证器
 * <p>
 * 检查 fKey 是否已存在于数据库中。
 * 空值或空白字符串会通过验证（由其他注解如 @NotBlank 负责非空验证）。
 */
@Component
@RequiredArgsConstructor
public class UniqueFKeyValidator implements ConstraintValidator<UniqueFKey, String> {

    private final FileReferenceRepository fileReferenceRepository;

    @Override
    public void initialize(UniqueFKey constraintAnnotation) {
        // 无需初始化逻辑
    }

    @Override
    public boolean isValid(String fKey, ConstraintValidatorContext context) {
        // 空值或空白字符串视为有效（交给其他注解处理）
        if (fKey == null || fKey.isBlank()) {
            return true;
        }
        
        // 检查唯一性
        boolean exists = fileReferenceRepository.existsByFKey(fKey.trim());
        
        if (exists) {
            // 自定义错误消息
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("fKey '%s' 已被使用", fKey.trim())
            ).addConstraintViolation();
        }
        
        return !exists;
    }
}
