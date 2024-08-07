package com.dobbinsoft.fw.launcher.manager;

import com.dobbinsoft.fw.support.model.PermissionPoint;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ClassName: IApiManager
 * Description: API管理器
 */
public interface IApiManager {

    public Method getMethod(String group, String name);

    public Method getRpcMethod(String group, String name);

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

    public List<PermissionPoint> getPermissions();

}
