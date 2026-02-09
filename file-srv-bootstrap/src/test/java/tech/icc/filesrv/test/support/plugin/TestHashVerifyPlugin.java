package tech.icc.filesrv.test.support.plugin;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;
import tech.icc.filesrv.common.spi.plugin.annotation.PluginExecute;
import tech.icc.filesrv.common.spi.plugin.annotation.TaskInfo;

import java.util.Map;

/**
 * 测试用 Hash 验证插件（使用注解注入）
 * <p>
 * 用于集成测试，模拟 hash 验证功能。
 * 始终返回成功，不进行实际验证。
 */
@SharedPlugin("hash-verify")
public class TestHashVerifyPlugin {

    /**
     * 使用注解注入执行方法
     */
    @PluginExecute
    public PluginResult execute(
            @TaskInfo("fileHash") String fileHash,
            @TaskInfo("taskId") String taskId
    ) {
        // 模拟验证逻辑（始终通过）
        return new PluginResult.Success(Map.of(
                "hash-verify.result", "PASSED",
                "hash-verify.verified", "true",
                "hash-verify.hash", fileHash != null ? fileHash : "unknown",
                "message", "Hash verification passed (test mode)"
        ));
    }
}
