/**
 * Callback 执行器核心组件
 * <p>
 * 提供 Kafka 分布式 Callback 执行能力，支持：
 * <ul>
 *   <li>Task 级别负载均衡</li>
 *   <li>节点内本地重试</li>
 *   <li>断点恢复</li>
 *   <li>幂等消费</li>
 * </ul>
 */
package tech.icc.filesrv.core.infra.executor;
