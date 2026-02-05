package tech.icc.filesrv.common.exception;

import lombok.Getter;
import tech.icc.filesrv.common.constants.ResultCode;

/**
 * 资源未找到异常
 * <p>
 * 当请求的资源不存在时抛出，例如：
 * <ul>
 *   <li>请求的文件不存在</li>
 *   <li>请求的存储节点不存在</li>
 * </ul>
 * 这是客户端问题（404），需要客户端确认请求参数是否正确。
 */
@Getter
public class NotFoundException extends FileServiceException implements WithoutStack {
    protected final String identifier;
    public NotFoundException(int code, String type, String identifier) {
        super(code, buildMessage(type, identifier), null);
        this.identifier = identifier;
    }

    private static String buildMessage(String resourceType, String identifier) {
        return "Resource[" + resourceType + "] not found: " + identifier;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    /** Factory methods for specific NotFoundExceptions **/
    public static FileNotFoundException withFileKey(String fileKey) {
        return new FileNotFoundException(fileKey);
    }

    /** Factory methods for specific NotFoundExceptions **/
    public static TaskNotFoundException withTaskId(String taskId) {
        return new TaskNotFoundException(taskId);
    }

    /** Factory methods for specific NotFoundExceptions **/
    public static PluginNotFoundException withPluginName(String pluginName) {
        return new PluginNotFoundException(pluginName);
    }

    /**
     * 文件未找到异常
     */
    public static class FileNotFoundException extends NotFoundException {
        public FileNotFoundException(String fileKey) {
            super(ResultCode.FILE_NOT_FOUND, "FILE", fileKey);
        }
    }

    /**
     * 任务未找到异常
     */
    public static class TaskNotFoundException extends NotFoundException {
        public TaskNotFoundException(String taskId) {
            super(ResultCode.TASK_NOT_FOUND, "TASK", taskId);
        }
    }

    /**
     * 插件未找到异常
     */
    public static class PluginNotFoundException extends NotFoundException {
        public PluginNotFoundException(String pluginName) {
            super(ResultCode.PLUGIN_NOT_FOUND, "PLUGIN", pluginName);
        }
    }
}
