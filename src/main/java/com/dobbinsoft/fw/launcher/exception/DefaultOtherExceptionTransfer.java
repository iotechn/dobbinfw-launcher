package com.dobbinsoft.fw.launcher.exception;

import com.dobbinsoft.fw.core.exception.ServiceException;
import org.springframework.stereotype.Component;

@Component
public class DefaultOtherExceptionTransfer implements OtherExceptionTransfer<Exception> {

    @Override
    public ServiceException trans(Exception e) {
        return new LauncherServiceException(LauncherExceptionDefinition.LAUNCHER_UNKNOWN_EXCEPTION);
    }

    @Override
    public Class getExceptionClass() {
        return Exception.class;
    }
}
