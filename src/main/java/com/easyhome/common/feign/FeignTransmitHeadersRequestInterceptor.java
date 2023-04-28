package com.easyhome.common.feign;

import com.easyhome.common.utils.GrayscaleConstant;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * feign传递请求头信息拦截器
 *
 * @author wangshufeng
 */
@Slf4j
@Configuration
public class FeignTransmitHeadersRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        Map<String,String> attributes=GrayParamHolder.getGrayMap();
        if (Objects.nonNull(attributes)) {
            //灰度标识传递
            String version = attributes.get(GrayscaleConstant.HEADER_KEY);
            if(!StringUtils.isEmpty(version)){
                requestTemplate.header(GrayscaleConstant.HEADER_KEY, version);
            }
            /** 自定义一些通用参数传递
            String deviceOs = attributes.get(GrayscaleConstant.DEVICE_OS);
            if(!StringUtils.isEmpty(deviceOs)){
                requestTemplate.header(GrayscaleConstant.DEVICE_OS, deviceOs);
            }**/
            String printLogFlg = attributes.get(GrayscaleConstant.PRINT_HEADER_LOG_KEY);
            if (log.isInfoEnabled() && GrayscaleConstant.STR_BOOLEAN_TRUE.equals(printLogFlg)) {
                requestTemplate.header(GrayscaleConstant.PRINT_HEADER_LOG_KEY, printLogFlg);
                log.info("feign传递请求头信息:{}={}", GrayscaleConstant.HEADER_KEY, version);
            }
        }
    }
}
