package com.easyhome.common.feign;

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
 * 打印请求头灰度参数拦截器
 * @author wangshufeng
 */
@Slf4j
public class TransmitHeaderPrintLogHanlerInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String printLogFlg = request.getHeader(GrayscaleConstant.PRINT_HEADER_LOG_KEY);
        if (log.isInfoEnabled() && GrayscaleConstant.STR_BOOLEAN_TRUE.equals(printLogFlg)) {
            Enumeration<String> headerNames = request.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String name = headerNames.nextElement();
                    String value = request.getHeader(name);
                    log.info("接收到的请求头信息:{}={}", name, value);
                }
            }
        }
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
