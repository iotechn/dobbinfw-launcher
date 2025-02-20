package com.dobbinsoft.fw.launcher.permission;

import com.dobbinsoft.fw.core.entiy.inter.PermissionOwner;
import com.dobbinsoft.fw.core.exception.ServiceException;
import reactor.core.publisher.Mono;

public interface IAdminAuthenticator {

    public Mono<? extends PermissionOwner> getAdmin(String accessToken) throws ServiceException;

}
