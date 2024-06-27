package com.dobbinsoft.fw.launcher.exception;

import com.dobbinsoft.fw.core.exception.ServiceException;

/**
 * 其他异常转换器。
 * 功能：将其他异常，转换为有提示的服务异常
 */
public interface OtherExceptionTransfer<T extends Throwable> {

    /**
     * 转换主体
     * @param e
     * @return
     */
    public ServiceException trans(T e);

    /**
     * 获取异常类
     * @return
     */
    public Class<?> getExceptionClass();

}
