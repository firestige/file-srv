package tech.icc.filesrv.core.domain.storage;

/**
 * 存储层级
 * <p>
 * 定义存储节点的性能/成本层级，用于冷热数据分层。
 */
public enum StorageTier {

    /** 热存储 - 高频访问，低延迟，高成本 */
    HOT,

    /** 温存储 - 中频访问，中等延迟 */
    WARM,

    /** 冷存储 - 低频访问，高延迟，低成本 */
    COLD,

    /** 归档存储 - 极低频，最高延迟，最低成本 */
    ARCHIVE;

    /**
     * 是否支持即时访问
     *
     * @return HOT/WARM 返回 true，COLD/ARCHIVE 返回 false
     */
    public boolean isImmediateAccess() {
        return this == HOT || this == WARM;
    }

    /**
     * 获取相对成本等级（1-4，越低越便宜）
     */
    public int getCostLevel() {
        return switch (this) {
            case HOT -> 4;
            case WARM -> 3;
            case COLD -> 2;
            case ARCHIVE -> 1;
        };
    }
}
