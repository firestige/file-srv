package tech.icc.filesrv.common.context;

/**
 * TaskContext 键名常量
 * <p>
 * 定义 TaskContext 中使用的标准键名，避免硬编码字符串，提供类型安全的访问。
 * </p>
 * 
 * <h3>键名分类</h3>
 * <ul>
 *   <li><b>TASK_*</b>: 任务基础信息（taskId, status等）</li>
 *   <li><b>FILE_*</b>: 主文件信息（fKey, name, size等）</li>
 *   <li><b>DELIVERY_*</b>: 衍生文件信息（缩略图、转码文件等）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 读取任务ID
 * String taskId = context.getString(TaskContextKeys.TASK_ID);
 * 
 * // 写入衍生文件路径
 * context.put(TaskContextKeys.deliveryPath("thumb_123"), "/storage/thumb.jpg");
 * </pre>
 *
 * @see TaskContext
 */
public final class TaskContextKeys {

    private TaskContextKeys() {
        // 工具类，禁止实例化
    }

    // ==================== Task 信息 ====================

    /**
     * 任务唯一标识
     * <p>
     * 类型: String<br>
     * 示例: "550e8400-e29b-41d4-a716-446655440000"
     */
    public static final String TASK_ID = "task.id";

    /**
     * 任务当前状态
     * <p>
     * 类型: String (TaskStatus枚举名称)<br>
     * 可选值: PENDING, IN_PROGRESS, PROCESSING, COMPLETED, FAILED, ABORTED<br>
     * 示例: "PROCESSING"
     */
    public static final String TASK_STATUS = "task.status";

    /**
     * 任务创建时间（ISO-8601格式）
     * <p>
     * 类型: String<br>
     * 示例: "2026-02-01T10:30:00Z"
     */
    public static final String TASK_CREATED_AT = "task.createdAt";

    // ==================== File 信息 ====================

    /**
     * 文件唯一标识（主文件）
     * <p>
     * 类型: String<br>
     * 注入时机: completeUpload()<br>
     * 示例: "file_abc123xyz"
     */
    public static final String FILE_FKEY = "file.fkey";

    /**
     * 文件名（主文件）
     * <p>
     * 类型: String<br>
     * 示例: "avatar.jpg"
     */
    public static final String FILE_NAME = "file.name";

    /**
     * 文件 MIME 类型（主文件）
     * <p>
     * 类型: String<br>
     * 示例: "image/jpeg"
     */
    public static final String FILE_TYPE = "file.type";

    /**
     * 文件大小（字节数，主文件）
     * <p>
     * 类型: Long<br>
     * 示例: 1048576 (1MB)
     */
    public static final String FILE_SIZE = "file.size";

    /**
     * 文件存储路径（主文件）
     * <p>
     * 类型: String<br>
     * 示例: "/storage/2026/02/01/abc123.jpg"
     */
    public static final String FILE_PATH = "file.path";

    /**
     * 文件校验和/ETag（主文件）
     * <p>
     * 类型: String<br>
     * 示例: "d41d8cd98f00b204e9800998ecf8427e"
     */
    public static final String FILE_ETAG = "file.etag";

    /**
     * 文件内容哈希（主文件）
     * <p>
     * 类型: String<br>
     * 示例: "xxhash64:1234567890abcdef"
     */
    public static final String FILE_HASH = "file.hash";

    // ==================== Delivery 信息（衍生文件） ====================

    /**
     * 衍生文件类型键前缀
     * <p>
     * 格式: delivery.{fkey}.type<br>
     * 示例: "delivery.thumb_123.type" -> "THUMBNAIL"
     */
    private static final String DELIVERY_TYPE_PREFIX = "delivery.";
    private static final String DELIVERY_TYPE_SUFFIX = ".type";

    /**
     * 衍生文件路径键前缀
     * <p>
     * 格式: delivery.{fkey}.path<br>
     * 示例: "delivery.thumb_123.path" -> "/storage/thumb.jpg"
     */
    private static final String DELIVERY_PATH_SUFFIX = ".path";

    /**
     * 衍生文件内容类型键前缀
     * <p>
     * 格式: delivery.{fkey}.contentType<br>
     * 示例: "delivery.thumb_123.contentType" -> "image/jpeg"
     */
    private static final String DELIVERY_CONTENT_TYPE_SUFFIX = ".contentType";

    /**
     * 衍生文件大小键前缀
     * <p>
     * 格式: delivery.{fkey}.size<br>
     * 示例: "delivery.thumb_123.size" -> 51200
     */
    private static final String DELIVERY_SIZE_SUFFIX = ".size";

    /**
     * 衍生文件关联关系键前缀
     * <p>
     * 格式: delivery.{fkey}.refKeys<br>
     * 示例: "delivery.thumb_123.refKeys" -> "file_main,file_original"
     */
    private static final String DELIVERY_REF_KEYS_SUFFIX = ".refKeys";

    // ==================== 辅助方法：动态键名生成 ====================

    /**
     * 生成衍生文件类型键
     * <p>
     * 示例: deliveryType("thumb_123") -> "delivery.thumb_123.type"
     *
     * @param fkey 衍生文件的 fKey
     * @return 完整的键名
     */
    public static String deliveryType(String fkey) {
        return DELIVERY_TYPE_PREFIX + fkey + DELIVERY_TYPE_SUFFIX;
    }

    /**
     * 生成衍生文件路径键
     * <p>
     * 示例: deliveryPath("thumb_123") -> "delivery.thumb_123.path"
     *
     * @param fkey 衍生文件的 fKey
     * @return 完整的键名
     */
    public static String deliveryPath(String fkey) {
        return DELIVERY_TYPE_PREFIX + fkey + DELIVERY_PATH_SUFFIX;
    }

    /**
     * 生成衍生文件内容类型键
     * <p>
     * 示例: deliveryContentType("thumb_123") -> "delivery.thumb_123.contentType"
     *
     * @param fkey 衍生文件的 fKey
     * @return 完整的键名
     */
    public static String deliveryContentType(String fkey) {
        return DELIVERY_TYPE_PREFIX + fkey + DELIVERY_CONTENT_TYPE_SUFFIX;
    }

    /**
     * 生成衍生文件大小键
     * <p>
     * 示例: deliverySize("thumb_123") -> "delivery.thumb_123.size"
     *
     * @param fkey 衍生文件的 fKey
     * @return 完整的键名
     */
    public static String deliverySize(String fkey) {
        return DELIVERY_TYPE_PREFIX + fkey + DELIVERY_SIZE_SUFFIX;
    }

    /**
     * 生成衍生文件关联关系键
     * <p>
     * 示例: deliveryRefKeys("thumb_123") -> "delivery.thumb_123.refKeys"
     *
     * @param fkey 衍生文件的 fKey
     * @return 完整的键名
     */
    public static String deliveryRefKeys(String fkey) {
        return DELIVERY_TYPE_PREFIX + fkey + DELIVERY_REF_KEYS_SUFFIX;
    }

    /**
     * 判断键名是否为衍生文件相关键
     * <p>
     * 示例: isDeliveryKey("delivery.thumb_123.path") -> true
     *
     * @param key 键名
     * @return 如果是衍生文件键返回 true
     */
    public static boolean isDeliveryKey(String key) {
        return key != null && key.startsWith(DELIVERY_TYPE_PREFIX);
    }

    /**
     * 从衍生文件键中提取 fKey
     * <p>
     * 示例: extractFKeyFromDeliveryKey("delivery.thumb_123.path") -> "thumb_123"
     *
     * @param deliveryKey 衍生文件键名
     * @return 提取的 fKey，如果格式不正确返回 null
     */
    public static String extractFKeyFromDeliveryKey(String deliveryKey) {
        if (!isDeliveryKey(deliveryKey)) {
            return null;
        }
        // delivery.{fkey}.{suffix}
        String withoutPrefix = deliveryKey.substring(DELIVERY_TYPE_PREFIX.length());
        int dotIndex = withoutPrefix.indexOf('.');
        if (dotIndex < 0) {
            return null;
        }
        return withoutPrefix.substring(0, dotIndex);
    }
}
