-- ==========================================
-- ICC File Service Database Schema
-- ==========================================
-- Version: 1.0.0
-- Generated: 2026-02-03
-- Description: Database schema for ICC file service
-- Supported databases: MySQL 8.0+, PostgreSQL 13+
-- ==========================================

-- ==========================================
-- 1. 物理文件信息表 (file_info)
-- ==========================================
-- 存储物理文件的元数据和引用计数
-- 主键: content_hash (内容哈希值，确保去重)
-- ==========================================

CREATE TABLE file_info (
    content_hash VARCHAR(64) PRIMARY KEY COMMENT '内容哈希值(SHA-256)',
    size BIGINT NOT NULL COMMENT '文件大小(字节)',
    content_type VARCHAR(128) COMMENT '文件MIME类型',
    ref_count INT NOT NULL DEFAULT 0 COMMENT '引用计数',
    status VARCHAR(16) NOT NULL COMMENT '文件状态: ACTIVE, PENDING, DELETED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    CONSTRAINT chk_file_info_status CHECK (status IN ('ACTIVE', 'PENDING', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='物理文件信息表，基于内容哈希去重';

-- ==========================================
-- 2. 存储副本表 (storage_copy)
-- ==========================================
-- 存储物理文件在不同存储节点的副本信息
-- 与 file_info 表是一对多关系
-- ==========================================

CREATE TABLE storage_copy (
    content_hash VARCHAR(64) NOT NULL COMMENT '关联的物理文件哈希',
    copy_id VARCHAR(36) NOT NULL COMMENT '副本ID(UUID)',
    node_id VARCHAR(32) NOT NULL COMMENT '存储节点ID',
    path VARCHAR(512) NOT NULL COMMENT '存储路径',
    tier VARCHAR(16) NOT NULL COMMENT '存储层级: HOT, WARM, COLD, ARCHIVE',
    copy_status VARCHAR(16) NOT NULL COMMENT '副本状态: ACTIVE, PENDING, MIGRATING, DELETED',
    copy_created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '副本创建时间',
    PRIMARY KEY (content_hash, copy_id),
    INDEX idx_node_id (node_id),
    INDEX idx_tier (tier),
    INDEX idx_copy_status (copy_status),
    CONSTRAINT fk_storage_copy_file_info 
        FOREIGN KEY (content_hash) REFERENCES file_info(content_hash) 
        ON DELETE CASCADE,
    CONSTRAINT chk_storage_copy_tier CHECK (tier IN ('HOT', 'WARM', 'COLD', 'ARCHIVE')),
    CONSTRAINT chk_storage_copy_status CHECK (copy_status IN ('ACTIVE', 'PENDING', 'MIGRATING', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='存储副本表，记录文件在不同存储节点的副本';

-- ==========================================
-- 3. 文件引用表 (file_reference)
-- ==========================================
-- 存储用户视角的文件引用（逻辑文件）
-- 主键: f_key (文件键，用户层面的文件标识)
-- ==========================================

CREATE TABLE file_reference (
    f_key VARCHAR(36) PRIMARY KEY COMMENT '文件键(用户层面的文件标识)',
    content_hash VARCHAR(64) COMMENT '关联的物理文件哈希',
    filename VARCHAR(255) NOT NULL COMMENT '文件名',
    content_type VARCHAR(128) COMMENT '文件MIME类型',
    size BIGINT COMMENT '文件大小(字节)',
    etag VARCHAR(128) COMMENT '实体标签',
    owner_id VARCHAR(64) COMMENT '所有者ID',
    owner_name VARCHAR(128) COMMENT '所有者名称',
    status VARCHAR(16) NOT NULL COMMENT '文件状态: ACTIVE, PENDING, DELETED',
    is_public BOOLEAN DEFAULT FALSE COMMENT '是否公开',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    INDEX idx_content_hash (content_hash),
    INDEX idx_owner_id (owner_id),
    INDEX idx_status (status),
    INDEX idx_filename (filename),
    INDEX idx_created_at (created_at),
    CONSTRAINT chk_file_reference_status CHECK (status IN ('ACTIVE', 'PENDING', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='文件引用表，存储用户视角的逻辑文件';

-- ==========================================
-- 4. 文件关系表 (file_relations)
-- ==========================================
-- 存储文件之间的关系（源文件、主文件、派生文件）
-- 复合主键: (file_fkey, related_fkey, relation_type)
-- ==========================================

CREATE TABLE file_relations (
    file_fkey VARCHAR(64) NOT NULL COMMENT '文件键',
    related_fkey VARCHAR(64) NOT NULL COMMENT '关联文件键',
    relation_type VARCHAR(16) NOT NULL COMMENT '关系类型: SOURCE, CURRENT_MAIN, DERIVED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (file_fkey, related_fkey, relation_type),
    INDEX idx_related (related_fkey, relation_type),
    INDEX idx_created (created_at),
    CONSTRAINT chk_file_relations_type CHECK (relation_type IN ('SOURCE', 'CURRENT_MAIN', 'DERIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='文件关系表，记录文件之间的源文件、主文件、派生文件关系';

-- ==========================================
-- 5. 上传任务表 (upload_task)
-- ==========================================
-- 存储异步上传任务的状态和元数据
-- 主键: task_id (任务ID)
-- 支持分片上传和回调链
-- ==========================================

CREATE TABLE upload_task (
    task_id VARCHAR(36) PRIMARY KEY COMMENT '任务ID(UUID)',
    f_key VARCHAR(128) NOT NULL COMMENT '关联的文件键',
    status VARCHAR(16) NOT NULL COMMENT '任务状态: PENDING, UPLOADING, MERGING, COMPLETED, FAILED, EXPIRED',
    node_id VARCHAR(64) COMMENT '存储节点ID',
    session_id VARCHAR(255) COMMENT '会话ID',
    storage_path VARCHAR(512) COMMENT '存储路径',
    hash VARCHAR(64) COMMENT '内容哈希值',
    total_size BIGINT COMMENT '总大小(字节)',
    content_type VARCHAR(128) COMMENT '内容类型',
    filename VARCHAR(255) COMMENT '文件名',
    parts JSON COMMENT '分片信息列表',
    callbacks JSON COMMENT '回调配置列表',
    current_callback_index INT COMMENT '当前回调索引',
    context JSON COMMENT '任务上下文',
    failure_reason VARCHAR(1024) COMMENT '失败原因',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    expires_at TIMESTAMP(6) COMMENT '过期时间',
    completed_at TIMESTAMP(6) COMMENT '完成时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
    INDEX idx_fkey (f_key),
    INDEX idx_status (status),
    INDEX idx_expires_at (expires_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='上传任务表，支持分片上传和回调链';

-- ==========================================
-- 6. 存储节点表 (storage_node)
-- ==========================================
-- 存储存储节点的配置和状态
-- 主键: node_id (节点ID)
-- ==========================================

CREATE TABLE storage_node (
    node_id VARCHAR(32) PRIMARY KEY COMMENT '节点ID',
    node_name VARCHAR(64) NOT NULL COMMENT '节点名称',
    adapter_type VARCHAR(32) NOT NULL COMMENT '适配器类型',
    endpoint VARCHAR(256) COMMENT '端点地址',
    bucket VARCHAR(64) COMMENT '存储桶名称',
    tier VARCHAR(16) COMMENT '存储层级: HOT, WARM, COLD, ARCHIVE',
    status VARCHAR(16) NOT NULL COMMENT '节点状态: ACTIVE, INACTIVE, READONLY, MAINTENANCE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    INDEX idx_status (status),
    INDEX idx_tier (tier),
    INDEX idx_adapter_type (adapter_type),
    CONSTRAINT chk_storage_node_tier CHECK (tier IN ('HOT', 'WARM', 'COLD', 'ARCHIVE')),
    CONSTRAINT chk_storage_node_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'READONLY', 'MAINTENANCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='存储节点表，存储各个存储节点的配置';

-- ==========================================
-- 额外的复合索引
-- ==========================================

-- file_reference 表：支持按所有者和状态查询
CREATE INDEX idx_file_reference_owner_status ON file_reference(owner_id, status);

-- file_reference 表：支持按内容类型和创建时间查询
CREATE INDEX idx_file_reference_content_type_created ON file_reference(content_type, created_at);

-- upload_task 表：支持按文件键和状态查询
CREATE INDEX idx_upload_task_fkey_status ON upload_task(f_key, status);

-- upload_task 表：支持按状态和过期时间查询（清理任务）
CREATE INDEX idx_upload_task_status_expires ON upload_task(status, expires_at);

-- storage_copy 表：支持按节点和状态查询
CREATE INDEX idx_storage_copy_node_status ON storage_copy(node_id, copy_status);

-- ==========================================
-- 初始化数据（可选）
-- ==========================================

-- 插入默认存储节点配置（示例）
-- INSERT INTO storage_node (node_id, node_name, adapter_type, tier, status, created_at) VALUES
-- ('node-001', 'Primary HCS Storage', 'HCS', 'HOT', 'ACTIVE', CURRENT_TIMESTAMP(6)),
-- ('node-002', 'Backup HCS Storage', 'HCS', 'WARM', 'ACTIVE', CURRENT_TIMESTAMP(6));

-- ==========================================
-- PostgreSQL 版本的差异说明
-- ==========================================
-- 如果使用 PostgreSQL，需要做以下调整：
-- 1. TIMESTAMP(6) 改为 TIMESTAMP(6) WITH TIME ZONE
-- 2. AUTO_INCREMENT 改为 SERIAL 或使用 SEQUENCE
-- 3. JSON 类型在 PostgreSQL 中原生支持
-- 4. 删除 ENGINE=InnoDB 和 CHARSET/COLLATE 选项
-- 5. DEFAULT CURRENT_TIMESTAMP(6) 改为 DEFAULT CURRENT_TIMESTAMP
-- 6. ON UPDATE CURRENT_TIMESTAMP(6) 需要使用触发器实现
-- ==========================================

-- ==========================================
-- 表结构说明
-- ==========================================
-- 1. file_info: 存储物理文件的唯一副本（基于内容哈希去重）
-- 2. storage_copy: 存储物理文件在不同节点的副本信息
-- 3. file_reference: 用户层面的文件引用（逻辑文件）
-- 4. file_relations: 文件之间的关系（源文件、主文件、派生文件）
-- 5. upload_task: 异步上传任务，支持分片上传和回调链
-- 6. storage_node: 存储节点配置
-- ==========================================
