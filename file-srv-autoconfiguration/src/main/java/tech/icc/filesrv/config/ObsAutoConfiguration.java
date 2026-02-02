package tech.icc.filesrv.config;

import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tech.icc.filesrv.adapter.hcs.HcsObsAdapter;
import tech.icc.filesrv.adapter.hcs.config.ObsProperties;
import tech.icc.filesrv.common.spi.storage.StorageAdapter;

/**
 * OBS 自动配置（装配层）
 */
@AutoConfiguration
@ConditionalOnClass(ObsClient.class)
@ConditionalOnProperty(prefix = "storage.obs", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ObsProperties.class)
public class ObsAutoConfiguration {

    @Bean
    public ObsClient obsClient(ObsProperties properties) {
        ObsConfiguration config = new ObsConfiguration();
        config.setEndPoint(properties.getEndpoint());

        // 超时配置
        ObsProperties.Timeout timeout = properties.getTimeout();
        config.setConnectionTimeout((int) timeout.getConnect().toMillis());
        config.setSocketTimeout((int) timeout.getSocket().toMillis());

        return new ObsClient(
                properties.getAccessKey(),
                properties.getSecretKey(),
                config
        );
    }

    @Bean
    public StorageAdapter hcsObsAdapter(ObsClient obsClient, ObsProperties properties) {
        return new HcsObsAdapter(obsClient, properties.getBucketName(), properties.getPresignedUrlExpiration());
    }
}