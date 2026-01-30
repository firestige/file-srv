package tech.icc.filesrv.core.testdata.stubs;

import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

/**
 * StringRedisTemplate Stub 包装器
 * <p>
 * 将 InMemoryRedisStub 包装成符合 Spring RedisTemplate 接口的形式，
 * 用于替代真实的 StringRedisTemplate 进行测试。
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 在测试类中
 * private InMemoryRedisStub redis = new InMemoryRedisStub();
 * private StringRedisTemplateStub redisTemplate = new StringRedisTemplateStub(redis);
 * 
 * // 使用
 * redisTemplate.opsForValue().set("key", "value", Duration.ofMinutes(5));
 * Boolean exists = redisTemplate.hasKey("key");
 * String value = redisTemplate.opsForValue().get("key");
 * }</pre>
 */
public class StringRedisTemplateStub {

    private final InMemoryRedisStub redis;
    private final ValueOperationsStub valueOps;

    public StringRedisTemplateStub(InMemoryRedisStub redis) {
        this.redis = redis;
        this.valueOps = new ValueOperationsStub(redis);
    }

    /**
     * 获取 ValueOperations（模拟 RedisTemplate.opsForValue()）
     */
    public ValueOperations<String, String> opsForValue() {
        return valueOps;
    }

    /**
     * 检查 key 是否存在（模拟 RedisTemplate.hasKey()）
     */
    public Boolean hasKey(String key) {
        return redis.hasKey(key);
    }

    /**
     * 删除 key（模拟 RedisTemplate.delete()）
     */
    public Boolean delete(String key) {
        return redis.delete(key);
    }

    /**
     * 设置过期时间（模拟 RedisTemplate.expire()）
     */
    public Boolean expire(String key, Duration timeout) {
        if (redis.hasKey(key)) {
            redis.expire(key, timeout);
            return true;
        }
        return false;
    }

    /**
     * 获取底层的 InMemoryRedisStub（测试辅助方法）
     */
    public InMemoryRedisStub getRedisStub() {
        return redis;
    }

    /**
     * ValueOperations 实现
     */
    private static class ValueOperationsStub implements ValueOperations<String, String> {
        private final InMemoryRedisStub redis;

        public ValueOperationsStub(InMemoryRedisStub redis) {
            this.redis = redis;
        }

        @Override
        public void set(String key, String value) {
            redis.set(key, value);
        }

        @Override
        public void set(String key, String value, Duration timeout) {
            redis.set(key, value, timeout);
        }

        @Override
        public Boolean setIfAbsent(String key, String value) {
            return redis.setNX(key, value, null);
        }

        @Override
        public Boolean setIfAbsent(String key, String value, Duration timeout) {
            return redis.setNX(key, value, timeout);
        }

        @Override
        public String get(Object key) {
            return redis.get(String.valueOf(key));
        }

        @Override
        public Long increment(String key) {
            return redis.increment(key);
        }

        @Override
        public Long increment(String key, long delta) {
            return redis.incrementBy(key, delta);
        }

        @Override
        public Long decrement(String key) {
            return redis.decrement(key);
        }

        @Override
        public Long decrement(String key, long delta) {
            return redis.decrementBy(key, delta);
        }

        // 以下方法为接口必须实现的方法，但在当前测试场景中不需要
        // 提供简化实现或抛出 UnsupportedOperationException

        @Override
        public void set(String key, String value, long timeout, java.util.concurrent.TimeUnit unit) {
            set(key, value, Duration.ofMillis(unit.toMillis(timeout)));
        }

        @Override
        public Boolean setIfAbsent(String key, String value, long timeout, java.util.concurrent.TimeUnit unit) {
            return setIfAbsent(key, value, Duration.ofMillis(unit.toMillis(timeout)));
        }

        @Override
        public Boolean setIfPresent(String key, String value) {
            if (redis.hasKey(key)) {
                redis.set(key, value);
                return true;
            }
            return false;
        }

        @Override
        public Boolean setIfPresent(String key, String value, Duration timeout) {
            if (redis.hasKey(key)) {
                redis.set(key, value, timeout);
                return true;
            }
            return false;
        }

        @Override
        public Boolean setIfPresent(String key, String value, long timeout, java.util.concurrent.TimeUnit unit) {
            return setIfPresent(key, value, Duration.ofMillis(unit.toMillis(timeout)));
        }

        @Override
        public void multiSet(java.util.Map<? extends String, ? extends String> map) {
            map.forEach(redis::set);
        }

        @Override
        public Boolean multiSetIfAbsent(java.util.Map<? extends String, ? extends String> map) {
            // 简化实现：全部设置或全部不设置
            for (String key : map.keySet()) {
                if (redis.hasKey(key)) {
                    return false;
                }
            }
            map.forEach(redis::set);
            return true;
        }

        @Override
        public String getAndDelete(String key) {
            String value = redis.get(key);
            redis.delete(key);
            return value;
        }

        @Override
        public String getAndExpire(String key, Duration timeout) {
            String value = redis.get(key);
            if (value != null) {
                redis.expire(key, timeout);
            }
            return value;
        }

        @Override
        public String getAndExpire(String key, long timeout, java.util.concurrent.TimeUnit unit) {
            return getAndExpire(key, Duration.ofMillis(unit.toMillis(timeout)));
        }

        @Override
        public String getAndPersist(String key) {
            String value = redis.get(key);
            if (value != null) {
                redis.expire(key, null);
            }
            return value;
        }

        @Override
        public String getAndSet(String key, String value) {
            String oldValue = redis.get(key);
            redis.set(key, value);
            return oldValue;
        }

        @Override
        public java.util.List<String> multiGet(java.util.Collection<String> keys) {
            return keys.stream()
                    .map(redis::get)
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public Double increment(String key, double delta) {
            throw new UnsupportedOperationException("Double increment not supported in stub");
        }

        @Override
        public Double decrement(String key, double delta) {
            throw new UnsupportedOperationException("Double decrement not supported in stub");
        }

        @Override
        public Integer append(String key, String value) {
            String current = redis.get(key);
            String newValue = (current != null ? current : "") + value;
            redis.set(key, newValue);
            return newValue.length();
        }

        @Override
        public String get(String key, long start, long end) {
            String value = redis.get(key);
            if (value == null) return null;
            int len = value.length();
            int s = (int) (start < 0 ? len + start : start);
            int e = (int) (end < 0 ? len + end : end);
            if (s < 0) s = 0;
            if (e >= len) e = len - 1;
            if (s > e) return "";
            return value.substring(s, e + 1);
        }

        @Override
        public void set(String key, String value, long offset) {
            throw new UnsupportedOperationException("Set with offset not supported in stub");
        }

        @Override
        public Long size(String key) {
            String value = redis.get(key);
            return value != null ? (long) value.length() : 0L;
        }

        @Override
        public Boolean setBit(String key, long offset, boolean value) {
            throw new UnsupportedOperationException("Bit operations not supported in stub");
        }

        @Override
        public Boolean getBit(String key, long offset) {
            throw new UnsupportedOperationException("Bit operations not supported in stub");
        }

        @Override
        public Long bitField(String key, org.springframework.data.redis.connection.BitFieldSubCommands subCommands) {
            throw new UnsupportedOperationException("Bit field operations not supported in stub");
        }

        @Override
        public org.springframework.data.redis.core.RedisOperations<String, String> getOperations() {
            throw new UnsupportedOperationException("getOperations not supported in stub");
        }
    }
}
