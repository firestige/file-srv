package tech.icc.filesrv.adapter.hcs.config;

import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.icc.filesrv.adapter.hcs.HcsObsAdapter;
import tech.icc.filesrv.core.infra.storage.StorageAdapter;

/**
 * OBS 自动配置
 */
@Configuration
@ConditionalOnProperty(prefix = "storage.obs", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ObsProperties.class)
public class ObsAutoConfiguration {

    @Bean
    public ObsClient obsClient(ObsProperties properties) {
        ObsConfiguration config = new ObsConfiguration();
        config.setEndPoint(properties.getEndpoint());
        config.setSocketTimeout(30000);
        config.setConnectionTimeout(10000);
        
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
