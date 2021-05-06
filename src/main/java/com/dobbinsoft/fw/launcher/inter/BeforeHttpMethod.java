package com.dobbinsoft.fw.launcher.inter;


import com.dobbinsoft.fw.core.annotation.HttpMethod;
import com.dobbinsoft.fw.core.exception.ServiceException;

import javax.servlet.http.HttpServletRequest;

/**
 * ClassName: BeforeHttpMethod
 * Description: TODO
 *
 * @author: e-weichaozheng
 * @date: 2021-03-29
 */
public interface BeforeHttpMethod {

    public void before(HttpServletRequest request, String _gp, String _mt, HttpMethod httpMethod) throws ServiceException;

}
