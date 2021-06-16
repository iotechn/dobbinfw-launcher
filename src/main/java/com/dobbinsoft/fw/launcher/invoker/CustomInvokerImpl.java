package com.dobbinsoft.fw.launcher.invoker;

import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Component
public class CustomInvokerImpl implements CustomInvoker {

    @Override
    public Object invoke(Object object, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        return method.invoke(object, args);
    }

}
