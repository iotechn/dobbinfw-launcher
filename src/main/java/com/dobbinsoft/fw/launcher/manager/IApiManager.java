package com.dobbinsoft.fw.launcher.manager;

import java.lang.reflect.Method;

/**
 * ClassName: IApiManager
 * Description: TODO
 *
 * @author: e-weichaozheng
 * @date: 2021-05-20
 */
public interface IApiManager {

    public Method getMethod(String app, String group, String name);

    public Object getServiceBean(Method method);

}
