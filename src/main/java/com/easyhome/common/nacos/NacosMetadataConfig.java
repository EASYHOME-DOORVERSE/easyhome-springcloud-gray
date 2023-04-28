package com.easyhome.common.nacos;

import com.alibaba.cloud.nacos.ConditionalOnNacosDiscoveryEnabled;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.discovery.NacosWatch;
import com.easyhome.common.utils.GrayUtil;
import com.easyhome.common.utils.GrayscaleConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * 注册服务添加元数据信息
 *
 * @author wangshufeng
 */
@Slf4j
@Configuration
@ConditionalOnNacosDiscoveryEnabled
@AutoConfigureBefore({SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class})
public class NacosMetadataConfig {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = {"spring.cloud.nacos.discovery.watch.enabled"}, matchIfMissing = true)
    public NacosWatch nacosWatch(NacosDiscoveryProperties nacosDiscoveryProperties) {
        String grayFlg = GrayUtil.isGrayPod().toString();
        log.info("注册服务添加元数据：当前实例是否为灰度环境-{}", grayFlg);
        nacosDiscoveryProperties.getMetadata().put(GrayscaleConstant.POD_GRAY, grayFlg);
        if(Objects.equals(grayFlg,GrayscaleConstant.STR_BOOLEAN_TRUE)){
            String groupFlg = GrayUtil.podGroup();
            nacosDiscoveryProperties.getMetadata().put(GrayscaleConstant.GRAY_GROUP, groupFlg);
        }
        return new NacosWatch(nacosDiscoveryProperties);
    }
}
