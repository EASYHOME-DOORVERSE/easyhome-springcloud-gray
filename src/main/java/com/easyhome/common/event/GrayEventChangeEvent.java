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
