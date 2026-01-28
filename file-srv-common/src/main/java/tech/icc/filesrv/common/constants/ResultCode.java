package tech.icc.filesrv.common.constants;

/**
 * 业务结果码常量
 * <p>
 * 编码规则：
 * <ul>
 *   <li>0 - 成功</li>
 *   <li>0x4XXXX - 客户端错误（4XX 系列）</li>
 *   <li>0x5XXXX - 服务端错误（5XX 系列）</li>
 * </ul>
 */
public interface ResultCode {
    // ==================== 成功 ====================
    int SUCCESS = 0;

    // ==================== 客户端错误 (4XX) ====================
    /** 400 - 请求参数错误 */
    int BAD_REQUEST = 0x40000;

    /** 403 - 访问被拒绝 */
    int ACCESS_DENIED = 0x40300;

    /** 404 - 文件未找到 */
    int FILE_NOT_FOUND = 0x40400;

    /** 409 - 文件未就绪 */
    int FILE_NOT_READY = 0x40900;

    /** 413 - 请求体过大 */
    int PAYLOAD_TOO_LARGE = 0x41300;

    // ==================== 服务端错误 (5XX) ====================
    /** 500 - 服务器内部错误 */
    int INTERNAL_ERROR = 0x50000;

    /** 500 - 数据一致性异常（物理文件丢失或无可用副本） */
    int DATA_CORRUPTED = 0x50001;

    /** 503 - 存储服务不可用 */
    int STORAGE_UNAVAILABLE = 0x50300;
}
