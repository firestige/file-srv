package tech.icc.filesrv.core.infra.event.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.icc.filesrv.common.vo.file.FileRelations;
import tech.icc.filesrv.common.vo.task.DerivedFile;
import tech.icc.filesrv.common.domain.events.DerivedFilesAddedEvent;
import tech.icc.filesrv.core.infra.persistence.entity.FileRelationEntity;
import tech.icc.filesrv.core.infra.persistence.repository.FileRelationRepository;

/**
 * 文件关联关系事件处理器
 * <p>
 * 监听 DerivedFilesAddedEvent，自动维护文件关联关系。
 * 当 Plugin 创建衍生文件时，自动为每个衍生文件创建 FileRelation 记录。
 * </p>
 * 
 * <h3>设计说明</h3>
 * <ul>
 *   <li>使用 @TransactionalEventListener(AFTER_COMMIT) 确保 Task 保存后再处理关系</li>
 *   <li>为每个 derivedFile 创建 3 条记录：SOURCE、CURRENT_MAIN、反向 DERIVED</li>
 *   <li>实现双向引用设计，避免孤儿文件产生</li>
 * </ul>
 */
@Component
public class FileRelationsEventHandler {

    private static final Logger log = LoggerFactory.getLogger(FileRelationsEventHandler.class);

    private final FileRelationRepository relationRepository;

    public FileRelationsEventHandler(FileRelationRepository relationRepository) {
        this.relationRepository = relationRepository;
    }

    /**
     * 处理衍生文件添加事件
     * <p>
     * 在 Task 事务提交后执行，确保 Task 状态已持久化。
     * 为每个新衍生文件创建文件关联关系记录。
     * </p>
     *
     * @param event 衍生文件添加事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDerivedFilesAdded(DerivedFilesAddedEvent event) {
        if (!event.hasDerivedFiles()) {
            return;
        }

        String sourceFkey = event.sourceFkey();
        log.info("Handling derived files event: taskId={}, sourceFkey={}, count={}",
                event.taskId(), sourceFkey, event.getDerivedFileCount());

        for (DerivedFile derivedFile : event.newDerivedFiles()) {
            String derivedFkey = derivedFile.fKey();
            
            try {
                // 检查是否已存在关系记录（幂等性）
                if (relationRepository.existsByFileFkeyAndRelatedFkey(derivedFkey, sourceFkey)) {
                    log.debug("Relation already exists: derived={}, source={}", derivedFkey, sourceFkey);
                    continue;
                }

                // 1. 创建 SOURCE 关系（衍生文件 -> 源文件）
                FileRelationEntity sourceRelation = FileRelationEntity.builder()
                        .fileFkey(derivedFkey)
                        .relatedFkey(sourceFkey)
                        .relationType(FileRelationEntity.RelationType.SOURCE)
                        .build();
                relationRepository.save(sourceRelation);

                // 2. 创建 CURRENT_MAIN 关系（衍生文件 -> 当前主文件，初始等于源文件）
                FileRelationEntity currentMainRelation = FileRelationEntity.builder()
                        .fileFkey(derivedFkey)
                        .relatedFkey(sourceFkey)
                        .relationType(FileRelationEntity.RelationType.CURRENT_MAIN)
                        .build();
                relationRepository.save(currentMainRelation);

                // 3. 创建反向 DERIVED 关系（源文件 -> 衍生文件）
                FileRelationEntity derivedRelation = FileRelationEntity.builder()
                        .fileFkey(sourceFkey)
                        .relatedFkey(derivedFkey)
                        .relationType(FileRelationEntity.RelationType.DERIVED)
                        .build();
                relationRepository.save(derivedRelation);

                log.debug("Created file relations: derived={}, source={}", derivedFkey, sourceFkey);

            } catch (Exception e) {
                log.error("Failed to create file relations: derived={}, source={}, error={}",
                        derivedFkey, sourceFkey, e.getMessage(), e);
                // 不抛出异常，避免影响其他衍生文件的处理
            }
        }

        log.info("Completed derived files processing: taskId={}, count={}",
                event.taskId(), event.getDerivedFileCount());
    }
}
