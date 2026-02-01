package tech.icc.filesrv.core.infra.event.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.icc.filesrv.core.domain.events.DerivedFilesAddedEvent;
import tech.icc.filesrv.core.domain.events.TaskCompletedEvent;
import tech.icc.filesrv.core.domain.events.TaskFailedEvent;
import tech.icc.filesrv.core.infra.event.TaskEventPublisher;

/**
 * 日志事件发布器（Kafka 不可用时的降级实现）
 * <p>
 * 将事件记录到日志，便于调试和审计。
 */
public class LoggingTaskEventPublisher implements TaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingTaskEventPublisher.class);

    @Override
    public void publishCompleted(TaskCompletedEvent event) {
        log.info("Task completed event: taskId={}, fKey={}, storagePath={}, derivedFiles={}",
                event.taskId(), event.fKey(), event.storagePath(), 
                event.derivedFiles() != null ? event.derivedFiles().size() : 0);
    }

    @Override
    public void publishFailed(TaskFailedEvent event) {
        log.warn("Task failed event: taskId={}, fKey={}, status={}, reason={}",
                event.taskId(), event.fKey(), event.finalStatus(), event.failureReason());
    }

    @Override
    public void publishDerivedFilesAdded(DerivedFilesAddedEvent event) {
        log.info("Derived files added event: taskId={}, sourceFkey={}, count={}",
                event.taskId(), event.sourceFkey(), event.getDerivedFileCount());
    }
}
