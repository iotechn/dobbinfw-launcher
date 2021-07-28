package com.dobbinsoft.fw.launcher.manager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

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

    public Set<Class> getRegisteredInterfaces();

    /**
     * 传入权限点，返回文字路径
     * eg:
     * input:
     * sys:user:list
     * output:
     * 系统管理 - 用户管理 - 列表
     * @param permission
     * @return
     */
    public String getPermissionRoute(String permission);

}
