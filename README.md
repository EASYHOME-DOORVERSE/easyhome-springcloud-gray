# 一、背景
通过对请求标记分组，实现请求在灰度服务的分组中流转，当微服务链路内无灰度分组对应的下游服务时，用主线分组中对应的微服务提供服务。
## 1、应用场景
### （1）A/B Testing
线上环境实现A/B Testing，期望在生产环境通过内测用户验证无误后再全量发布给所有用户使用。
![image](https://user-images.githubusercontent.com/131850911/235172673-3357fb83-3679-4752-9ba0-1e9ab929dadb.png)
### （2）多版本开发测试调试
多个版本并行开发时，需要为每个版本准备一整套开发环境。如果版本较多，开发环境成本会非常大。分组隔离可以在多版本开发测试时大幅度降低资源成本，并实现开发机加入测试环境完成本地代码调试。
![image](https://user-images.githubusercontent.com/131850911/235172881-d3817c7a-5ba8-43ce-b333-66603c1ebcdb.png)
## 2、需要解决的问题
现有的灰度发布工具可以实现同步调用链路的流量按请求标识在响应的服务分组内流转，但是存在两个异步调用链路问题导致灰度请求无法在灰度环境中流转完毕：
### （1）异步线程
链路中存在异步线程调用下游服务时，请求中灰度分组标识会丢失，导致灰度请求被流转到主线分组中处理，灰度分组无法正常接收异步线程调用的请求；
![image](https://user-images.githubusercontent.com/131850911/235173037-9d34c54c-6878-44a4-ae42-a22e285b7513.png)
### （2）异步消息
当链路中请求产生mq消息后，因灰度分组和主线分组内消息消费方监听同一队列导致消息流转混乱，易出现问题：消息处理逻辑不能兼容、消息丢失（因同一队列在同一订阅组内订阅规则可能不一致）等；
![image](https://user-images.githubusercontent.com/131850911/235173163-03323103-2430-45b5-8e56-7afc7fcc195b.png)
# 二、方案实现
方案实现前提：在项目中使用Nacos，Spring Cloud OpenFeign、Spring Cloud Gateway，RoketMq
## 1自定义SpringMVC拦截器
将http请求中的灰度分组标识写入当前本地线程ThreadLocal中，ThreadLocal采用Alibaba开源的TransmittableThreadLocal增强，解决当前请求中存在异步线程调用下游服务时，请求中灰度分组标识会丢失，导致灰度请求被流转到主线分组中处理的问题。
### （1）拦截器实现
``` 
```package com.easyhome.common.feign;

import com.easyhome.common.utils.GrayscaleConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 请求分组参数拦截器
 * @author wangshufeng
 */
@Slf4j
public class TransmitHeaderPrintLogHanlerInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Map<String,String> param=new HashMap<>(8);
        //获取所有灰度参数值设置到ThreadLocal，以便传值
        for (GrayHeaderParam item:GrayHeaderParam.values()) {
            String hParam = request.getHeader(item.getValue());
            if(!StringUtils.isEmpty(hParam)){
                param.put(item.getValue(), hParam);
            }
        }
        GrayParamHolder.putValues(param);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) throws Exception {
        //清除灰度ThreadLocal
        GrayParamHolder.clearValue();
    }
}
```
### （2）ThreadLocal增强工具类

``` 
```package com.easyhome.common.feign;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.easyhome.common.utils.GrayUtil;
import com.easyhome.common.utils.GrayscaleConstant;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 异步线程间参数传递
 *
 * @author wangshufeng
 */
public class GrayParamHolder {

    /**
     * 在Java的启动参数加上：-javaagent:path/to/transmittable-thread-local-2.x.y.jar。
     * <p>
     * 注意：
     * <p>
     * 如果修改了下载的TTL的Jar的文件名（transmittable-thread-local-2.x.y.jar），则需要自己手动通过-Xbootclasspath JVM参数来显式配置。
     * 比如修改文件名成ttl-foo-name-changed.jar，则还需要加上Java的启动参数：-Xbootclasspath/a:path/to/ttl-foo-name-changed.jar。
     * 或使用v2.6.0之前的版本（如v2.5.1），则也需要自己手动通过-Xbootclasspath JVM参数来显式配置（就像TTL之前的版本的做法一样）。
     * 加上Java的启动参数：-Xbootclasspath/a:path/to/transmittable-thread-local-2.5.1.jar。
     */
    private static ThreadLocal<Map<String, String>> paramLocal = new TransmittableThreadLocal();

    /**
     * 获取单个参数值
     *
     * @param key
     * @return
     */
    public static String getValue(String key) {
        Map<String, String> paramMap = GrayParamHolder.paramLocal.get();
        if (Objects.nonNull(paramMap) && !paramMap.isEmpty()) {
            return paramMap.get(key);
        }
        return null;
    }

    /**
     * 获取所有参数
     *
     * @return
     */
    public static Map<String, String> getGrayMap() {
        Map<String, String> paramMap = GrayParamHolder.paramLocal.get();
        if(paramMap==null){
            paramMap=new HashMap<>(8);
            if(GrayUtil.isGrayPod()){
                paramMap.put(GrayscaleConstant.HEADER_KEY, GrayscaleConstant.HEADER_VALUE);
                paramMap.put(GrayscaleConstant.PRINT_HEADER_LOG_KEY, GrayscaleConstant.STR_BOOLEAN_TRUE);
                GrayParamHolder.paramLocal.set(paramMap);
            }
        }
        return paramMap;

    }

    /**
     * 设置单个参数
     *
     * @param key
     * @param value
     */
    public static void putValue(String key, String value) {
        Map<String, String> paramMap = GrayParamHolder.paramLocal.get();
        if (Objects.isNull(paramMap) || paramMap.isEmpty()) {
            paramMap = new HashMap<>(6);
            GrayParamHolder.paramLocal.set(paramMap);
        }
        paramMap.put(key, value);
    }


    /**
     * 设置单多个参数
     *
     * @param map
     */
    public static void putValues(Map<String,String> map) {
        Map<String, String> paramMap = GrayParamHolder.paramLocal.get();
        if (Objects.isNull(paramMap) || paramMap.isEmpty()) {
            paramMap = new HashMap<>(6);
            GrayParamHolder.paramLocal.set(paramMap);
        }
        if(Objects.nonNull(map)&&!map.isEmpty()){
            for (Map.Entry<String,String> item:map.entrySet()){
                paramMap.put(item.getKey(),item.getValue());
            }
        }
    }

    /**
     * 清空线程参数
     */
    public static void clearValue() {
        GrayParamHolder.paramLocal.remove();
    }

}
```
### （3）启动加载拦截器

``` 
package com.easyhome.common.feign;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 请求分组参数拦截器加载配置
 * @author wangshufeng
 */
@Configuration
public class TransmitHeaderPrintLogConfig implements WebMvcConfigurer {
    /**
     * 配置拦截规则与注入拦截器
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // addPathPattern 添加拦截规则 /** 拦截所有包括静态资源
        // excludePathPattern 排除拦截规则 所以我们需要放开静态资源的拦截
        registry.addInterceptor(new TransmitHeaderPrintLogHanlerInterceptor())
                .addPathPatterns("/**");
    }
}
```

## 2、自定义Feign拦截器
将自定义SpringMVC拦截器中放入ThreadLocal的灰度分组标识传递给下游服务。
``` 
package com.easyhome.common.feign;

import com.easyhome.common.utils.GrayscaleConstant;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * feign传递请求头信息拦截器
 *
 * @author wangshufeng
 */
@Slf4j
@Configuration
public class FeignTransmitHeadersRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        Map<String,String> attributes=GrayParamHolder.getGrayMap();
        if (Objects.nonNull(attributes)) {
            //灰度标识传递
            String version = attributes.get(GrayscaleConstant.HEADER_KEY);
            if(!StringUtils.isEmpty(version)){
                requestTemplate.header(GrayscaleConstant.HEADER_KEY, version);
            }
			//自定义一些在链路中需要一直携带的通用参数
            //userId传递
            String userId = attributes.get(GrayscaleConstant.USER_ID);
            if(!StringUtils.isEmpty(userId)){
                requestTemplate.header(GrayscaleConstant.USER_ID, userId);
            }
            String dwLang = attributes.get(GrayscaleConstant.DW_LANG);
            if(!StringUtils.isEmpty(dwLang)){
                requestTemplate.header(GrayscaleConstant.DW_LANG, dwLang);
            }
            String deviceOs = attributes.get(GrayscaleConstant.DEVICE_OS);
            if(!StringUtils.isEmpty(deviceOs)){
                requestTemplate.header(GrayscaleConstant.DEVICE_OS, deviceOs);
            }
        }
    }
}
```
## 3、自定义负载策略
### （1）负载策略实现
通过请求中的分组标识选择对应分组的服务列表，实现请求在灰度服务的分组中流转，当微服务链路内无对应分组的下游服务存活时，用主线分组中对应的微服务提供服务。
> 基于com.alibaba.cloud.nacos.ribbon.NacosRule重写

``` 
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
```
### （2）启动加载负载策略
``` 
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

```
## 4、注册服务添加元数据信息
在服务启动时向注册中心注册当前服务所在服务分组信息，在自定义负载策略中通过识别服务元数据中服务分组信息进行服务选择。
``` 
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
```
## 5、异步消息处理
采用消息双队列隔离消息的流转，消费方通过识别消息来源队列在调用下游服务时放入服务分组信息，达到链路的正确流转。
消息消费方灰度分组有实例运行情况：
![image](https://user-images.githubusercontent.com/131850911/235173444-81369e75-4da6-4e6b-8514-8c9e76159a15.png)
消息消费方灰度分组实例下线情况：
![image](https://user-images.githubusercontent.com/131850911/235173580-73449ec5-2039-4c2e-9d53-a376342b3f0d.png)
### （1）自定义灰度mq消息监听器
接收灰度队列消息后在当前线程中添加灰度流量分组标识，保证在消息处理逻辑中调用下游服务时请求在对应分组内流转。
``` 
package com.easyhome.common.rocketmq;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.MessageListener;
import com.easyhome.common.feign.GrayParamHolder;
import com.easyhome.common.utils.GrayscaleConstant;
import lombok.extern.slf4j.Slf4j;

/**
 * 灰度mq消息监听器
 * 通过topic后缀判断是否为灰度流量
 * @author wangshufeng
 */
@Slf4j
public final class GrayMessageListener implements MessageListener {

    private MessageListener messageListener;

    public GrayMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    @Override
    public Action consume(Message message, ConsumeContext context) {
        if(message.getTopic().endsWith(GrayscaleConstant.GRAY_TOPIC_SUFFIX)){
            GrayParamHolder.putValue(GrayscaleConstant.HEADER_KEY, GrayscaleConstant.HEADER_VALUE);
            GrayParamHolder.putValue(GrayscaleConstant.PRINT_HEADER_LOG_KEY, GrayscaleConstant.STR_BOOLEAN_TRUE);
            log.info("为当前mq设置传递灰度标识。");
        }
        Action result= messageListener.consume(message,context);
        GrayParamHolder.clearValue();
        return result;
    }
}
```
### （2）自定义spring灰度环境变更事件
``` 
package com.easyhome.common.event;

import com.easyhome.common.rocketmq.ListenerStateEnum;
import org.springframework.context.ApplicationEvent;

/**
 * 灰度环境变更事件
 * @author wangshufeng
 */
public class GrayEventChangeEvent extends ApplicationEvent {
    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param source the object on which the event initially occurred or with
     *               which the event is associated (never {@code null})
     */
    public GrayEventChangeEvent(ListenerStateEnum source) {
        super(source);
    }
}
```
### （3）灰度实例上下线事件处理基础类
定义spring灰度环境变更事件统一处理抽象类，RocketMq消费者继承此抽象类，实现当前服务实例监听spring事件完成正式队列和灰度队列的监听自动切换。
``` 
package com.easyhome.common.rocketmq;

import com.aliyun.openservices.ons.api.Consumer;
import com.aliyun.openservices.ons.api.MessageListener;
import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.PropertyKeyConst;
import com.easyhome.common.event.GrayEventChangeEvent;
import com.easyhome.common.utils.GrayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;


/**
 * 灰度实例上下线事件处理基础类
 *
 * @author wangshufeng
 */
@Slf4j
public abstract class AbstractGrayEventListener implements ApplicationListener<GrayEventChangeEvent> {

    private Consumer consumer;
    private Consumer consumerGray;

    /**
     * 默认订阅tag规则
     */
    private static final String DEFAULT_SUB_EXPRESSION = "*";

    private List<SubscriptionData> subscribes = new ArrayList<>();

    private ListenerStateEnum currentState;

    private Properties mqProperties;

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 初始化消费者实例
     */
    public void initConsumer() {
        if (GrayUtil.isGrayPod()) {
            initConsumerGray();
        } else {
            initConsumerProduction();
        }
    }

    /**
     * 初始化生产消费者实例
     */
    private void initConsumerProduction() {
        if (consumer == null) {
            synchronized (this) {
                if (consumer == null) {
                    if (Objects.isNull(mqProperties)) {
                        throw new NullPointerException("rocketMq配置信息未设置");
                    } else {
                        consumer = ONSFactory.createConsumer(mqProperties);
                        consumer.start();
                    }
                }
            }
        }
    }

    /**
     * 初始化灰度消费者实例
     */
    private void initConsumerGray() {
        if (consumerGray == null) {
            synchronized (this) {
                if (consumerGray == null) {
                    if (Objects.isNull(mqProperties)) {
                        throw new NullPointerException("rocketMq配置信息未设置");
                    } else {
                        Properties grayProperties = new Properties();
                        grayProperties.putAll(mqProperties);
                        grayProperties.setProperty(PropertyKeyConst.GROUP_ID, GrayUtil.topicGrayName(grayProperties.getProperty(PropertyKeyConst.GROUP_ID)));
                        consumerGray = ONSFactory.createConsumer(grayProperties);
                        consumerGray.start();
                    }
                }
            }
        }
    }

    @Override
    public void onApplicationEvent(GrayEventChangeEvent event) {
        ListenerStateEnum listenerStateEnum = (ListenerStateEnum) event.getSource();
        log.info(this.getClass().getName() + "灰度环境变更:" + listenerStateEnum.getValue());
        currentState = listenerStateEnum;
        if (ListenerStateEnum.PRODUCTION.equals(listenerStateEnum)) {
            initConsumerProduction();
            for (SubscriptionData item : subscribes) {
                if (Objects.nonNull(consumer)) {
                    consumer.subscribe(item.getTopic(), item.getSubExpression(), item.getListener());
                }
            }
            shutdownConsumerGray();
        }
        if (ListenerStateEnum.TOGETHER.equals(listenerStateEnum)) {
            initConsumerProduction();
            initConsumerGray();
            for (SubscriptionData item : subscribes) {
                if (Objects.nonNull(consumer)) {
                    consumer.subscribe(item.getTopic(), item.getSubExpression(), item.getListener());
                }
                if (Objects.nonNull(consumerGray)) {
                    consumerGray.subscribe(GrayUtil.topicGrayName(item.getTopic()), item.getSubExpression(), item.getListener());
                }
            }
        }

        if (ListenerStateEnum.GRAYSCALE.equals(listenerStateEnum)) {
            initConsumerGray();
            for (SubscriptionData item : subscribes) {
                if (Objects.nonNull(consumerGray)) {
                    consumerGray.subscribe(GrayUtil.topicGrayName(item.getTopic()), item.getSubExpression(), item.getListener());
                }
            }
            shutdownConsumerProduction();
        }
    }

    /**
     * 添加订阅规则
     *
     * @param topic         主题
     * @param listenerClass 处理消息监听器类名称
     * @return AbstractGrayEventListener
     */
    public AbstractGrayEventListener subscribe(String topic, Class<? extends MessageListener> listenerClass) {
        return this.subscribe(topic, DEFAULT_SUB_EXPRESSION, listenerClass);
    }

    /**
     * 添加订阅规则
     *
     * @param topic         主题
     * @param subExpression 订阅tag规则
     * @param listenerClass 处理消息监听器类名称
     * @return AbstractGrayEventListener
     */
    public AbstractGrayEventListener subscribe(String topic, String subExpression, Class<? extends MessageListener> listenerClass) {
        if (Objects.isNull(listenerClass)) {
            throw new NullPointerException("listenerClass信息未设置");
        }
        MessageListener listener = applicationContext.getBean(listenerClass);
        if (Objects.isNull(listener)) {
            throw new NullPointerException(listenerClass.getName().concat("未找到实例对象"));
        }
        return this.subscribe(topic, subExpression, listener);
    }

    /**
     * 添加订阅规则
     *
     * @param topic    主题
     * @param listener 处理消息监听器
     * @return AbstractGrayEventListener
     */
    public AbstractGrayEventListener subscribe(String topic, MessageListener listener) {
        return this.subscribe(topic, DEFAULT_SUB_EXPRESSION, listener);
    }

    /**
     * 添加订阅规则
     *
     * @param topic         主题
     * @param subExpression 订阅tag规则
     * @param listener      处理消息监听器
     * @return AbstractGrayEventListener
     */
    public AbstractGrayEventListener subscribe(String topic, String subExpression, MessageListener listener) {
        if (StringUtils.isEmpty(topic)) {
            throw new NullPointerException("topic信息未设置");
        }
        if (StringUtils.isEmpty(subExpression)) {
            throw new NullPointerException("subExpression信息未设置");
        }
        if (Objects.isNull(listener)) {
            throw new NullPointerException("listener信息未设置");
        }
        if (listener instanceof GrayMessageListener) {
            subscribes.add(new SubscriptionData(topic, subExpression, listener));
        } else {
            subscribes.add(new SubscriptionData(topic, subExpression, new GrayMessageListener(listener)));
        }
        return this;
    }

    /**
     * 设置RoketMq配置属性
     *
     * @param mqProperties 配置属性
     * @return AbstractGrayEventListener
     */
    public AbstractGrayEventListener setMqProperties(Properties mqProperties) {
        this.mqProperties = mqProperties;
        return this;
    }

    /**
     * 销毁方法
     */
    @PreDestroy
    public void shutdown() {
        shutdownConsumerProduction();
        shutdownConsumerGray();
    }

    /**
     * 销毁生产消费实例
     */
    private void shutdownConsumerProduction() {
        if (Objects.nonNull(consumer)) {
            consumer.shutdown();
            consumer = null;
        }
    }

    /**
     * 销毁灰度消费者实例
     */
    private void shutdownConsumerGray() {
        if (Objects.nonNull(consumerGray)) {
            consumerGray.shutdown();
            consumerGray = null;
        }
    }
}
```
### （4）nacos注册中心服务列表变更事件监听器实现
监听nacos注册中心服务列表发生变化的事件，识别当前实例需要监听的消息队列的类型，发出spring灰度环境变更事件通知所有mq消费者完成监听队列切换。
``` 
package com.easyhome.common.nacos;

import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.easyhome.common.event.GrayEventChangeEvent;
import com.easyhome.common.rocketmq.ListenerStateEnum;
import com.easyhome.common.utils.GrayUtil;
import com.easyhome.common.utils.GrayscaleConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * nacos自定义监听实现
 *
 * @author wangshufeng
 */
@Slf4j
@Component
public class NacosEventListener implements EventListener {

    @Resource
    private ApplicationEventPublisher publisher;

    @Override
    public void onEvent(Event event) {
        if (event instanceof NamingEvent) {
            this.mqInit(((NamingEvent) event).getInstances());
        }
    }

    /**
     * 当前的mq监听状态
     */
    private static ListenerStateEnum listenerMqState;

    public synchronized void mqInit(List<Instance> instances) {
        ListenerStateEnum newState;
        //当前实例是灰度实例
        if (GrayUtil.isGrayPod()) {
            newState = ListenerStateEnum.GRAYSCALE;
        } else {
            //判断当前服务有灰度实例
            if (this.isHaveGray(instances)) {
                newState = ListenerStateEnum.PRODUCTION;
            } else {
                newState = ListenerStateEnum.TOGETHER;
            }
        }
        log.info("当前实例是否为灰度环境：{}", GrayUtil.isGrayPod());
        log.info("当前实例监听mq队列的状态:{}", newState.getValue());
        //防止重复初始化监听mq队列信息
        if (!newState.equals(listenerMqState)) {
            listenerMqState = newState;
            publisher.publishEvent(new GrayEventChangeEvent(listenerMqState));
        }
    }

    /**
     * 是否有灰度实例
     *
     * @return
     */
    private boolean isHaveGray(List<Instance> instances) {
        if (!CollectionUtils.isEmpty(instances)) {
            for (Instance instance : instances) {
                if (GrayscaleConstant.STR_BOOLEAN_TRUE.equals(instance.getMetadata().get(GrayscaleConstant.POD_GRAY))) {
                    return true;
                }
            }
        }
        return false;
    }
}
```
### （5）加载nacos自定义监听器
``` 
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
```
# 三、使用方法
## 1、项目中引入easyhome-common-gray.jar
```
<dependency>
    <groupId>com.easyhome</groupId>
    <artifactId>easyhome-common-gray</artifactId>
    <version>1.0.2-RELEASE</version>
</dependency>
```
## 2、 SpringBoot启动类上添加扫描类路径
```
@SpringBootApplication(scanBasePackages = {"com.easyhome.*" })
```
## 3、 定义RocketMq消费者时，继承AbstractGrayEventListener，示例代码如下
``` 
/**
 * 商品事件消费
 * @author wangshufeng
 */
@Component
@Slf4j
public class GoodsChangeEventConsumer extends AbstractGrayEventListener {

    @Resource
    private MqGoodsConfig mqConfig;

    @Resource
    private MqMarketingConfig mqMarketingConfig;

    /**
     * 消息订阅
     */
    @PostConstruct
    public void consume() {
        this.subscribe(mqConfig.getGoodsEventTopic(), "*", GoodsChangeMessageListener.class)
        .subscribe(mqConfig.getShopEventTopic(), "*", ShopChangeMessageListener.class)
        .subscribe(this.mqMarketingConfig.getChangeTopic(), this.mqMarketingConfig.getChangeTag(), MarketingChangeMessageListener.class)
        .subscribe(mqConfig.getCategoryEventTopic(),"*", CategoryChangeMessageListener.class)
        .setMqProperties(mqConfig.getGoodsEventMsgMqProperties()).initConsumer();
    }
}
```
## 4、jvm 启动参数添加如下
-Dpod.gray值为false时，启动服务实例为主线分组实例，-Dgray.group无需设置；-Dpod.gray值为true时，启动服务实例为灰度分组实例，-Dgray.group需设置当前服务实例所属分组
``` 
-javaagent:/home/easyhome/transmittable-thread-local-2.13.2.jar
-Dpod.gray=true -Dgray.group=自定义分组名称
```

# 四、存在问题
目前消息只支持主线队列和灰度队列两种队列，多灰度组时灰度消息没有分组隔离，后续版本解决。
