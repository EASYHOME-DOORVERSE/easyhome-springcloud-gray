package com.easyhome.common.nacos;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * 配置nacos自定义监听
 * @author wangshufeng
 */
@Configuration
@Slf4j
public class NacosListenerConfig {
    @Resource
    NacosDiscoveryProperties nacosDiscoveryProperties;
    @Resource
    NacosEventListener nacosEventListener;

    @PostConstruct
    public void subscribe() {
        try {
            NamingService namingService = NamingFactory.createNamingService(nacosDiscoveryProperties.getServerAddr());
            namingService.subscribe(nacosDiscoveryProperties.getService(),nacosDiscoveryProperties.getGroup(), nacosEventListener);
            log.info("配置nacos自定义监听完成");
        } catch (NacosException e) {
            log.error("配置nacos自定义监听错误", e);
        }
    }
}
