package tech.icc.filesrv.adapter.hcs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tech.icc.filesrv.adapter.hcs.config.ObsProperties;
import tech.icc.filesrv.adapter.model.FileMetadata;
import com.obs.services.ObsClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OBS 真实环境的集成测试。需在环境变量中提供连接信息：
 * OBS_ENDPOINT, OBS_AK, OBS_SK, OBS_BUCKET。
 * 未配置时将自动跳过。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HcsObsAdapterIT {

    private HcsObsAdapter adapter;
    private ObsClient obsClient;
    private String bucketName;

    @BeforeAll
    void setUp() {
        String endpoint = env("OBS_ENDPOINT");
        String accessKey = env("OBS_AK");
        String secretKey = env("OBS_SK");
        bucketName = env("OBS_BUCKET");

        boolean ready = notBlank(endpoint) && notBlank(accessKey) && notBlank(secretKey) && notBlank(bucketName);
        Assumptions.assumeTrue(ready, "Set OBS_ENDPOINT/OBS_AK/OBS_SK/OBS_BUCKET to run integration test");

        ObsProperties properties = new ObsProperties();
        properties.setEnabled(true);
        properties.setEndpoint(endpoint);
        properties.setAccessKey(accessKey);
        properties.setSecretKey(secretKey);
        properties.setBucketName(bucketName);
        properties.setPresignedUrlExpiration(Duration.ofMinutes(30));

        this.obsClient = new ObsClient(accessKey, secretKey, endpoint);
        this.adapter = new HcsObsAdapter(obsClient, properties);
    }

    @AfterAll
    void tearDown() {
        if (obsClient != null) {
            try {
                obsClient.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    void uploadDownloadDelete_shouldWork() throws Exception {
        String objectKey = "it-" + UUID.randomUUID();
        byte[] data = ("hello-obs-" + objectKey).getBytes();

        FileMetadata meta = FileMetadata.builder()
                .filename(objectKey + ".txt")
                .size((long) data.length)
                .contentType("text/plain")
                .objectKey(objectKey)
                .build();

        String savedKey;
        try (InputStream in = new ByteArrayInputStream(data)) {
            savedKey = adapter.uploadFile(in, meta);
        }

        assertNotNull(savedKey);

        // download
        byte[] downloaded;
        try (InputStream in = adapter.downloadFile(savedKey)) {
            downloaded = in.readAllBytes();
        }
        assertArrayEquals(data, downloaded);

        // presigned url
        String url = adapter.generatePresignedUrl(savedKey, Duration.ofMinutes(5));
        assertTrue(notBlank(url));

        // exists & delete
        assertTrue(adapter.exists(savedKey));
        adapter.deleteFile(savedKey);
        assertFalse(adapter.exists(savedKey));
    }

    private static String env(String key) {
        return System.getenv(key);
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
