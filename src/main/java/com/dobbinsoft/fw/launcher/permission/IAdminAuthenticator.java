package com.dobbinsoft.fw.launcher.permission;

import com.dobbinsoft.fw.core.entiy.inter.PermissionOwner;

public interface IAdminAuthenticator {

    public PermissionOwner getAdmin(String accessToken);

}
