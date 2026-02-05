-- 添加文件元数据字段
-- 版本: V2
-- 描述: 为 file_reference 表添加 tags 和 custom_metadata 字段

-- 添加 tags 字段（文本格式，空格分隔的标签列表）
ALTER TABLE file_reference ADD COLUMN tags TEXT;

-- 添加 custom_metadata 字段（JSON 格式）
ALTER TABLE file_reference ADD COLUMN custom_metadata JSON;

-- 为常见查询添加索引（可选）
-- CREATE INDEX idx_file_reference_tags ON file_reference USING gin(to_tsvector('simple', tags));
