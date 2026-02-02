package tech.icc.filesrv.test.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.icc.filesrv.common.spi.cache.TaskIdValidator;

import java.util.BitSet;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于布隆过滤器的 TaskId 验证器
 * <p>
 * 提供 O(1) 时间复杂度的存在性检查，用于快速过滤不存在的 taskId。
 */
@Service
public class BloomFilterTaskIdValidator implements TaskIdValidator {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterTaskIdValidator.class);

    private final BitSet bitSet;
    private final int bitSize;
    private final int hashCount;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 创建布隆过滤器
     *
     * @param expectedInsertions  预期插入数量
     * @param falsePositiveRate   误判率 (0-1)
     */
    public BloomFilterTaskIdValidator(int expectedInsertions, double falsePositiveRate) {
        // 计算最优 bit 数组大小: m = -n * ln(p) / (ln2)^2
        this.bitSize = optimalBitSize(expectedInsertions, falsePositiveRate);
        // 计算最优哈希函数数量: k = m/n * ln2
        this.hashCount = optimalHashCount(expectedInsertions, bitSize);
        this.bitSet = new BitSet(bitSize);

        log.info("BloomFilterTaskIdValidator initialized: bitSize={}, hashCount={}, expectedInsertions={}, fpp={}",
                bitSize, hashCount, expectedInsertions, falsePositiveRate);
    }

    /**
     * 使用默认配置创建
     */
    public BloomFilterTaskIdValidator() {
        this(1_000_000, 0.01);
    }

    @Override
    public boolean isValidFormat(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        // UUID 格式校验
        try {
            UUID.fromString(taskId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean mightExist(String taskId) {
        if (!isValidFormat(taskId)) {
            return false;
        }

        lock.readLock().lock();
        try {
            int[] hashes = getHashes(taskId);
            for (int hash : hashes) {
                if (!bitSet.get(hash)) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void register(String taskId) {
        if (!isValidFormat(taskId)) {
            return;
        }

        lock.writeLock().lock();
        try {
            int[] hashes = getHashes(taskId);
            for (int hash : hashes) {
                bitSet.set(hash);
            }
            log.debug("TaskId registered to bloom filter: {}", taskId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取当前填充率
     */
    public double getFillRatio() {
        lock.readLock().lock();
        try {
            return (double) bitSet.cardinality() / bitSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ==================== 私有方法 ====================

    private int[] getHashes(String value) {
        int[] hashes = new int[hashCount];
        long hash64 = murmurHash64(value);
        int hash1 = (int) hash64;
        int hash2 = (int) (hash64 >>> 32);

        for (int i = 0; i < hashCount; i++) {
            int combinedHash = hash1 + i * hash2;
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            hashes[i] = combinedHash % bitSize;
        }
        return hashes;
    }

    /**
     * MurmurHash64 实现
     */
    private long murmurHash64(String value) {
        byte[] data = value.getBytes();
        long seed = 0xe17a1465L;
        long m = 0xc6a4a7935bd1e995L;
        int r = 47;

        long h = seed ^ (data.length * m);

        int length8 = data.length / 8;
        for (int i = 0; i < length8; i++) {
            int i8 = i * 8;
            long k = ((long) data[i8] & 0xff)
                    | (((long) data[i8 + 1] & 0xff) << 8)
                    | (((long) data[i8 + 2] & 0xff) << 16)
                    | (((long) data[i8 + 3] & 0xff) << 24)
                    | (((long) data[i8 + 4] & 0xff) << 32)
                    | (((long) data[i8 + 5] & 0xff) << 40)
                    | (((long) data[i8 + 6] & 0xff) << 48)
                    | (((long) data[i8 + 7] & 0xff) << 56);

            k *= m;
            k ^= k >>> r;
            k *= m;

            h ^= k;
            h *= m;
        }

        int remaining = data.length % 8;
        if (remaining > 0) {
            int offset = length8 * 8;
            for (int i = remaining - 1; i >= 0; i--) {
                h ^= ((long) data[offset + i] & 0xff) << (i * 8);
            }
            h *= m;
        }

        h ^= h >>> r;
        h *= m;
        h ^= h >>> r;

        return h;
    }

    private static int optimalBitSize(int n, double p) {
        return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    private static int optimalHashCount(int n, int m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }
}
