package tech.icc.filesrv.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 请求日志切面
 * <p>
 * 自动记录 Controller 层所有请求的入口日志，包括：
 * <ul>
 *   <li>请求方法和路径</li>
 *   <li>方法签名和参数（敏感信息脱敏）</li>
 *   <li>响应状态码</li>
 *   <li>处理耗时</li>
 * </ul>
 *
 * <p>日志级别说明：
 * <ul>
 *   <li>DEBUG - 请求入口、参数详情</li>
 *   <li>INFO - 请求完成、耗时统计</li>
 *   <li>WARN - 慢请求（超过阈值）</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class RequestLoggingAspect {

    /** 慢请求阈值（毫秒） */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 3000;

    /** 参数值最大显示长度 */
    private static final int MAX_PARAM_LENGTH = 200;

    /**
     * 切入点：所有 @RestController 注解类的 public 方法
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerMethods() {}

    /**
     * 环绕通知：记录请求入口和出口日志
     */
    @Around("restControllerMethods()")
    public Object logRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取请求信息
        HttpServletRequest request = getCurrentRequest();
        String httpMethod = request != null ? request.getMethod() : "UNKNOWN";
        String requestUri = request != null ? request.getRequestURI() : "UNKNOWN";
        String queryString = request != null ? request.getQueryString() : null;
        String clientIp = request != null ? getClientIp(request) : "UNKNOWN";

        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // 记录请求入口日志
        if (log.isDebugEnabled()) {
            String params = formatArguments(signature.getParameterNames(), joinPoint.getArgs());
            log.debug(">>> [{}] {} {} | client={} | {}.{}({})",
                    httpMethod, requestUri, queryString != null ? "?" + queryString : "",
                    clientIp, className, methodName, params);
        }

        try {
            // 执行目标方法
            Object result = joinPoint.proceed();

            // 计算耗时
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = extractStatusCode(result);

            // 记录请求完成日志
            if (duration > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("<<< [{}] {} | status={} | duration={}ms [SLOW]",
                        httpMethod, requestUri, statusCode, duration);
            } else if (log.isInfoEnabled()) {
                log.info("<<< [{}] {} | status={} | duration={}ms",
                        httpMethod, requestUri, statusCode, duration);
            }

            return result;

        } catch (Throwable e) {
            // 记录异常日志
            long duration = System.currentTimeMillis() - startTime;
            log.error("<<< [{}] {} | exception={} | duration={}ms",
                    httpMethod, requestUri, e.getClass().getSimpleName(), duration);
            throw e;
        }
    }

    /**
     * 获取当前 HTTP 请求
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取客户端真实 IP
     * <p>
     * 支持代理环境，优先从 X-Forwarded-For 等头部获取。
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能包含多个 IP，取第一个
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * 格式化方法参数
     * <p>
     * 对敏感参数进行脱敏处理，避免日志泄露。
     */
    private String formatArguments(String[] paramNames, Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }

        return IntStream.range(0, args.length)
                .mapToObj(i -> {
                    String name = paramNames != null && i < paramNames.length ? paramNames[i] : "arg" + i;
                    String value = formatArgumentValue(args[i]);
                    return name + "=" + value;
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * 格式化单个参数值
     */
    private String formatArgumentValue(Object arg) {
        if (arg == null) {
            return "null";
        }

        // 文件类型：只显示文件名和大小
        if (arg instanceof MultipartFile file) {
            return String.format("File(%s, %d bytes)",
                    file.getOriginalFilename(), file.getSize());
        }

        // 数组类型
        if (arg.getClass().isArray()) {
            return Arrays.toString((Object[]) arg);
        }

        // 其他类型：截断过长的值
        String value = arg.toString();
        if (value.length() > MAX_PARAM_LENGTH) {
            return value.substring(0, MAX_PARAM_LENGTH) + "...(truncated)";
        }

        return value;
    }

    /**
     * 从响应结果中提取 HTTP 状态码
     */
    private int extractStatusCode(Object result) {
        if (result instanceof ResponseEntity<?> response) {
            return response.getStatusCode().value();
        }
        return 200; // 默认成功
    }
}
