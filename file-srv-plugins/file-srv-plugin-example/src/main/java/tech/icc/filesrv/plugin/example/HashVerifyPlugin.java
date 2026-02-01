package tech.icc.filesrv.plugin.example;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.context.TaskContextKeys;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;

/**
 * 【类型 C: 数据传递类插件】 - 哈希校验
 * <p>
 * <b>插件类型说明：</b>
 * <p>
 * 数据传递类插件用于验证文件、提取信息或计算数据，并将结果写入 TaskContext 供后续插件使用。
 * 此类插件不修改元数据，也不生成新文件，仅进行验证或数据提取。
 * <p>
 * <b>典型场景：</b>
 * <ul>
 *   <li>哈希校验 - 验证客户端上传的哈希与服务端计算的哈希是否一致</li>
 *   <li>病毒扫描 - 调用杀毒引擎检查文件安全性</li>
 *   <li>内容识别 - 提取图片 EXIF、视频时长、文档页数等信息</li>
 *   <li>敏感词检测 - 检查文本内容是否包含敏感信息</li>
 * </ul>
 * <p>
 * <b>操作逻辑：</b>
 * <ol>
 *   <li>从 TaskContext 读取插件参数（如 hash-verify.algorithm, hash-verify.expected）</li>
 *   <li>从 TaskContext 获取待验证数据（如 KEY_LOCAL_FILE_PATH、KEY_FILE_HASH）</li>
 *   <li>执行验证或计算逻辑</li>
 *   <li>若验证失败，返回 {@link PluginResult.Failure}，终止 callback 链</li>
 *   <li>若验证成功，通过 {@code ctx.put(key, value)} 写入提取的数据供后续插件使用</li>
 *   <li>返回 {@link PluginResult.Success}，可携带输出数据</li>
 * </ol>
 * <p>
 * <b>数据传递约定：</b>
 * <pre>{@code
 * // 写入数据供后续插件使用
 * ctx.put("hash-verify.result", "PASSED");
 * ctx.put("hash-verify.serverHash", computedHash);
 *
 * // 后续插件可读取
 * ctx.getString("hash-verify.result");
 * }</pre>
 * <p>
 * <b>注意事项：</b>
 * <ul>
 *   <li>验证类插件建议放在 callback 链的前端，尽早发现问题</li>
 *   <li>写入 TaskContext 的 key 建议使用 {@code {pluginName}.{key}} 格式避免冲突</li>
 *   <li>验证失败时应返回明确的错误信息，便于问题排查</li>
 * </ul>
 * <p>
 * <b>参数配置示例：</b>
 * <pre>{@code
 * {
 *   "hash-verify.algorithm": "xxhash64",
 *   "hash-verify.expected": "a1b2c3d4e5f6"
 * }
 * }</pre>
 */
public class HashVerifyPlugin implements SharedPlugin {

    private static final String PLUGIN_NAME = "hash-verify";

    // 参数 Key
    private static final String PARAM_ALGORITHM = "algorithm";
    private static final String PARAM_EXPECTED = "expected";

    // 输出 Key
    private static final String OUTPUT_RESULT = "result";
    private static final String OUTPUT_SERVER_HASH = "serverHash";
    private static final String OUTPUT_CLIENT_HASH = "clientHash";

    // 默认值
    private static final String DEFAULT_ALGORITHM = "xxhash64";

    // 结果常量
    private static final String RESULT_PASSED = "PASSED";
    private static final String RESULT_FAILED = "FAILED";
    private static final String RESULT_SKIPPED = "SKIPPED";

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public PluginResult apply(TaskContext ctx) {
        // 1. 获取参数
        String algorithm = ctx.getPluginString(PLUGIN_NAME, PARAM_ALGORITHM)
                .orElse(DEFAULT_ALGORITHM);
        String expectedHash = ctx.getPluginString(PLUGIN_NAME, PARAM_EXPECTED)
                .orElse(null);

        // 2. 如果没有提供期望哈希值，跳过校验
        if (expectedHash == null || expectedHash.isBlank()) {
            ctx.put(buildOutputKey(OUTPUT_RESULT), RESULT_SKIPPED);
            return PluginResult.Skip.of("No expected hash provided, skipping verification");
        }

        // 3. 获取服务端计算的哈希值
        String serverHash = ctx.getString(TaskContextKeys.FILE_HASH).orElse(null);
        if (serverHash == null || serverHash.isBlank()) {
            return PluginResult.Failure.of("Server hash not available in context");
        }

        // 4. 记录双方哈希值（便于排查）
        ctx.put(buildOutputKey(OUTPUT_SERVER_HASH), serverHash);
        ctx.put(buildOutputKey(OUTPUT_CLIENT_HASH), expectedHash);

        // 5. 执行校验
        boolean matched = compareHash(serverHash, expectedHash, algorithm);

        if (matched) {
            // 校验通过
            ctx.put(buildOutputKey(OUTPUT_RESULT), RESULT_PASSED);
            return PluginResult.Success.empty();
        } else {
            // 校验失败
            ctx.put(buildOutputKey(OUTPUT_RESULT), RESULT_FAILED);
            String errorMsg = String.format(
                    "Hash mismatch! Expected: %s, Server: %s (algorithm: %s)",
                    expectedHash, serverHash, algorithm);
            return PluginResult.Failure.of(errorMsg);
        }
    }

    /**
     * 构建输出 Key
     * <p>
     * 格式: {pluginName}.{key}
     */
    private String buildOutputKey(String key) {
        return PLUGIN_NAME + "." + key;
    }

    /**
     * 比较哈希值
     * <p>
     * 忽略大小写，支持不同算法格式
     *
     * @param serverHash   服务端计算的哈希
     * @param clientHash   客户端提供的哈希
     * @param algorithm    哈希算法（预留，当前未使用）
     * @return 是否匹配
     */
    private boolean compareHash(String serverHash, String clientHash, String algorithm) {
        // 标准化比较：去除空格、统一小写
        String normalizedServer = normalizeHash(serverHash);
        String normalizedClient = normalizeHash(clientHash);
        return normalizedServer.equals(normalizedClient);
    }

    /**
     * 标准化哈希值
     */
    private String normalizeHash(String hash) {
        if (hash == null) {
            return "";
        }
        return hash.trim().toLowerCase();
    }
}
