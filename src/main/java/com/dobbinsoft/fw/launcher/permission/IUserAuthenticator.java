package com.dobbinsoft.fw.launcher.permission;

import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;

public interface IUserAuthenticator {

    public IdentityOwner getUser(String accessToken);

}
