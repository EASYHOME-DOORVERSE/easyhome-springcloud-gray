package com.easyhome.common.feign;

import com.easyhome.common.utils.GrayscaleConstant;

/**
 * 异步线程拷贝灰度灰度环境信息枚举
 * @author wangshufeng
 */

public enum GrayHeaderParam {

    HEADER_KEY(GrayscaleConstant.HEADER_KEY),
    PRINT_HEADER_LOG_KEY(GrayscaleConstant.PRINT_HEADER_LOG_KEY),
    USER_ID(GrayscaleConstant.USER_ID),
    DW_LANG(GrayscaleConstant.DW_LANG),
    DEVICE_OS(GrayscaleConstant.DEVICE_OS);
    private String value;

    GrayHeaderParam(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
