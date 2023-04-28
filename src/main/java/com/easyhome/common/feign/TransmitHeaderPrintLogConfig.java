package com.easyhome.common.feign;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 打印请求头灰度参数拦截器配置
 * @author wangshufeng
 */
@Configuration
public class TransmitHeaderPrintLogConfig implements WebMvcConfigurer {
    /**
     * 配置拦截规则与注入拦截器
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // addPathPattern 添加拦截规则 /** 拦截所有包括静态资源
        // excludePathPattern 排除拦截规则 所以我们需要放开静态资源的拦截
        registry.addInterceptor(new TransmitHeaderPrintLogHanlerInterceptor())
                .addPathPatterns("/**");
    }
}
