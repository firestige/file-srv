package tech.icc.filesrv.plugins.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;
import tech.icc.filesrv.common.spi.plugin.inject.*;

import java.io.File;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.util.Map;

/**
 * 哈希验证插件（声明式参数注入示例）
 * <p>
 * 演示了基本的参数注入和任务信息读取。
 * <p>
 * 配置示例：
 * <pre>
 * {
 *   "name": "hash-verify",
 *   "params": [
 *     {"key": "algorithm", "value": "SHA-256"}
 *   ]
 * }
 * </pre>
 */
public class HashVerifyPlugin implements SharedPlugin {
    private static final Logger log = LoggerFactory.getLogger(HashVerifyPlugin.class);

    @Override
    public String name() {
        return "hash-verify";
    }

    /**
     * 声明式参数注入执行方法
     */
    public PluginResult execute(
            // 插件配置参数
            @PluginParam(value = "algorithm", defaultValue = "SHA-256") String algorithm,
            @PluginParam(value = "strict", defaultValue = "true") Boolean strict,
            
            // 本地文件
            @LocalFile File localFile,
            
            // 任务信息
            @TaskInfo(TaskInfo.TaskInfoType.FILE_HASH) String expectedHash,
            @TaskInfo(TaskInfo.TaskInfoType.TASK_ID) String taskId
    ) {
        log.info("Verifying file hash: taskId={}, algorithm={}, strict={}", 
                taskId, algorithm, strict);

        try {
            // 1. 计算文件哈希
            String actualHash = calculateHash(localFile, algorithm);
            
            // 2. 比较哈希值
            boolean matches = actualHash.equalsIgnoreCase(expectedHash);
            
            if (!matches && strict) {
                return PluginResult.failure(
                        "Hash mismatch: expected=" + expectedHash + ", actual=" + actualHash);
            }
            
            log.info("Hash verification result: matches={}, expected={}, actual={}", 
                    matches, expectedHash, actualHash);
            
            // 3. 返回验证结果
            return PluginResult.success(Map.of(
                    "hashVerify.matches", matches,
                    "hashVerify.expected", expectedHash,
                    "hashVerify.actual", actualHash,
                    "hashVerify.algorithm", algorithm
            ));
            
        } catch (Exception e) {
            log.error("Hash verification failed: {}", e.getMessage(), e);
            return PluginResult.failure("Hash calculation failed: " + e.getMessage());
        }
    }

    private String calculateHash(File file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        byte[] hashBytes = digest.digest(fileBytes);
        
        // 转换为十六进制字符串
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
