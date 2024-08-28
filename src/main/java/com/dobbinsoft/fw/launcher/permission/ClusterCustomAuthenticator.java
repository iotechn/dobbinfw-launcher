package com.dobbinsoft.fw.launcher.permission;

import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.entiy.inter.CustomAccountOwner;
import com.dobbinsoft.fw.core.exception.ServiceException;
import com.dobbinsoft.fw.core.util.ISessionUtil;
import com.dobbinsoft.fw.support.properties.FwSystemProperties;
import com.dobbinsoft.fw.support.session.SessionStorage;
import com.dobbinsoft.fw.support.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClusterCustomAuthenticator implements ICustomAuthenticator {

    @Autowired
    private ISessionUtil sessionUtil;

    @Autowired
    private SessionStorage sessionStorage;

    @Autowired
    private FwSystemProperties fwSystemProperties;

    @Override
    public CustomAccountOwner getCustom(Class clazz, String accessToken) throws ServiceException {
        if (StringUtils.isEmpty(accessToken)) {
            return null;
        }
        String simpleName = clazz.getSimpleName();
        String key = Const.CUSTOM_REDIS_PREFIX + simpleName;
        CustomAccountOwner identityOwner = (CustomAccountOwner)sessionStorage.get(key, accessToken, clazz);
        if (identityOwner != null) {
            sessionUtil.setCustom(identityOwner);
            sessionStorage.renew(key, accessToken, fwSystemProperties.getUserSessionPeriod());
        }
        return identityOwner;
    }
}
