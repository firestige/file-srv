package tech.icc.filesrv.core.testdata.stubs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InMemoryRedisStub 测试
 */
@DisplayName("InMemoryRedisStub 测试")
class InMemoryRedisStubTest {

    private InMemoryRedisStub redis;

    @BeforeEach
    void setUp() {
        redis = new InMemoryRedisStub();
    }

    @Test
    @DisplayName("应成功设置和获取值")
    void shouldSetAndGetValue() {
        // When
        redis.set("key", "value");

        // Then
        assertThat(redis.get("key")).isEqualTo("value");
        assertThat(redis.hasKey("key")).isTrue();
    }

    @Test
    @DisplayName("应支持带 TTL 的设置")
    void shouldSetWithTTL() {
        // When
        redis.set("key", "value", Duration.ofSeconds(10));

        // Then
        assertThat(redis.get("key")).isEqualTo("value");
        assertThat(redis.hasKey("key")).isTrue();
    }

    @Test
    @DisplayName("SET NX 应仅在 key 不存在时设置")
    void shouldSetNXOnlyIfNotExists() {
        // Given
        redis.set("key", "value1");

        // When
        boolean result1 = redis.setNX("key", "value2", null);
        boolean result2 = redis.setNX("new-key", "value3", null);

        // Then
        assertThat(result1).isFalse();
        assertThat(redis.get("key")).isEqualTo("value1");
        assertThat(result2).isTrue();
        assertThat(redis.get("new-key")).isEqualTo("value3");
    }

    @Test
    @DisplayName("应成功删除 key")
    void shouldDeleteKey() {
        // Given
        redis.set("key", "value");

        // When
        boolean deleted = redis.delete("key");

        // Then
        assertThat(deleted).isTrue();
        assertThat(redis.hasKey("key")).isFalse();
    }

    @Test
    @DisplayName("应支持计数器递增")
    void shouldIncrementCounter() {
        // When
        long count1 = redis.increment("counter");
        long count2 = redis.increment("counter");
        long count3 = redis.incrementBy("counter", 5);

        // Then
        assertThat(count1).isEqualTo(1);
        assertThat(count2).isEqualTo(2);
        assertThat(count3).isEqualTo(7);
    }

    @Test
    @DisplayName("应支持计数器递减")
    void shouldDecrementCounter() {
        // Given
        redis.set("counter", "10");

        // When
        long count1 = redis.decrement("counter");
        long count2 = redis.decrementBy("counter", 5);

        // Then
        assertThat(count1).isEqualTo(9);
        assertThat(count2).isEqualTo(4);
    }

    @Test
    @DisplayName("应支持 keys 模式匹配")
    void shouldMatchKeysPattern() {
        // Given
        redis.set("user:1", "alice");
        redis.set("user:2", "bob");
        redis.set("order:1", "order-data");

        // When
        var userKeys = redis.keys("user:*");
        var orderKeys = redis.keys("order:*");

        // Then
        assertThat(userKeys).hasSize(2);
        assertThat(orderKeys).hasSize(1);
    }

    @Test
    @DisplayName("测试辅助方法应正常工作")
    void shouldWorkWithHelperMethods() {
        // Given
        redis.set("key1", "value1");
        redis.set("key2", "value2");

        // Then
        assertThat(redis.size()).isEqualTo(2);
        assertThat(redis.verify("key1", "value1")).isTrue();
        assertThat(redis.getAllKeys()).containsExactlyInAnyOrder("key1", "key2");

        // Clear
        redis.clear();
        assertThat(redis.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("StringRedisTemplateStub 应正常工作")
    void shouldWorkWithStringRedisTemplateStub() {
        // Given
        StringRedisTemplateStub template = new StringRedisTemplateStub(redis);

        // When
        template.opsForValue().set("key", "value", Duration.ofMinutes(5));

        // Then
        assertThat(template.hasKey("key")).isTrue();
        assertThat(template.opsForValue().get("key")).isEqualTo("value");

        // Delete
        template.delete("key");
        assertThat(template.hasKey("key")).isFalse();
    }

    @Test
    @DisplayName("StringRedisTemplateStub 应支持 setIfAbsent")
    void shouldSupportSetIfAbsent() {
        // Given
        StringRedisTemplateStub template = new StringRedisTemplateStub(redis);

        // When
        Boolean result1 = template.opsForValue().setIfAbsent("key", "value1");
        Boolean result2 = template.opsForValue().setIfAbsent("key", "value2");

        // Then
        assertThat(result1).isTrue();
        assertThat(result2).isFalse();
        assertThat(template.opsForValue().get("key")).isEqualTo("value1");
    }
}
