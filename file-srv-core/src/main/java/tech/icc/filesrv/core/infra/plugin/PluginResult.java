package tech.icc.filesrv.core.infra.plugin;

import java.util.Map;

/**
 * 插件执行结果
 */
public sealed interface PluginResult {

    /**
     * 执行成功
     *
     * @param outputs 输出数据，会合并到 TaskContext
     */
    record Success(Map<String, Object> outputs) implements PluginResult {
        public Success {
            outputs = outputs != null ? Map.copyOf(outputs) : Map.of();
        }

        public static Success empty() {
            return new Success(Map.of());
        }

        public static Success of(String key, Object value) {
            return new Success(Map.of(key, value));
        }
    }

    /**
     * 执行失败
     *
     * @param reason    失败原因
     * @param retryable 是否可重试
     */
    record Failure(String reason, boolean retryable) implements PluginResult {
        public static Failure of(String reason) {
            return new Failure(reason, false);
        }

        public static Failure retryable(String reason) {
            return new Failure(reason, true);
        }
    }

    /**
     * 跳过执行（不算失败）
     *
     * @param reason 跳过原因
     */
    record Skip(String reason) implements PluginResult {
        public static Skip of(String reason) {
            return new Skip(reason);
        }
    }

    /**
     * 判断是否成功
     */
    default boolean isSuccess() {
        return this instanceof Success;
    }

    /**
     * 判断是否失败
     */
    default boolean isFailure() {
        return this instanceof Failure;
    }
}
