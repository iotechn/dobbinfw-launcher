package com.dobbinsoft.fw.launcher.inter;


import com.dobbinsoft.fw.core.annotation.HttpMethod;
import com.dobbinsoft.fw.core.exception.ServiceException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * ClassName: BeforeHttpMethod
 * Description: 在Http接口调用前可以做的事情，例如从Header获取某些上下文等。
 */
public interface BeforeHttpMethod {

    public void before(HttpServletRequest request, String _gp, String _mt, HttpMethod httpMethod) throws ServiceException;

}
