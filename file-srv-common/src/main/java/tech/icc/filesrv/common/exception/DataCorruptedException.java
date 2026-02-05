package tech.icc.filesrv.common.exception;

import tech.icc.filesrv.common.constants.ResultCode;

/**
 * 数据一致性异常
 * <p>
 * 当检测到数据不一致时抛出，例如：
 * <ul>
 *   <li>文件引用存在但物理文件丢失</li>
 *   <li>FileInfo 存在但无可用存储副本</li>
 * </ul>
 * 这是服务端问题（500），需要运维介入排查。
 */
public class DataCorruptedException extends FileServiceException {
    public DataCorruptedException(String message) {
        super(ResultCode.DATA_CORRUPTED, message, null);
    }
}
