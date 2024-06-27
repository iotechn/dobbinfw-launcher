package com.dobbinsoft.fw.launcher.exception;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@Component
public class OtherExceptionTransferHolder {


    /**
     * 初始值，与缓存
     */
    private Map<Class, OtherExceptionTransfer> otherExceptionHandlerHolderMap;

    @Bean
    public Map<Class, OtherExceptionTransfer> otherExceptionHandlerHolderMap(List<OtherExceptionTransfer> otherExceptionTransferList) {
        Map<Class, OtherExceptionTransfer> map = otherExceptionTransferList.stream().collect(Collectors.toMap(OtherExceptionTransfer::getExceptionClass, v -> v));
        Map<Class, OtherExceptionTransfer> targetMap = new HashMap<>();
        targetMap.putAll(map);
        this.otherExceptionHandlerHolderMap = targetMap;
        return targetMap;
    }

    public OtherExceptionTransfer getByClass(Class clazz) {
        OtherExceptionTransfer otherExceptionTransfer = otherExceptionHandlerHolderMap.get(clazz);
        if (otherExceptionTransfer == null) {
            // 循环遍历，判断class是否是处理器的子类
            for (OtherExceptionTransfer transfer : otherExceptionHandlerHolderMap.values()) {
                // 若clazz 是 transfer class的子类，则直接用这个，并加入缓存
                if (transfer.getExceptionClass().isAssignableFrom(clazz)) {
                    return putTransfer(clazz, transfer);
                }
            }
            // 使用默认转换器
            return putTransfer(clazz, otherExceptionHandlerHolderMap.get(Exception.class));
        }
        return otherExceptionTransfer;
    }

    private OtherExceptionTransfer putTransfer(Class clazz, OtherExceptionTransfer transfer) {
        // 此处加入缓存可能是有多个线程进入。因为添加缓存属于低频操作，用Concurrent有点亏，所以这里加一把锁
        synchronized (this) {
            // Double Check， 防止另外一个线程已经把此值设置好。
            if (!otherExceptionHandlerHolderMap.containsKey(transfer.getExceptionClass())) {
                otherExceptionHandlerHolderMap.put(clazz, transfer);
            }
            return transfer;
        }
    }


}
