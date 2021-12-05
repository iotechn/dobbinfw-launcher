package com.dobbinsoft.fw.launcher.inter;


import javax.servlet.http.HttpServletResponse;

/**
 * ClassName: BeforeHttpMethod
 * Description: TODO
 *
 * @author: e-weichaozheng
 * @date: 2021-03-29
 */
public interface AfterHttpMethod {

    public void after(HttpServletResponse response, String result);

}
