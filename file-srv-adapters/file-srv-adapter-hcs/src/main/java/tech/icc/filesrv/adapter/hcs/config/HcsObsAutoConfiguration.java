package tech.icc.filesrv.adapter.hcs.config;

import com.obs.services.ObsClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import tech.icc.filesrv.core.infra.storage.StorageAdapter;
import tech.icc.filesrv.adapter.hcs.HcsObsAdapter;

/**
 * OBS 适配器自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(ObsProperties.class)
@ConditionalOnClass(ObsClient.class)
public class HcsObsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "storage.obs.enabled", havingValue = "true")
    public ObsClient obsClient(ObsProperties properties) {
        Assert.hasText(properties.getEndpoint(), "storage.obs.endpoint must not be empty");
        Assert.hasText(properties.getAccessKey(), "storage.obs.access-key must not be empty");
        Assert.hasText(properties.getSecretKey(), "storage.obs.secret-key must not be empty");
        Assert.hasText(properties.getBucketName(), "storage.obs.bucket-name must not be empty");
        return new ObsClient(properties.getAccessKey(), properties.getSecretKey(), properties.getEndpoint());
    }

    @Bean
    @ConditionalOnMissingBean(StorageAdapter.class)
    @ConditionalOnProperty(name = "storage.obs.enabled", havingValue = "true")
    public StorageAdapter hcsObsAdapter(ObsClient obsClient, ObsProperties properties) {
        return new HcsObsAdapter(properties.getBucketName());
    }
}
