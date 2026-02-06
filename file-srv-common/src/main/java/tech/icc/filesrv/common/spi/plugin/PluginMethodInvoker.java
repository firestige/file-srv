package tech.icc.filesrv.common.spi.plugin;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.annotation.LocalFile;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginExecute;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginInvoker;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginOutput;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginParam;
import tech.icc.filesrv.common.spi.plugin.annotation.TaskInfo;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

/**
 * 插件方法调用器（新式注解插件）
 * <p>
 * 负责：
 * <ul>
 *   <li>通过反射查找标记了 {@link PluginExecute} 的方法</li>
 *   <li>解析方法参数上的注解并从 TaskContext 中注入值</li>
 *   <li>调用方法并返回结果</li>
 * </ul>
 * <p>
 * 参数注入规则：
 * <ul>
 *   <li>{@code @PluginParam("key")} → 从 PluginParamsContext 读取</li>
 *   <li>{@code @LocalFile} → 从 ExecutionInfo 读取 localFilePath</li>
 *   <li>{@code @TaskInfo("field")} → 从 ExecutionInfo 读取指定字段</li>
 *   <li>{@code @PluginOutput("key")} → 从 PluginOutputsContext 读取</li>
 *   <li>{@code TaskContext} (无注解) → 直接传入完整上下文</li>
 * </ul>
 */
public class PluginMethodInvoker implements PluginInvoker {

    private final Object pluginInstance;
    private final String pluginName;
    private final Method executeMethod;

    /**
     * 创建调用器
     *
     * @param pluginInstance 插件实例
     * @param pluginName     插件名称
     * @throws IllegalArgumentException 如果未找到 @PluginExecute 方法
     */
    public PluginMethodInvoker(Object pluginInstance, String pluginName) {
        this.pluginInstance = pluginInstance;
        this.pluginName = pluginName;
        this.executeMethod = findExecuteMethod(pluginInstance.getClass());
    }

    /**
     * 查找标记了 @PluginExecute 的方法
     */
    private Method findExecuteMethod(Class<?> pluginClass) {
        Optional<Method> found = Arrays.stream(pluginClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PluginExecute.class))
                .findFirst();

        if (found.isEmpty()) {
            throw new IllegalArgumentException(
                    "Plugin " + pluginClass.getName() + " must have a method annotated with @PluginExecute");
        }

        Method method = found.get();
        method.setAccessible(true);
        return method;
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }

    @Override
    public Object getPluginInstance() {
        return pluginInstance;
    }

    /**
     * 执行插件方法
     *
     * @param context 任务上下文
     * @return 插件执行结果
     * @throws Exception 方法调用失败
     */
    @Override
    public PluginResult invoke(TaskContext context) throws Exception {
        Parameter[] parameters = executeMethod.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            args[i] = resolveParameter(parameters[i], context);
        }

        Object result = executeMethod.invoke(pluginInstance, args);

        // 如果方法返回 PluginResult，直接返回；否则视为成功
        if (result instanceof PluginResult pluginResult) {
            return pluginResult;
        } else {
            return PluginResult.Success.empty();
        }
    }

    /**
     * 解析单个参数
     */
    private Object resolveParameter(Parameter parameter, TaskContext context) {
        Class<?> paramType = parameter.getType();

        // 1. TaskContext 参数（无需注解，直接注入）
        if (paramType == TaskContext.class) {
            return context;
        }

        // 2. @PluginParam 注入
        if (parameter.isAnnotationPresent(PluginParam.class)) {
            return resolvePluginParam(parameter, context);
        }

        // 3. @LocalFile 注入
        if (parameter.isAnnotationPresent(LocalFile.class)) {
            return resolveLocalFile(parameter, context);
        }

        // 4. @TaskInfo 注入
        if (parameter.isAnnotationPresent(TaskInfo.class)) {
            return resolveTaskInfo(parameter, context);
        }

        // 5. @PluginOutput 注入
        if (parameter.isAnnotationPresent(PluginOutput.class)) {
            return resolvePluginOutput(parameter, context);
        }

        // 不支持的参数类型
        throw new IllegalArgumentException(
                "Parameter " + parameter.getName() + " of type " + paramType.getName()
                        + " must be annotated with @PluginParam, @LocalFile, @TaskInfo, or @PluginOutput, "
                        + "or be of type TaskContext");
    }

    /**
     * 解析 @PluginParam 参数
     */
    private Object resolvePluginParam(Parameter parameter, TaskContext context) {
        PluginParam annotation = parameter.getAnnotation(PluginParam.class);
        String paramKey = annotation.value();
        Class<?> paramType = parameter.getType();

        // 直接从 TaskContext 获取当前 callback 的参数
        Optional<String> value = context.getPluginParam(paramKey);

        // 参数不存在时的处理
        if (value.isEmpty()) {
            // 使用默认值
            if (!annotation.defaultValue().isEmpty()) {
                return convertValue(annotation.defaultValue(), paramType);
            }
            // 必需参数缺失
            if (annotation.required()) {
                throw new IllegalArgumentException(
                        "Required plugin parameter '" + paramKey + "' is missing for plugin '" + pluginName + "'");
            }
            // 可选参数缺失，返回null或基本类型默认值
            return getDefaultValue(paramType);
        }

        return convertValue(value.get(), paramType);
    }

    /**
     * 解析 @LocalFile 参数
     */
    private Object resolveLocalFile(Parameter parameter, TaskContext context) {
        Class<?> paramType = parameter.getType();
        String localPath = context.getLocalFilePath()
                .orElseThrow(() -> new IllegalStateException("Local file path is not available in TaskContext"));

        if (paramType == String.class) {
            return localPath;
        } else if (paramType == File.class) {
            return new File(localPath);
        } else if (paramType == Path.class) {
            return Paths.get(localPath);
        } else {
            throw new IllegalArgumentException(
                    "@LocalFile parameter must be of type String, File, or Path, but got " + paramType.getName());
        }
    }

    /**
     * 解析 @TaskInfo 参数
     */
    private Object resolveTaskInfo(Parameter parameter, TaskContext context) {
        TaskInfo annotation = parameter.getAnnotation(TaskInfo.class);
        String fieldName = annotation.value();
        Class<?> paramType = parameter.getType();

        Object value = switch (fieldName) {
            case "taskId" -> context.executionInfo().getTaskId().orElse(null);
            case "fileHash" -> context.executionInfo().getFileHash().orElse(null);
            case "contentType" -> context.executionInfo().getContentType().orElse(null);
            case "fileSize" -> context.executionInfo().getFileSize().orElse(null);
            case "filename" -> context.executionInfo().getFilename().orElse(null);
            case "storagePath" -> context.executionInfo().getStoragePath().orElse(null);
            default -> throw new IllegalArgumentException(
                    "Unknown @TaskInfo field: " + fieldName + ". Supported: taskId, fileHash, contentType, fileSize, filename, storagePath");
        };

        if (value == null) {
            throw new IllegalStateException("Task info field '" + fieldName + "' is not available in TaskContext");
        }

        return convertValue(value, paramType);
    }

    /**
     * 解析 @PluginOutput 参数
     */
    private Object resolvePluginOutput(Parameter parameter, TaskContext context) {
        PluginOutput annotation = parameter.getAnnotation(PluginOutput.class);
        String outputKey = annotation.value();
        Class<?> paramType = parameter.getType();

        Optional<Object> value = context.pluginOutputs().get(outputKey);

        // 输出不存在时的处理
        if (value.isEmpty()) {
            // 使用默认值
            if (!annotation.defaultValue().isEmpty()) {
                return convertValue(annotation.defaultValue(), paramType);
            }
            // 必需输出缺失
            if (annotation.required()) {
                throw new IllegalArgumentException(
                        "Required plugin output '" + outputKey + "' is missing");
            }
            // 可选输出缺失，返回null或基本类型默认值
            return getDefaultValue(paramType);
        }

        return convertValue(value.get(), paramType);
    }

    /**
     * 类型转换
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // 已经是目标类型
        if (targetType.isInstance(value)) {
            return value;
        }

        // 字符串转换
        String strValue = value.toString();

        try {
            if (targetType == String.class) {
                return strValue;
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(strValue);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(strValue);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(strValue);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(strValue);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(strValue);
            } else {
                throw new IllegalArgumentException(
                        "Unsupported parameter type: " + targetType.getName());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Cannot convert value '" + strValue + "' to type " + targetType.getName(), e);
        }
    }

    /**
     * 获取基本类型的默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == int.class) {
            return 0;
        } else if (type == long.class) {
            return 0L;
        } else if (type == double.class) {
            return 0.0;
        } else if (type == float.class) {
            return 0.0f;
        } else if (type == boolean.class) {
            return false;
        } else {
            return null;
        }
    }
}
