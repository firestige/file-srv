package tech.icc.filesrv.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 文件服务配置属性
 */
@Data
@ConfigurationProperties(prefix = "file-service")
public class FileServiceProperties {

    private TaskProperties task = new TaskProperties();
    private KafkaProperties kafka = new KafkaProperties();
    private CacheProperties cache = new CacheProperties();
    private BloomFilterProperties bloomFilter = new BloomFilterProperties();
    private FileControllerProperties fileController = new FileControllerProperties();

    /**
     * 任务相关配置
     */
    @Data
    public static class TaskProperties {
        
        /**
         * 本地临时文件目录
         */
        private String tempDir = System.getProperty("java.io.tmpdir") + "/file-srv-tasks";
        
        /**
         * 临时文件保留时间
         */
        private Duration tempFileTtl = Duration.ofHours(1);
        
        /**
         * 任务超时时间
         */
        private Duration expireAfter = Duration.ofHours(24);
    }

    /**
     * Kafka 相关配置
     */
    @Data
    public static class KafkaProperties {
        
        private TopicProperties topic = new TopicProperties();

        @Data
        public static class TopicProperties {
            
            /**
             * 任务完成事件 Topic
             */
            private String taskCompleted = "file-task-completed";
            
            /**
             * 任务失败事件 Topic
             */
            private String taskFailed = "file-task-failed";
        }
    }

    /**
     * 缓存相关配置
     */
    @Data
    public static class CacheProperties {

        /**
         * 最大缓存条目数
         */
        private int maxSize = 10000;

        /**
         * 缓存过期时间（秒）
         */
        private int expireSeconds = 30;
    }

    /**
     * 布隆过滤器配置
     */
    @Data
    public static class BloomFilterProperties {

        /**
         * 是否使用 Redis 布隆过滤器（多节点部署推荐）
         * <p>
         * true:  使用 Redis 实现（全局共享，适合分布式）
         * false: 使用本地内存实现（单节点场景）
         */
        private boolean useRedis = true;

        /**
         * 预期插入数量
         */
        private int expectedInsertions = 1000000;

        /**
         * 误判率 (False Positive Probability)
         */
        private double fpp = 0.01;
    }

    /**
     * 文件控制器相关配置
     */
    @Data
    public static class FileControllerProperties {

        /**
         * 文件标识最大长度
         */
        private int maxFileKeyLength = 128;

        /**
         * 预签名URL配置
         */
        private PresignProperties presign = new PresignProperties();

        /**
         * 分页配置
         */
        private PaginationProperties pagination = new PaginationProperties();
    }

    /**
     * 预签名URL配置
     */
    @Data
    public static class PresignProperties {

        /**
         * 默认有效期（秒）
         */
        private int defaultExpirySeconds = 3600;

        /**
         * 最小有效期（秒）
         */
        private int minExpirySeconds = 60;

        /**
         * 最大有效期（秒），7天
         */
        private int maxExpirySeconds = 604800;
    }

    /**
     * 分页配置
     */
    @Data
    public static class PaginationProperties {

        /**
         * 默认每页大小
         */
        private int defaultSize = 20;

        /**
         * 最大每页大小
         */
        private int maxSize = 100;
    }
}
