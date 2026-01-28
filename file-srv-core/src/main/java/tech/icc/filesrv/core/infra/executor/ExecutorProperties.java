package tech.icc.filesrv.core.infra.executor;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Callback 执行器配置
 */
@ConfigurationProperties(prefix = "file-service.executor")
public record ExecutorProperties(
        /** Kafka 配置 */
        KafkaConfig kafka,

        /** 超时配置 */
        TimeoutConfig timeout,

        /** 重试配置 */
        RetryConfig retry,

        /** 幂等配置 */
        IdempotencyConfig idempotency
) {

    public ExecutorProperties {
        if (kafka == null) kafka = new KafkaConfig(null, null, null, 0);
        if (timeout == null) timeout = new TimeoutConfig(null, null, null);
        if (retry == null) retry = new RetryConfig(0, null, 0, null);
        if (idempotency == null) idempotency = new IdempotencyConfig(null);
    }

    /**
     * Kafka 配置
     */
    public record KafkaConfig(
            /** 任务 topic */
            String topic,
            /** 死信 topic */
            String dltTopic,
            /** 消费者组 */
            String consumerGroup,
            /** 每节点并发数 */
            int concurrency
    ) {
        public KafkaConfig {
            if (topic == null || topic.isBlank()) topic = "file-callback-tasks";
            if (dltTopic == null || dltTopic.isBlank()) dltTopic = "file-callback-tasks-dlt";
            if (consumerGroup == null || consumerGroup.isBlank()) consumerGroup = "file-callback-executor";
            if (concurrency <= 0) concurrency = 4;
        }
    }

    /**
     * 超时配置
     */
    public record TimeoutConfig(
            /** 单个 callback 超时 */
            Duration callback,
            /** 整个链超时（预留） */
            Duration chain,
            /** 任务最大等待时间 */
            Duration taskDeadline
    ) {
        public TimeoutConfig {
            if (callback == null) callback = Duration.ofMinutes(5);
            if (chain == null) chain = Duration.ofMinutes(30);
            if (taskDeadline == null) taskDeadline = Duration.ofHours(1);
        }
    }

    /**
     * 重试配置（本地重试）
     */
    public record RetryConfig(
            /** 单个 callback 最大本地重试次数 */
            int maxRetriesPerCallback,
            /** 首次重试退避时间 */
            Duration backoff,
            /** 退避乘数 */
            double backoffMultiplier,
            /** 最大退避时间 */
            Duration maxBackoff
    ) {
        public RetryConfig {
            if (maxRetriesPerCallback <= 0) maxRetriesPerCallback = 3;
            if (backoff == null) backoff = Duration.ofSeconds(1);
            if (backoffMultiplier <= 0) backoffMultiplier = 2.0;
            if (maxBackoff == null) maxBackoff = Duration.ofMinutes(1);
        }

        /**
         * 计算指定重试次数的退避时间
         *
         * @param attemptNumber 当前尝试次数（从 0 开始）
         * @return 退避时间
         */
        public Duration getBackoff(int attemptNumber) {
            if (attemptNumber <= 0) {
                return Duration.ZERO;
            }
            long delay = (long) (backoff.toMillis() * Math.pow(backoffMultiplier, attemptNumber - 1));
            return Duration.ofMillis(Math.min(delay, maxBackoff.toMillis()));
        }
    }

    /**
     * 幂等配置
     */
    public record IdempotencyConfig(
            /** 幂等 key 过期时间 */
            Duration ttl
    ) {
        public IdempotencyConfig {
            if (ttl == null) ttl = Duration.ofHours(24);
        }
    }
}
