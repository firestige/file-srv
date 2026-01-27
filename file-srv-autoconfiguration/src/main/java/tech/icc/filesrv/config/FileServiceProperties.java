package tech.icc.filesrv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 文件服务配置属性
 */
@ConfigurationProperties(prefix = "file-service")
public class FileServiceProperties {

    private TaskProperties task = new TaskProperties();
    private KafkaProperties kafka = new KafkaProperties();
    private CacheProperties cache = new CacheProperties();
    private BloomFilterProperties bloomFilter = new BloomFilterProperties();

    public TaskProperties getTask() {
        return task;
    }

    public void setTask(TaskProperties task) {
        this.task = task;
    }

    public KafkaProperties getKafka() {
        return kafka;
    }

    public void setKafka(KafkaProperties kafka) {
        this.kafka = kafka;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public void setCache(CacheProperties cache) {
        this.cache = cache;
    }

    public BloomFilterProperties getBloomFilter() {
        return bloomFilter;
    }

    public void setBloomFilter(BloomFilterProperties bloomFilter) {
        this.bloomFilter = bloomFilter;
    }

    /**
     * 任务相关配置
     */
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

        public String getTempDir() {
            return tempDir;
        }

        public void setTempDir(String tempDir) {
            this.tempDir = tempDir;
        }

        public Duration getTempFileTtl() {
            return tempFileTtl;
        }

        public void setTempFileTtl(Duration tempFileTtl) {
            this.tempFileTtl = tempFileTtl;
        }

        public Duration getExpireAfter() {
            return expireAfter;
        }

        public void setExpireAfter(Duration expireAfter) {
            this.expireAfter = expireAfter;
        }
    }

    /**
     * Kafka 相关配置
     */
    public static class KafkaProperties {
        
        private TopicProperties topic = new TopicProperties();

        public TopicProperties getTopic() {
            return topic;
        }

        public void setTopic(TopicProperties topic) {
            this.topic = topic;
        }

        public static class TopicProperties {
            
            /**
             * 任务完成事件 Topic
             */
            private String taskCompleted = "file-task-completed";
            
            /**
             * 任务失败事件 Topic
             */
            private String taskFailed = "file-task-failed";

            public String getTaskCompleted() {
                return taskCompleted;
            }

            public void setTaskCompleted(String taskCompleted) {
                this.taskCompleted = taskCompleted;
            }

            public String getTaskFailed() {
                return taskFailed;
            }

            public void setTaskFailed(String taskFailed) {
                this.taskFailed = taskFailed;
            }
        }
    }

    /**
     * 缓存相关配置
     */
    public static class CacheProperties {

        /**
         * 最大缓存条目数
         */
        private int maxSize = 10000;

        /**
         * 缓存过期时间（秒）
         */
        private int expireSeconds = 30;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getExpireSeconds() {
            return expireSeconds;
        }

        public void setExpireSeconds(int expireSeconds) {
            this.expireSeconds = expireSeconds;
        }
    }

    /**
     * 布隆过滤器配置
     */
    public static class BloomFilterProperties {

        /**
         * 预期插入数量
         */
        private int expectedInsertions = 1000000;

        /**
         * 误判率 (False Positive Probability)
         */
        private double fpp = 0.01;

        public int getExpectedInsertions() {
            return expectedInsertions;
        }

        public void setExpectedInsertions(int expectedInsertions) {
            this.expectedInsertions = expectedInsertions;
        }

        public double getFpp() {
            return fpp;
        }

        public void setFpp(double fpp) {
            this.fpp = fpp;
        }
    }
}
