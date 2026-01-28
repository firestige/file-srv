package tech.icc.filesrv.core.domain.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.icc.filesrv.core.domain.services.StorageRoutingService;
import tech.icc.filesrv.core.domain.storage.StorageNode;
import tech.icc.filesrv.core.domain.storage.StorageNodeRepository;
import tech.icc.filesrv.core.domain.storage.StoragePolicy;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;
import tech.icc.filesrv.core.infra.storage.StorageAdapterRegistry;

import java.util.Map;

/**
 * 存储路由服务实现
 * <p>
 * Phase 1: 固定返回 primary 节点，简单路径生成策略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageRoutingServiceImpl implements StorageRoutingService {

    private final StorageNodeRepository storageNodeRepository;
    private final StorageAdapterRegistry adapterRegistry;

    /**
     * MIME 类型到文件扩展名的映射
     */
    private static final Map<String, String> MIME_TO_EXT = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/gif", "gif"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/svg+xml", "svg"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/json", "json"),
            Map.entry("application/xml", "xml"),
            Map.entry("text/plain", "txt"),
            Map.entry("text/html", "html"),
            Map.entry("text/css", "css"),
            Map.entry("text/javascript", "js"),
            Map.entry("application/zip", "zip"),
            Map.entry("application/gzip", "gz"),
            Map.entry("video/mp4", "mp4"),
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
    );

    @Override
    public StorageNode selectNode(StoragePolicy policy) {
        // Phase 1: 固定返回 primary 节点
        // TODO Phase 2: 根据策略选择最优节点（考虑 tier、负载均衡等）
        return storageNodeRepository.findByNodeId(StorageNode.PRIMARY_NODE_ID)
                .orElseGet(() -> {
                    log.warn("Primary node not found, creating default");
                    return StorageNode.primaryNode("default-adapter", "localhost", "default-bucket");
                });
    }

    @Override
    public StorageAdapter getAdapter(String nodeId) {
        return adapterRegistry.getAdapter(nodeId);
    }

    @Override
    public String buildStoragePath(String contentHash, String contentType) {
        // 路径格式: {hash前2位}/{hash前4位}/{hash}.{extension}
        // 例如: ab/abcd/abcd1234567890.png
        //
        // 这种结构便于：
        // 1. 分散存储（避免单目录文件过多）
        // 2. 快速定位文件
        // 3. 便于按前缀清理

        if (contentHash == null || contentHash.length() < 4) {
            throw new IllegalArgumentException("Invalid content hash: " + contentHash);
        }

        String prefix2 = contentHash.substring(0, 2);
        String prefix4 = contentHash.substring(0, 4);
        String extension = getExtension(contentType);

        return String.format("%s/%s/%s%s", prefix2, prefix4, contentHash, extension);
    }

    /**
     * 根据 MIME 类型获取文件扩展名
     */
    private String getExtension(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }

        // 移除参数部分 (如 text/plain; charset=utf-8)
        String mimeType = contentType.split(";")[0].trim().toLowerCase();

        String ext = MIME_TO_EXT.get(mimeType);
        if (ext != null) {
            return "." + ext;
        }

        // 尝试从 MIME 类型提取（如 image/png -> png）
        int slashIndex = mimeType.lastIndexOf('/');
        if (slashIndex > 0 && slashIndex < mimeType.length() - 1) {
            String subtype = mimeType.substring(slashIndex + 1);
            // 过滤特殊字符
            if (subtype.matches("[a-z0-9]+")) {
                return "." + subtype;
            }
        }

        return "";
    }
}
