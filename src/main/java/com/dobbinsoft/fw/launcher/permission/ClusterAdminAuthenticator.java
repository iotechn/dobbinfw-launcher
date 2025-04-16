package com.dobbinsoft.fw.launcher.permission;

import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.entiy.inter.PermissionOwner;
import com.dobbinsoft.fw.core.exception.ServiceException;
import com.dobbinsoft.fw.core.util.ISessionUtil;
import com.dobbinsoft.fw.support.properties.FwSystemProperties;
import com.dobbinsoft.fw.support.session.SessionStorage;
import com.dobbinsoft.fw.support.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ClusterAdminAuthenticator implements IAdminAuthenticator {

    @Autowired
    private ISessionUtil sessionUtil;

    @Autowired
    private SessionStorage sessionStorage;

    @Autowired
    private FwSystemProperties fwSystemProperties;

    @Override
    public Mono<PermissionOwner> getAdmin(String accessToken) {
        if (StringUtils.isEmpty(accessToken)) {
            return null;
        }
        PermissionOwner p = (PermissionOwner) sessionStorage.get(Const.ADMIN_REDIS_PREFIX, accessToken, sessionUtil.getAdminClass());
        if (p != null) {
            sessionUtil.setAdmin(p);
            sessionStorage.renew(Const.ADMIN_REDIS_PREFIX, accessToken, fwSystemProperties.getAdminSessionPeriod());
            return Mono.just(p);
        }
        return Mono.empty();
    }
}
