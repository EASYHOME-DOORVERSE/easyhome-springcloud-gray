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







