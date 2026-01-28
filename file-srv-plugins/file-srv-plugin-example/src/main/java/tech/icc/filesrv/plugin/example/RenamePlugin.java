package tech.icc.filesrv.plugin.example;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 【类型 A: 元数据修改类插件】 - 文件重命名
 * <p>
 * <b>插件类型说明：</b>
 * <p>
 * 元数据修改类插件用于修改 FileReference 的属性（如 filename、contentType 等）。
 * 此类插件不生成新文件，只变更已有文件的元信息。
 * <p>
 * <b>操作逻辑：</b>
 * <ol>
 *   <li>从 TaskContext 读取插件参数（如 rename.pattern）</li>
 *   <li>从 TaskContext 读取当前文件信息（如 filename、fileHash）</li>
 *   <li>执行业务逻辑计算新值</li>
 *   <li>通过 {@code ctx.setMetadata(field, value)} 记录变更</li>
 *   <li>返回 {@link PluginResult.Success#empty()}，无需额外输出</li>
 * </ol>
 * <p>
 * <b>元数据修改 API：</b>
 * <pre>{@code
 * // 通用方法 - 使用常量指定字段
 * ctx.setMetadata(TaskContext.METADATA_FILENAME, newFilename);
 * ctx.setMetadata(TaskContext.METADATA_CONTENT_TYPE, newContentType);
 * }</pre>
 * <p>
 * <b>注意事项：</b>
 * <ul>
 *   <li>元数据变更会在 callback 链全部成功后，由 TaskService 统一应用到 FileReference</li>
 *   <li>若 callback 链中任一插件失败，所有元数据变更都不会生效（事务性）</li>
 * </ul>
 * <p>
 * <b>支持的占位符：</b>
 * <ul>
 *   <li>{@code {filename}} - 原始文件名（不含扩展名）</li>
 *   <li>{@code {ext}} - 文件扩展名（不含点号）</li>
 *   <li>{@code {fullname}} - 完整原始文件名</li>
 *   <li>{@code {uuid}} - 随机 UUID</li>
 *   <li>{@code {hash}} - 文件内容哈希（前 8 位）</li>
 *   <li>{@code {date:FORMAT}} - 日期时间，如 {date:yyyyMMdd}、{date:yyyy/MM/dd}</li>
 * </ul>
 * <p>
 * <b>参数配置示例：</b>
 * <pre>{@code
 * {
 *   "rename.pattern": "{date:yyyy/MM/dd}/{uuid}.{ext}"
 * }
 * }</pre>
 */
public class RenamePlugin implements SharedPlugin {

    private static final String PLUGIN_NAME = "rename";
    private static final String PARAM_PATTERN = "pattern";
    private static final String DEFAULT_PATTERN = "{fullname}";

    /** 日期占位符正则: {date:FORMAT} */
    private static final Pattern DATE_PLACEHOLDER = Pattern.compile("\\{date:([^}]+)}");

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public PluginResult apply(TaskContext ctx) {
        // 1. 获取参数
        String pattern = ctx.getPluginString(PLUGIN_NAME, PARAM_PATTERN)
                .orElse(DEFAULT_PATTERN);

        String originalName = ctx.getString(TaskContext.KEY_FILENAME).orElse("unknown");
        String fileHash = ctx.getString(TaskContext.KEY_FILE_HASH).orElse("");

        // 2. 解析原始文件名
        String baseName = getBaseName(originalName);
        String extension = getExtension(originalName);

        // 3. 执行占位符替换
        String newName = pattern
                .replace("{filename}", baseName)
                .replace("{ext}", extension)
                .replace("{fullname}", originalName)
                .replace("{uuid}", UUID.randomUUID().toString())
                .replace("{hash}", truncateHash(fileHash, 8));

        // 处理日期占位符
        newName = replaceDatePlaceholders(newName);

        // 4. 记录元数据变更（由 TaskService 在 callback 链完成后统一应用）
        ctx.setMetadata(TaskContext.METADATA_FILENAME, newName);

        // 5. 返回成功，无需额外输出
        return PluginResult.Success.empty();
    }

    /**
     * 获取文件基本名（不含扩展名）
     */
    private String getBaseName(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }

    /**
     * 获取文件扩展名（不含点号）
     */
    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 && lastDot < filename.length() - 1
                ? filename.substring(lastDot + 1) : "";
    }

    /**
     * 截取哈希值前 N 位
     */
    private String truncateHash(String hash, int length) {
        return hash.length() >= length ? hash.substring(0, length) : hash;
    }

    /**
     * 替换日期占位符
     * <p>
     * 支持格式: {date:yyyyMMdd}, {date:yyyy/MM/dd}, {date:HHmmss} 等
     */
    private String replaceDatePlaceholders(String pattern) {
        Matcher matcher = DATE_PLACEHOLDER.matcher(pattern);
        StringBuilder result = new StringBuilder();
        LocalDateTime now = LocalDateTime.now();

        while (matcher.find()) {
            String format = matcher.group(1);
            try {
                String formatted = now.format(DateTimeFormatter.ofPattern(format));
                matcher.appendReplacement(result, Matcher.quoteReplacement(formatted));
            } catch (IllegalArgumentException e) {
                // 格式无效，保留原样
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
