package com.easyhome.common.utils;

/**
 * 灰度发布常量
 * @author wangshufeng
 */
public class GrayscaleConstant {

    public static final String STR_BOOLEAN_TRUE = "true";

    /**
     * 头信息打印日志标识
     */
    public static final String PRINT_HEADER_LOG_KEY = "print_header_log";

    /**
     * http请求头灰度标识参数名
     */
    public static final String  HEADER_KEY = "release-version";

    /**
     * http请求头灰度标识参数值
     */
    public static final String HEADER_VALUE = "grayscale";

    /**
     * useId http请求头标识
     */
    public static final String USER_ID = "User_Id";

    /**
     * 国际化语言
     */
    public static final String DW_LANG = "dw_lang";

    /**
     * 操作
     */
    public static final String DEVICE_OS = "deviceOs";
    /**
     * 是否灰度实例
     */
    public static final String POD_GRAY="pod.gray";

    /**
     * 灰度消息队列后缀
     */
    public static final String GRAY_TOPIC_SUFFIX="_gray";

    /**
     * 默认字符串
     */
    public static final String DEFAULT = "default";

    /**
     * 灰度分组
     */
    public static final String GRAY_GROUP="gray.group";

}
