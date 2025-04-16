package com.dobbinsoft.fw.launcher.permission;

import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;
import com.dobbinsoft.fw.core.exception.ServiceException;
import reactor.core.publisher.Mono;

public interface IUserAuthenticator {

    public Mono<IdentityOwner> getUser(String accessToken);

}
