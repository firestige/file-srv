package tech.icc.filesrv.core.infra.cache;

/**
 * 任务 ID 验证器
 * <p>
 * 提供快速的 ID 格式校验和存在性检查（布隆过滤器）。
 */
public interface TaskIdValidator {

    /**
     * 验证 taskId 格式是否合法
     *
     * @param taskId 任务 ID
     * @return 格式是否合法
     */
    boolean isValidFormat(String taskId);

    /**
     * 快速判断 taskId 是否可能存在
     * <p>
     * 使用布隆过滤器实现，可能有误判（返回 true 但实际不存在），
     * 但返回 false 则一定不存在。
     *
     * @param taskId 任务 ID
     * @return 是否可能存在
     */
    boolean mightExist(String taskId);

    /**
     * 注册 taskId 到布隆过滤器
     *
     * @param taskId 任务 ID
     */
    void register(String taskId);

    /**
     * 批量注册
     *
     * @param taskIds 任务 ID 列表
     */
    default void registerAll(Iterable<String> taskIds) {
        taskIds.forEach(this::register);
    }
}
