package com.easyhome.common.job;

import com.alibaba.schedulerx.worker.domain.JobContext;
import com.alibaba.schedulerx.worker.processor.JobProcessor;
import com.alibaba.schedulerx.worker.processor.ProcessResult;
import com.easyhome.common.feign.GrayParamHolder;
import com.easyhome.common.utils.GrayUtil;
import com.easyhome.common.utils.GrayscaleConstant;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云job基类
 * @author wangshufeng
 */
@Slf4j
public abstract class JavaGrayProcessor implements JobProcessor {
    @Override
    public void preProcess(JobContext context) throws Exception {
        GrayParamHolder.clearValue();
        if (GrayUtil.isGrayPod()) {
            GrayParamHolder.putValue(GrayscaleConstant.HEADER_KEY, GrayscaleConstant.HEADER_VALUE);
            GrayParamHolder.putValue(GrayscaleConstant.PRINT_HEADER_LOG_KEY, GrayscaleConstant.STR_BOOLEAN_TRUE);
            log.info("当前实例是否为灰度环境：true,Job设置传递灰度标识。");
        }
    }

    @Override
    public ProcessResult postProcess(JobContext context) {
        GrayParamHolder.clearValue();
        return null;
    }

    @Override
    public void kill(JobContext context) {

    }
}
