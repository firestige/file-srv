package tech.icc.filesrv.core.infra.storage.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;
import tech.icc.filesrv.core.infra.storage.StorageAdapterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储适配器注册表实现
 * <p>
 * 在启动时自动注册所有 StorageAdapter Bean。
 */
@Slf4j
@Component
public class StorageAdapterRegistryImpl implements StorageAdapterRegistry {

    private final Map<String, StorageAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * 构造时自动注册所有 StorageAdapter
     */
    public StorageAdapterRegistryImpl(List<StorageAdapter> adapterList) {
        for (StorageAdapter adapter : adapterList) {
            register(adapter.getAdapterType(), adapter);
        }
        // todo 这里用adapterType作为nodeId，但RoutingStorageAdapter却用primary作为key查找，需要统一
        log.info("Registered {} storage adapters: {}", adapters.size(), adapters.keySet());
    }

    @Override
    public StorageAdapter getAdapter(String nodeId) {
        StorageAdapter adapter = adapters.get(nodeId);
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter not found: " + nodeId);
        }
        return adapter;
    }

    @Override
    public void register(String nodeId, StorageAdapter adapter) {
        StorageAdapter existing = adapters.put(nodeId, adapter);
        if (existing != null) {
            log.warn("Replaced adapter for nodeId: {}", nodeId);
        } else {
            log.debug("Registered adapter: {} -> {}", nodeId, adapter.getAdapterType());
        }
    }

    @Override
    public void unregister(String nodeId) {
        StorageAdapter removed = adapters.remove(nodeId);
        if (removed != null) {
            log.debug("Unregistered adapter: {}", nodeId);
        }
    }

    @Override
    public List<String> getRegisteredNodeIds() {
        return new ArrayList<>(adapters.keySet());
    }

    @Override
    public boolean isRegistered(String nodeId) {
        return adapters.containsKey(nodeId);
    }
}
