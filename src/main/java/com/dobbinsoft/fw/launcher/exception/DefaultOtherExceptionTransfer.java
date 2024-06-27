package com.dobbinsoft.fw.launcher.exception;

import com.dobbinsoft.fw.core.exception.CoreExceptionDefinition;
import com.dobbinsoft.fw.core.exception.ServiceException;
import org.springframework.stereotype.Component;

@Component
public class DefaultOtherExceptionTransfer implements OtherExceptionTransfer<Exception> {

    @Override
    public ServiceException trans(Exception e) {
        return new ServiceException(CoreExceptionDefinition.LAUNCHER_UNKNOWN_EXCEPTION);
    }

    @Override
    public Class<?> getExceptionClass() {
        return Exception.class;
    }
}
