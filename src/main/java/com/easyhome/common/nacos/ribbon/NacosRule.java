package com.easyhome.common.nacos.ribbon;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.ribbon.ExtendBalancer;
import com.alibaba.cloud.nacos.ribbon.NacosServer;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.easyhome.common.utils.GrayUtil;
import com.easyhome.common.utils.GrayscaleConstant;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancerRule;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * nacos自定义负载策略
 *
 * @author wangshufeng
 */
@Slf4j
public class NacosRule extends AbstractLoadBalancerRule {

    @Autowired
    private NacosDiscoveryProperties nacosDiscoveryProperties;

    @Override
    public Server choose(Object key) {
        try {
            String clusterName = this.nacosDiscoveryProperties.getClusterName();
            DynamicServerListLoadBalancer loadBalancer = (DynamicServerListLoadBalancer) getLoadBalancer();
            String name = loadBalancer.getName();
            NamingService namingService = nacosDiscoveryProperties.namingServiceInstance();
            List<Instance> instances = namingService.selectInstances(name, true);
            instances = this.getGrayFilterInstances(instances, key);
            if (CollectionUtils.isEmpty(instances)) {
                log.warn("no instance in service {}", name);
                return null;
            }
            List<Instance> instancesToChoose = instances;
            if (StringUtils.isNotBlank(clusterName)) {
                List<Instance> sameClusterInstances = instances.stream()
                        .filter(instance -> Objects.equals(clusterName, instance.getClusterName()))
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(sameClusterInstances)) {
                    instancesToChoose = sameClusterInstances;
                } else {
                    log.warn(
                            "A cross-cluster call occurs，name = {}, clusterName = {}, instance = {}",
                            name, clusterName, instances);
                }
            }

            Instance instance = ExtendBalancer.getHostByRandomWeight2(instancesToChoose);
            return new NacosServer(instance);
        } catch (Exception e) {
            log.warn("NacosRule error", e);
            return null;
        }
    }

    /**
     * 根据当前请求是否为灰度过滤服务实例列表
     *
     * @param instances
     * @return List<Instance>
     */
    private List<Instance> getGrayFilterInstances(List<Instance> instances, Object key) {
        if (CollectionUtils.isEmpty(instances)) {
            return instances;
        } else {
            //是否灰度请求
            Boolean isGrayRequest;
            String grayGroup=GrayscaleConstant.HEADER_VALUE;
            //兼容gateway传值方式，gateway是nio是通过key来做负载实例识别的
            if (Objects.nonNull(key) && !GrayscaleConstant.DEFAULT.equals(key)) {
                isGrayRequest = true;
                if(isGrayRequest){
                    grayGroup=(String)key;
                }
            } else {
                isGrayRequest = GrayUtil.isGrayRequest();
                if(isGrayRequest){
                    grayGroup=GrayUtil.requestGroup();
                }
            }

            List<Instance> prodInstance=new ArrayList<>();
            List<Instance> grayInstance=new ArrayList<>();
            for(Instance item:instances){
                Map<String, String> metadata = item.getMetadata();
                if (metadata.isEmpty() || !GrayscaleConstant.STR_BOOLEAN_TRUE.equals(metadata.get(GrayscaleConstant.POD_GRAY))) {
                    prodInstance.add(item);
                }
                if (isGrayRequest) {
                    if (!metadata.isEmpty() && GrayscaleConstant.STR_BOOLEAN_TRUE.equals(metadata.get(GrayscaleConstant.POD_GRAY))) {
                        if(Objects.equals(grayGroup,metadata.get(GrayscaleConstant.GRAY_GROUP))){
                            grayInstance.add(item);
                        }
                    }
                }
            }
            if(!isGrayRequest||CollectionUtils.isEmpty(grayInstance)){
                return prodInstance;
            }
            return grayInstance;
        }
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {

    }
}
