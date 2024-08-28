package com.dobbinsoft.fw.launcher.permission;

import com.dobbinsoft.fw.core.entiy.inter.PermissionOwner;
import com.dobbinsoft.fw.core.exception.ServiceException;

public interface IAdminAuthenticator {

    public PermissionOwner getAdmin(String accessToken) throws ServiceException;

}
