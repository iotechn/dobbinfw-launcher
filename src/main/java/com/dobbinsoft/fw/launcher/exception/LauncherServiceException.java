package com.dobbinsoft.fw.launcher.exception;


import com.dobbinsoft.fw.core.exception.ServiceException;
import com.dobbinsoft.fw.core.exception.ServiceExceptionDefinition;

/**
 * Created by rize on 2019/6/30.
 */
public class LauncherServiceException extends ServiceException {

    public LauncherServiceException(String message, int code) {
        super(message, code);
    }

    public LauncherServiceException(ServiceExceptionDefinition exceptionDefinition) {
        super(exceptionDefinition);
    }

}
