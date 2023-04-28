package com.easyhome.common.rocketmq;

import com.aliyun.openservices.ons.api.MessageListener;
import lombok.Data;

/**
 * 订阅规则数据
 * @author wangshufeng
 */
@Data
public class SubscriptionData {
    private String topic;
    private String subExpression;
    private MessageListener listener;

    public SubscriptionData(String topic, String subExpression, MessageListener listener) {
        this.topic = topic;
        this.subExpression = subExpression;
        this.listener = listener;
    }
}
