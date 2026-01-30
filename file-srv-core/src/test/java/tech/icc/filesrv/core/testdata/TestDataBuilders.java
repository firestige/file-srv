package tech.icc.filesrv.core.testdata;

import net.datafaker.Faker;
import org.instancio.Instancio;
import tech.icc.filesrv.common.vo.audit.OwnerInfo;
import tech.icc.filesrv.common.vo.file.AccessControl;
import tech.icc.filesrv.common.vo.task.CallbackConfig;
import tech.icc.filesrv.core.domain.files.FileReference;
import tech.icc.filesrv.core.domain.tasks.PartInfo;
import tech.icc.filesrv.core.domain.tasks.TaskAggregate;

import java.time.Duration;
import java.util.List;

/**
 * 测试数据构建器 - 框架定义
 * <p>
 * 设计原则：
 * <ul>
 *   <li>复用 Lombok @Builder - 不自己实现 Builder 类</li>
 *   <li>DataFaker 优先 - 生成有业务语义的随机数据（文件名、MIME、大小等）</li>
 *   <li>Instancio 补充 - 复杂对象的批量填充</li>
 *   <li>固定 seed=42 - 保证测试可复现</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 使用默认值
 * TaskAggregate task = TestDataBuilders.aTask();
 * 
 * // 自定义字段（使用 Lombok Builder）
 * TaskAggregate task = TestDataBuilders.aTaskBuilder()
 *     .fKey("custom-fkey")
 *     .status(TaskStatus.IN_PROGRESS)
 *     .build();
 * 
 * // 随机数据（DataFaker，seed=42 可复现）
 * String filename = TestDataBuilders.randomFilename();
 * String contentType = TestDataBuilders.randomContentType();
 * }</pre>
 * <p>
 * 数据来源标注：
 * <ul>
 *   <li>🎲 DataFaker - 有业务语义的随机数据</li>
 *   <li>🤖 Instancio - 复杂对象批量生成</li>
 *   <li>📦 Lombok - 使用现有 @Builder</li>
 * </ul>
 */
public class TestDataBuilders {

    /**
     * DataFaker 实例（固定 seed=42，保证可复现）
     */
    private static final Faker faker = new Faker(new java.util.Random(42));

    // ==================== Domain 聚合根 ====================

    /**
     * 创建默认 TaskAggregate
     * <p>
     * 状态: PENDING<br>
     * Callbacks: 空<br>
     * 过期时间: 24小时
     *
     * @return 带默认值的任务聚合根
     */
    public static TaskAggregate aTask() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 获取 TaskAggregate Builder（如果有 Lombok @Builder）
     * <p>
     * 📦 复用 Domain 对象的 Builder
     *
     * @return TaskAggregate.TaskAggregateBuilder
     */
    // public static TaskAggregate.TaskAggregateBuilder aTaskBuilder() {
    //     // TODO: 如果 TaskAggregate 有 @Builder，复用它
    //     throw new UnsupportedOperationException("TaskAggregate 需要 @Builder 注解");
    // }

    /**
     * 创建带指定状态的任务
     * <p>
     * 快捷方法，用于常见测试场景
     *
     * @param status 目标状态
     * @return 对应状态的任务
     */
    // public static TaskAggregate aTaskWithStatus(TaskStatus status) {
    //     // TODO: 实现
    //     throw new UnsupportedOperationException("待实现");
    // }

    // ==================== 值对象 ====================

    /**
     * 创建默认 PartInfo（分片信息）
     * <p>
     * 分片号: 1<br>
     * ETag: 自动生成<br>
     * 大小: 5MB
     *
     * @return 默认分片信息
     */
    public static PartInfo aPart() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 创建指定分片号的 PartInfo
     *
     * @param partNumber 分片号（从1开始）
     * @return 分片信息
     */
    public static PartInfo aPart(int partNumber) {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 批量创建分片列表
     * <p>
     * 用于多段上传测试场景
     *
     * @param count 分片数量
     * @return 分片列表，分片号从 1 到 count
     */
    public static List<PartInfo> parts(int count) {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 创建默认 FileReference
     * <p>
     * 📦 使用 FileReference 的 Lombok @Builder
     *
     * @return 带默认值的文件引用
     */
    public static FileReference aFileReference() {
        // TODO: 实现，使用 FileReference.builder()
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 创建默认 CallbackConfig
     * <p>
     * 插件名: test-plugin<br>
     * 参数: 空
     *
     * @return 回调配置
     */
    public static CallbackConfig aCallback() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 创建指定插件名的 CallbackConfig
     *
     * @param pluginName 插件名称
     * @return 回调配置
     */
    public static CallbackConfig aCallback(String pluginName) {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    // ==================== 共享 VO ====================

    /**
     * 创建默认 OwnerInfo
     * <p>
     * 📦 使用 OwnerInfo.builder()
     * <p>
     * 用户ID: user-123<br>
     * 用户名: Test User
     *
     * @return 所有者信息
     */
    public static OwnerInfo anOwner() {
        // TODO: 实现，使用 OwnerInfo.builder()
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 创建私有访问控制
     *
     * @return AccessControl(isPublic=false)
     */
    public static AccessControl privateAccess() {
        return AccessControl.privateAccess();
    }

    /**
     * 创建公开访问控制
     *
     * @return AccessControl(isPublic=true)
     */
    public static AccessControl publicAccess() {
        return AccessControl.publicAccess();
    }

    // ==================== DataFaker 随机数据 🎲 ====================

    /**
     * 生成随机文件名
     * <p>
     * 🎲 DataFaker: file().fileName()
     * <p>
     * 示例: "document.pdf", "report_2024.xlsx"
     *
     * @return 随机文件名（含扩展名）
     */
    public static String randomFilename() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 生成指定扩展名的随机文件名
     * <p>
     * 🎲 DataFaker: file().fileName() + 指定扩展名
     *
     * @param extension 扩展名（如 "pdf", "jpg"）
     * @return 随机文件名
     */
    public static String randomFilename(String extension) {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 生成随机 MIME 类型
     * <p>
     * 🎲 DataFaker: file().mimeType()
     * <p>
     * 示例: "application/pdf", "image/jpeg", "text/plain"
     *
     * @return 随机 MIME 类型
     */
    public static String randomContentType() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 生成随机文件大小（字节）
     * <p>
     * 🎲 DataFaker: number().numberBetween(1KB, 100MB)
     * <p>
     * 范围: 1KB ~ 100MB
     *
     * @return 随机文件大小
     */
    public static long randomFileSize() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 生成指定范围的随机文件大小
     * <p>
     * 🎲 DataFaker: number().numberBetween(min, max)
     *
     * @param minBytes 最小值（字节）
     * @param maxBytes 最大值（字节）
     * @return 随机文件大小
     */
    public static long randomFileSize(long minBytes, long maxBytes) {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 生成随机 ETag
     * <p>
     * 🎲 格式: "etag-" + UUID 前8位
     * <p>
     * 示例: "etag-a1b2c3d4"
     *
     * @return 随机 ETag
     */
    public static String randomETag() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 生成随机内容哈希（SHA-256格式）
     * <p>
     * 🎲 格式: 64位十六进制字符串
     * <p>
     * 示例: "a1b2c3d4e5f6..."
     *
     * @return 随机哈希值
     */
    public static String randomContentHash() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 生成随机用户ID
     * <p>
     * 🎲 DataFaker: internet().uuid()
     *
     * @return 随机用户ID
     */
    public static String randomUserId() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 生成随机用户名
     * <p>
     * 🎲 DataFaker: name().fullName()
     *
     * @return 随机用户名
     */
    public static String randomUsername() {
        // TODO: 实现
        throw new UnsupportedOperationException("待实现");
    }

    // ==================== Instancio 批量生成 🤖 ====================

    /**
     * 批量生成随机 TaskAggregate 列表
     * <p>
     * 🤖 Instancio: 用于压力测试、性能测试
     *
     * @param count 数量
     * @return 任务列表
     */
    public static List<TaskAggregate> randomTasks(int count) {
        // TODO: 实现，使用 Instancio.ofList(TaskAggregate.class).size(count).create()
        throw new UnsupportedOperationException("待实现");
    }

    /**
     * 批量生成随机 FileReference 列表
     * <p>
     * 🤖 Instancio: 用于批量数据测试
     *
     * @param count 数量
     * @return 文件引用列表
     */
    public static List<FileReference> randomFileReferences(int count) {
        // TODO: 实现，使用 Instancio
        throw new UnsupportedOperationException("待实现");
    }

    // ==================== 常量和工具 ====================

    /**
     * 标准小文件大小: 1KB
     */
    public static final long SIZE_1KB = 1024L;

    /**
     * 标准中等文件大小: 1MB
     */
    public static final long SIZE_1MB = 1024L * 1024;

    /**
     * 标准大文件大小: 10MB（同步上传上限）
     */
    public static final long SIZE_10MB = 10L * 1024 * 1024;

    /**
     * 标准超大文件: 100MB（需分片上传）
     */
    public static final long SIZE_100MB = 100L * 1024 * 1024;

    /**
     * 分片大小: 5MB（多段上传默认分片）
     */
    public static final long PART_SIZE_5MB = 5L * 1024 * 1024;

    /**
     * 标准过期时间: 24小时
     */
    public static final Duration EXPIRY_24H = Duration.ofHours(24);

    /**
     * 短过期时间: 1小时（用于过期测试）
     */
    public static final Duration EXPIRY_1H = Duration.ofHours(1);
}
