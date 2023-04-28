package com.easyhome.common.feign;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.easyhome.common.utils.GrayUtil;
import com.easyhome.common.utils.GrayscaleConstant;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 异步线程间参数传递
 *
 * @author wangshufeng
 */
public class GrayParamHolder {

    /**
     * 在Java的启动参数加上：-javaagent:path/to/transmittable-thread-local-2.x.y.jar。
     * <p>
     * 注意：
     * <p>
     * 如果修改了下载的TTL的Jar的文件名（transmittable-thread-local-2.x.y.jar），则需要自己手动通过-Xbootclasspath JVM参数来显式配置。
     * 比如修改文件名成ttl-foo-name-changed.jar，则还需要加上Java的启动参数：-Xbootclasspath/a:path/to/ttl-foo-name-changed.jar。
     * 或使用v2.6.0之前的版本（如v2.5.1），则也需要自己手动通过-Xbootclasspath JVM参数来显式配置（就像TTL之前的版本的做法一样）。
     * 加上Java的启动参数：-Xbootclasspath/a:path/to/transmittable-thread-local-2.5.1.jar。
     */
    private static ThreadLocal<Map<String, String>> paramLocal = new TransmittableThreadLocal();

    /**
     * 获取单个参数值
     *
     * @param key
     * @return
     */
    public static String getValue(String key) {
        Map<String, String> paramMap = GrayParamHolder.paramLocal.get();
        if (Objects.nonNull(paramMap) && !paramMap.isEmpty()) {
            return paramMap.get(key);
        }
        return null;
    }

    /**
     * 获取所有参数
     *
     * @return
     */
    public static Map<String, String> getGrayMap() {
        Map<String, String> paramMap = GrayParamHolder.paramLocal.get();
        if(paramMap==null){
            paramMap=new HashMap<>(8);
            if(GrayUtil.isGrayPod()){
                paramMap.put(GrayscaleConstant.HEADER_KEY, GrayscaleConstant.HEADER_VALUE);
                paramMap.put(GrayscaleConstant.PRINT_HEADER_LOG_KEY, GrayscaleConstant.STR_BOOLEAN_TRUE);
                GrayParamHolder.paramLocal.set(paramMap);
            }
        }
        return paramMap;

    }

    /**
     * 设置单个参数
     *
     * @param key
     * @param value
     */
    public static void putValue(String key, String value) {
        Map<String, String> paramMap = GrayParamHolder.paramLocal.get();
        if (Objects.isNull(paramMap) || paramMap.isEmpty()) {
            paramMap = new HashMap<>(6);
            GrayParamHolder.paramLocal.set(paramMap);
        }
        paramMap.put(key, value);
    }


    /**
     * 设置单多个参数
     *
     * @param map
     */
    public static void putValues(Map<String,String> map) {
        Map<String, String> paramMap = GrayParamHolder.paramLocal.get();
        if (Objects.isNull(paramMap) || paramMap.isEmpty()) {
            paramMap = new HashMap<>(6);
            GrayParamHolder.paramLocal.set(paramMap);
        }
        if(Objects.nonNull(map)&&!map.isEmpty()){
            for (Map.Entry<String,String> item:map.entrySet()){
                paramMap.put(item.getKey(),item.getValue());
            }
        }
    }

    /**
     * 清空线程参数
     */
    public static void clearValue() {
        GrayParamHolder.paramLocal.remove();
    }

}
