package com.dobbinsoft.fw.launcher.inter;


import jakarta.servlet.http.HttpServletResponse;

/**
 * ClassName: AfterHttpMethod
 * Description: 当调用http结束时可以做的一些事情，例如增加cookie等
 */
public interface AfterHttpMethod {

    public void after(HttpServletResponse response, String result);

}
