package com.dobbinsoft.fw.launcher.inter;

import com.dobbinsoft.fw.core.exception.ServiceException;

import javax.servlet.http.HttpServletRequest;

public interface BeforeFileUpload {

    public void before(HttpServletRequest request) throws ServiceException;

}
