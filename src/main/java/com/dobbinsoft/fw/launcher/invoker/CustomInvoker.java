package com.dobbinsoft.fw.launcher.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 调用者
 * 在ApiController中Method.invoke()时，进行静态代理，从IoC中获取
 */
public interface CustomInvoker {

    public Object invoke(Object object, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException;

}
