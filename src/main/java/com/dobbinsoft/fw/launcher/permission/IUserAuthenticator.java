package com.dobbinsoft.fw.launcher.permission;

import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;
import com.dobbinsoft.fw.core.exception.ServiceException;

public interface IUserAuthenticator {

    public IdentityOwner getUser(String accessToken) throws ServiceException;

}
