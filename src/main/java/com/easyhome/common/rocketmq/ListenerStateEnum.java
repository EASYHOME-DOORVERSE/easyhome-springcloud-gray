package com.easyhome.common.rocketmq;

import lombok.Getter;

/**
 * 灰度发版队列监听状态
 *
 * @author wangshufeng
 */
@Getter
public enum ListenerStateEnum {
    /**
     * 只监听生产环境队列
     */
    PRODUCTION(0, "只监听生产环境队列"),
    /**
     * 只监听灰度环境队列
     */
    GRAYSCALE(1, "只监听灰度环境队列"),
    /**
     * 同时监听生产和灰度环境队列
     */
    TOGETHER(2, "同时监听生产和灰度环境队列");


    /**
     * key
     */
    private Integer key;
    /**
     * value
     */
    private String value;

    ListenerStateEnum(Integer key, String value) {
        this.key = key;
        this.value = value;
    }

    public static String getValue(Integer key) {
        for (ListenerStateEnum value : values()) {
            if (value.getKey().equals(key)) {
                return value.getValue();
            }
        }
        return null;
    }
}
