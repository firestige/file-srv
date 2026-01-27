package tech.icc.filesrv.adapter.hcs;

import org.junit.jupiter.api.TestInstance;

/**
 * OBS 真实环境的集成测试。需在环境变量中提供连接信息：
 * OBS_ENDPOINT, OBS_AK, OBS_SK, OBS_BUCKET。
 * 未配置时将自动跳过。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HcsObsAdapterIT {

}
