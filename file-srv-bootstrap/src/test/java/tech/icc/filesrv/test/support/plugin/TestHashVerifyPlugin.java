package tech.icc.filesrv.test.support.plugin;

import tech.icc.filesrv.common.context.TaskContext;
import tech.icc.filesrv.common.spi.plugin.PluginResult;
import tech.icc.filesrv.common.spi.plugin.SharedPlugin;

/**
 * 测试用 Hash 验证插件
 * <p>
 * 用于集成测试，模拟 hash 验证功能。
 * 始终返回成功，不进行实际验证。
 */
public class TestHashVerifyPlugin implements SharedPlugin {

    @Override
    public String name() {
        return "hash-verify";
    }

    @Override
    public PluginResult apply(TaskContext context) {
        // 模拟验证逻辑（始终通过）
        context.put("hash-verify.result", "PASSED");
        context.put("hash-verify.verified", "true");
        
        return PluginResult.Success.of("message", "Hash verification passed (test mode)");
    }

    @Override
    public int order() {
        return 0; // 最高优先级，应该最先执行
    }
}
