package tech.icc.filesrv.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * TaskContext 日志切面
 * <p>
 * 自动记录 TaskContext 注入和修改日志，并将 taskId、fKey 传播到 MDC，
 * 便于日志关联和 ELK 检索。
 * </p>
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>拦截 TaskService 的关键方法，自动记录 Context 操作</li>
 *   <li>MDC 传播：taskId、fKey 自动添加到所有日志</li>
 *   <li>Context 变更追踪：记录前后差异</li>
 *   <li>异常安全：确保 MDC 清理</li>
 * </ul>
 *
 * <h3>日志示例</h3>
 * <pre>
 * [taskId=abc123, fKey=file_xyz] TaskContext injected: keys=[task.id, file.fkey, file.name]
 * [taskId=abc123, fKey=file_xyz] TaskContext modified: added=[delivery.thumb_123.path], removed=[]
 * </pre>
 */
@Aspect
@Component
public class TaskContextLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(TaskContextLoggingAspect.class);

    // MDC 键名常量
    private static final String MDC_TASK_ID = "taskId";
    private static final String MDC_FILE_KEY = "fKey";

    /**
     * 拦截 TaskService 的所有公共方法
     * <p>
     * 执行前：注入 MDC（taskId, fKey）<br>
     * 执行后：记录 Context 变更，清理 MDC
     * </p>
     */
    @Around("execution(public * tech.icc.filesrv.core.application.service.TaskService.*(..))")
    public Object aroundTaskServiceMethod(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String methodName = signature.getName();
        Object[] args = pjp.getArgs();

        // 1. 提取 taskId 和 fKey 注入 MDC
        String taskId = extractTaskId(args);
        String fKey = extractFKey(args);
        
        boolean mdcInjected = false;
        try {
            if (taskId != null) {
                MDC.put(MDC_TASK_ID, taskId);
                mdcInjected = true;
            }
            if (fKey != null) {
                MDC.put(MDC_FILE_KEY, fKey);
                mdcInjected = true;
            }

            if (mdcInjected) {
                log.debug("MDC injected for method {}: taskId={}, fKey={}", methodName, taskId, fKey);
            }

            // 2. 记录 Context 快照（如果方法操作 Task）
            TaskAggregate taskBefore = extractTaskAggregate(args);
            Map<String, Object> contextBefore = null;
            if (taskBefore != null && taskBefore.getContext() != null) {
                contextBefore = new HashMap<>(taskBefore.getContext().toMap());
            }

            // 3. 执行目标方法
            Object result = pjp.proceed();

            // 4. 记录 Context 变更
            if (contextBefore != null) {
                TaskAggregate taskAfter = extractTaskAggregateFromResult(result, taskBefore);
                if (taskAfter != null && taskAfter.getContext() != null) {
                    logContextChanges(methodName, contextBefore, taskAfter.getContext().toMap());
                }
            }

            return result;

        } finally {
            // 5. 清理 MDC（防止线程池复用导致上下文污染）
            if (mdcInjected) {
                MDC.remove(MDC_TASK_ID);
                MDC.remove(MDC_FILE_KEY);
            }
        }
    }

    /**
     * 从方法参数中提取 taskId
     */
    private String extractTaskId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String && ((String) arg).length() == 36) {
                // 简单启发式：36 字符的字符串可能是 UUID (taskId)
                return (String) arg;
            }
            if (arg instanceof TaskAggregate) {
                return ((TaskAggregate) arg).getTaskId();
            }
        }
        return null;
    }

    /**
     * 从方法参数中提取 fKey
     */
    private String extractFKey(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof TaskAggregate) {
                return ((TaskAggregate) arg).getFKey();
            }
        }
        return null;
    }

    /**
     * 从方法参数中提取 TaskAggregate
     */
    private TaskAggregate extractTaskAggregate(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof TaskAggregate) {
                return (TaskAggregate) arg;
            }
        }
        return null;
    }

    /**
     * 从方法返回值中提取 TaskAggregate
     * <p>
     * 注意：部分方法返回 TaskInfoDto，需要重新查询 Task（暂不实现，后续优化）
     * </p>
     */
    private TaskAggregate extractTaskAggregateFromResult(Object result, TaskAggregate fallback) {
        if (result instanceof TaskAggregate) {
            return (TaskAggregate) result;
        }
        // 其他情况返回方法执行前的 Task（假设未修改）
        return fallback;
    }

    /**
     * 记录 Context 变更日志
     * <p>
     * 比较前后快照，记录新增、删除、修改的键
     * </p>
     */
    private void logContextChanges(String methodName, Map<String, Object> before, Map<String, Object> after) {
        Set<String> added = new java.util.HashSet<>(after.keySet());
        added.removeAll(before.keySet());

        Set<String> removed = new java.util.HashSet<>(before.keySet());
        removed.removeAll(after.keySet());

        Set<String> modified = new java.util.HashSet<>();
        for (String key : before.keySet()) {
            if (after.containsKey(key) && !before.get(key).equals(after.get(key))) {
                modified.add(key);
            }
        }

        if (!added.isEmpty() || !removed.isEmpty() || !modified.isEmpty()) {
            log.info("TaskContext modified in {}: added={}, removed={}, modified={}", 
                    methodName, added, removed, modified);
        }
    }
}
