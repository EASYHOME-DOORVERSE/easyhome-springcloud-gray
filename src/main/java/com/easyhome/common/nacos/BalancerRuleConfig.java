package com.easyhome.common.nacos;

import com.easyhome.common.nacos.ribbon.NacosRule;
import com.netflix.loadbalancer.IRule;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * 灰度负载策略配置
 * @author wangshufeng
 */
@Configuration
public class BalancerRuleConfig {
    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public IRule getRule(){
        return new NacosRule();
    }
}
