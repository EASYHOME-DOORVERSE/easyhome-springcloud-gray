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
