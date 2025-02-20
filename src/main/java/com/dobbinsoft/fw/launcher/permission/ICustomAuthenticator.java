package com.dobbinsoft.fw.launcher.permission;

import com.dobbinsoft.fw.core.entiy.inter.CustomAccountOwner;
import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;
import com.dobbinsoft.fw.core.exception.ServiceException;
import reactor.core.publisher.Mono;

public interface ICustomAuthenticator {

    public Mono<? extends CustomAccountOwner> getCustom(Class clazz, String accessToken) throws ServiceException;

}
