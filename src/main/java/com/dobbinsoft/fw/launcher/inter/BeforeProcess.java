package com.dobbinsoft.fw.launcher.inter;

import com.dobbinsoft.fw.core.exception.ServiceException;

import jakarta.servlet.http.HttpServletRequest;

public interface BeforeProcess {

    public void before(HttpServletRequest request) throws ServiceException;

}
