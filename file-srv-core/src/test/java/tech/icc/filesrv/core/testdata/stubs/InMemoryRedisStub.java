package tech.icc.filesrv.core.testdata.stubs;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Redis Stub
 * <p>
 * 用于单元测试，完全在内存中模拟 Redis 操作，无需真实的 Redis 服务。
 * <p>
 * 特性：
 * <ul>
 *   <li>支持 String 类型的 get/set/delete 操作</li>
 *   <li>支持 TTL（自动过期）</li>
 *   <li>支持 hasKey/exists 检查</li>
 *   <li>支持 increment/decrement 计数器</li>
 *   <li>提供测试辅助方法（验证、清空、查看内容）</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * InMemoryRedisStub redis = new InMemoryRedisStub();
 * 
 * // 设置值
 * redis.set("key", "value");
 * redis.set("key-with-ttl", "value", Duration.ofMinutes(5));
 * 
 * // 获取值
 * String value = redis.get("key");
 * 
 * // 检查存在
 * boolean exists = redis.hasKey("key");
 * 
 * // 计数器
 * long count = redis.increment("counter");
 * 
 * // 测试完成后清空
 * redis.clear();
 * }</pre>
 */
public class InMemoryRedisStub {

    private final Map<String, RedisValue> storage = new ConcurrentHashMap<>();

    /**
     * 设置值（无过期时间）
     */
    public void set(String key, String value) {
        storage.put(key, new RedisValue(value, null));
    }

    /**
     * 设置值（带过期时间）
     */
    public void set(String key, String value, Duration ttl) {
        Instant expiresAt = ttl != null ? Instant.now().plus(ttl) : null;
        storage.put(key, new RedisValue(value, expiresAt));
    }

    /**
     * SET NX - 仅当 key 不存在时设置
     *
     * @return true 如果设置成功，false 如果 key 已存在
     */
    public boolean setNX(String key, String value, Duration ttl) {
        if (hasKey(key)) {
            return false;
        }
        set(key, value, ttl);
        return true;
    }

    /**
     * 获取值
     *
     * @return 值，如果 key 不存在或已过期则返回 null
     */
    public String get(String key) {
        RedisValue value = storage.get(key);
        if (value == null) {
            return null;
        }
        if (value.isExpired()) {
            storage.remove(key);
            return null;
        }
        return value.value;
    }

    /**
     * 删除 key
     *
     * @return true 如果删除成功，false 如果 key 不存在
     */
    public boolean delete(String key) {
        return storage.remove(key) != null;
    }

    /**
     * 检查 key 是否存在
     */
    public boolean hasKey(String key) {
        RedisValue value = storage.get(key);
        if (value == null) {
            return false;
        }
        if (value.isExpired()) {
            storage.remove(key);
            return false;
        }
        return true;
    }

    /**
     * 检查 key 是否存在（exists 别名）
     */
    public boolean exists(String key) {
        return hasKey(key);
    }

    /**
     * 递增计数器
     *
     * @return 递增后的值
     */
    public long increment(String key) {
        return incrementBy(key, 1L);
    }

    /**
     * 按指定值递增
     *
     * @return 递增后的值
     */
    public long incrementBy(String key, long delta) {
        String currentValue = get(key);
        long newValue = (currentValue != null ? Long.parseLong(currentValue) : 0L) + delta;
        set(key, String.valueOf(newValue));
        return newValue;
    }

    /**
     * 递减计数器
     *
     * @return 递减后的值
     */
    public long decrement(String key) {
        return decrementBy(key, 1L);
    }

    /**
     * 按指定值递减
     *
     * @return 递减后的值
     */
    public long decrementBy(String key, long delta) {
        return incrementBy(key, -delta);
    }

    /**
     * 设置过期时间
     */
    public void expire(String key, Duration ttl) {
        RedisValue value = storage.get(key);
        if (value != null) {
            Instant expiresAt = ttl != null ? Instant.now().plus(ttl) : null;
            storage.put(key, new RedisValue(value.value, expiresAt));
        }
    }

    /**
     * 获取剩余 TTL
     *
     * @return 剩余时间，如果没有过期时间返回 null，如果 key 不存在返回 Duration.ZERO
     */
    public Duration getTTL(String key) {
        RedisValue value = storage.get(key);
        if (value == null) {
            return Duration.ZERO;
        }
        if (value.expiresAt == null) {
            return null; // 没有设置过期时间
        }
        if (value.isExpired()) {
            storage.remove(key);
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), value.expiresAt);
    }

    /**
     * 获取所有匹配的 key
     */
    public Set<String> keys(String pattern) {
        // 简化实现：支持 * 通配符
        String regex = pattern.replace("*", ".*");
        return storage.keySet().stream()
                .filter(key -> key.matches(regex))
                .filter(this::hasKey) // 过滤掉已过期的
                .collect(java.util.stream.Collectors.toSet());
    }

    // ==================== 测试辅助方法 ====================

    /**
     * 获取存储的 key 数量（不包括已过期的）
     */
    public int size() {
        cleanExpired();
        return storage.size();
    }

    /**
     * 清空所有数据
     */
    public void clear() {
        storage.clear();
    }

    /**
     * 验证 key 存在且值匹配
     */
    public boolean verify(String key, String expectedValue) {
        String actualValue = get(key);
        return expectedValue.equals(actualValue);
    }

    /**
     * 获取所有 key
     */
    public Set<String> getAllKeys() {
        cleanExpired();
        return storage.keySet();
    }

    /**
     * 清理已过期的 key
     */
    public void cleanExpired() {
        storage.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 模拟时间流逝（用于测试 TTL）
     */
    public void advanceTime(Duration duration) {
        // 注意：这个方法只是辅助测试，实际上我们通过 isExpired() 来判断
        // 在真实场景中，我们依赖系统时间，这里只是提供一个概念性的方法
        cleanExpired();
    }

    // ==================== 内部类 ====================

    /**
     * Redis 值对象（包含值和过期时间）
     */
    private static class RedisValue {
        final String value;
        final Instant expiresAt;

        RedisValue(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }
}
