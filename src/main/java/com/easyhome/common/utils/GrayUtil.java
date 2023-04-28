package com.easyhome.common.utils;

import com.easyhome.common.feign.GrayParamHolder;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * 灰度发布工具类
 *
 * @author wangshufeng
 */
public class GrayUtil {
    /**
     * 主题名称拼接灰度后缀
     *
     * @param topicName
     * @return String
     */
    public static String topicGrayName(String topicName) {
        if (StringUtils.isEmpty(topicName)) {
            throw new NullPointerException("topicName为null");
        }
        return topicName.concat(GrayscaleConstant.GRAY_TOPIC_SUFFIX);
    }

    /**
     * 自动主题名称拼接灰度后缀
     * @param topicName
     * @return String
     */
    public static String autoTopicGrayName(String topicName) {
        if (isGrayRequest()) {
            return topicGrayName(topicName);
        } else {
            return topicName;
        }
    }

    /**
     * 是否为灰度请求
     * @return Boolean
     */
    public static Boolean isGrayRequest(){
        Map<String,String> attributes= GrayParamHolder.getGrayMap();
        String releaseVersion=attributes.get(GrayscaleConstant.HEADER_KEY);
        if (Objects.nonNull(releaseVersion)&&!"".equals(releaseVersion)) {
            return true;
        }
        return false;
    }

    /**
     * 当前环境是否为灰度环境
     *
     * @return boolean
     */
    public static Boolean isGrayPod() {
        String grayFlg = System.getProperty(GrayscaleConstant.POD_GRAY);
        if (GrayscaleConstant.STR_BOOLEAN_TRUE.equals(grayFlg)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 获取运行实例的灰度分组
     * @return
     */
    public static String podGroup() {
        String groupFlag = System.getProperty(GrayscaleConstant.GRAY_GROUP);
        if (groupFlag!=null&&!"".equals(groupFlag)) {
            return groupFlag;
        } else {
            return GrayscaleConstant.HEADER_VALUE;
        }
    }

    /**
     * 获取当前请求分组
     * @return
     */
    public static String requestGroup(){
        Map<String,String> attributes= GrayParamHolder.getGrayMap();
        String groupFlag =attributes.get(GrayscaleConstant.HEADER_KEY);
        if (groupFlag!=null&&!"".equals(groupFlag)) {
            return groupFlag;
        } else {
            return GrayscaleConstant.HEADER_VALUE;
        }
    }


}
