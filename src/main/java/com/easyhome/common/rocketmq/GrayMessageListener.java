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
