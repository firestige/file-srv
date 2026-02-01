package tech.icc.filesrv.core.infra.task;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.icc.filesrv.core.infra.config.OrphanCleanupProperties;
import tech.icc.filesrv.core.infra.persistence.repository.FileRelationRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 孤儿文件清理定时任务
 * <p>
 * 定期扫描并清理孤儿文件（关联的主文件不存在的文件）。
 * 通过 file-srv.orphan.enabled 配置启用。
 * </p>
 * 
 * <h3>清理策略</h3>
 * <ul>
 *   <li>查询 file_relations 表，找到关联文件不存在的记录</li>
 *   <li>只处理超过宽限期（默认7天）的记录</li>
 *   <li>分批处理，避免一次加载过多数据</li>
 *   <li>记录详细日志用于审计</li>
 *   <li>发布监控指标到 Micrometer</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "file-srv.orphan", name = "enabled", havingValue = "true")
public class OrphanFileCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(OrphanFileCleanupTask.class);

    private final OrphanCleanupProperties properties;
    private final FileRelationRepository relationRepository;
    
    // 监控指标
    private final Counter orphansFoundCounter;
    private final Counter orphansDeletedCounter;
    private final Counter cleanupFailuresCounter;
    private final AtomicLong lastCleanupTimestamp;
    private final AtomicLong lastOrphansCount;

    public OrphanFileCleanupTask(
            OrphanCleanupProperties properties,
            FileRelationRepository relationRepository,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.relationRepository = relationRepository;
        
        // 初始化监控指标
        this.orphansFoundCounter = Counter.builder("file_srv.orphan.found")
                .description("Number of orphan files found")
                .register(meterRegistry);
        
        this.orphansDeletedCounter = Counter.builder("file_srv.orphan.deleted")
                .description("Number of orphan files deleted")
                .register(meterRegistry);
        
        this.cleanupFailuresCounter = Counter.builder("file_srv.orphan.cleanup_failures")
                .description("Number of cleanup failures")
                .register(meterRegistry);
        
        this.lastCleanupTimestamp = new AtomicLong(0);
        this.lastOrphansCount = new AtomicLong(0);
        
        // 注册 Gauge 指标
        Gauge.builder("file_srv.orphan.last_cleanup_timestamp", lastCleanupTimestamp, AtomicLong::get)
                .description("Timestamp of last cleanup execution")
                .register(meterRegistry);
        
        Gauge.builder("file_srv.orphan.last_count", lastOrphansCount, AtomicLong::get)
                .description("Number of orphans found in last cleanup")
                .register(meterRegistry);
    }

    /**
     * 执行孤儿文件清理
     * <p>
     * 使用配置的 cron 表达式定时触发
     * </p>
     */
    @Scheduled(cron = "${file-srv.orphan.cleanup-cron}")
    public void cleanupOrphanFiles() {
        log.info("Starting orphan file cleanup task (retention-days={}, batch-size={}, dry-run={})",
                properties.getRetentionDays(), properties.getBatchSize(), properties.isDryRun());
        
        long startTime = System.currentTimeMillis();
        int totalOrphans = 0;
        int totalDeleted = 0;
        int totalFailed = 0;

        try {
            // 计算宽限期开始时间
            Instant gracePeriodStart = Instant.now()
                    .minus(properties.getRetentionDays(), ChronoUnit.DAYS);
            
            log.debug("Searching for orphan files created before: {}", gracePeriodStart);
            
            // 查询孤儿文件
            List<String> orphanFkeys = relationRepository.findOrphanFiles(gracePeriodStart);
            totalOrphans = orphanFkeys.size();
            
            if (orphanFkeys.isEmpty()) {
                log.info("No orphan files found");
                lastCleanupTimestamp.set(Instant.now().toEpochMilli());
                lastOrphansCount.set(0);
                return;
            }
            
            log.info("Found {} orphan files", totalOrphans);
            orphansFoundCounter.increment(totalOrphans);
            
            // 分批处理孤儿文件
            int batchSize = properties.getBatchSize();
            for (int i = 0; i < orphanFkeys.size(); i += batchSize) {
                int end = Math.min(i + batchSize, orphanFkeys.size());
                List<String> batch = orphanFkeys.subList(i, end);
                
                log.debug("Processing batch {}-{} of {}", i, end, totalOrphans);
                
                for (String fkey : batch) {
                    try {
                        if (properties.isDryRun()) {
                            log.info("[DRY-RUN] Would delete orphan file: {}", fkey);
                            totalDeleted++;
                        } else {
                            deleteOrphanFile(fkey);
                            totalDeleted++;
                            orphansDeletedCounter.increment();
                        }
                    } catch (Exception e) {
                        log.error("Failed to delete orphan file: fkey={}, error={}", fkey, e.getMessage(), e);
                        totalFailed++;
                        cleanupFailuresCounter.increment();
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Orphan file cleanup task failed", e);
            cleanupFailuresCounter.increment();
            totalFailed++;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            lastCleanupTimestamp.set(Instant.now().toEpochMilli());
            lastOrphansCount.set(totalOrphans);
            
            log.info("Orphan file cleanup completed: found={}, deleted={}, failed={}, duration={}ms",
                    totalOrphans, totalDeleted, totalFailed, duration);
        }
    }

    /**
     * 删除孤儿文件
     * <p>
     * TODO: 集成 File 域的删除服务，删除物理存储和元数据
     * 目前仅记录日志
     * </p>
     *
     * @param fkey 文件 key
     */
    private void deleteOrphanFile(String fkey) {
        // TODO: 调用 FileService.delete(fkey)
        // 1. 删除物理文件（通过 StorageAdapter）
        // 2. 删除 file_metadata 记录
        // 3. 删除 file_relations 记录
        // 4. 发布 FileDeletedEvent
        
        log.info("Deleting orphan file: fkey={}", fkey);
        
        // 临时实现：仅删除关联关系记录
        try {
            relationRepository.deleteByFileFkey(fkey);
            relationRepository.deleteByRelatedFkey(fkey);
            log.debug("Deleted file relations for orphan: fkey={}", fkey);
        } catch (Exception e) {
            log.error("Failed to delete file relations: fkey={}", fkey, e);
            throw e;
        }
    }
}
