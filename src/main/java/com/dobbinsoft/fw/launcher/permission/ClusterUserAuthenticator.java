package com.dobbinsoft.fw.launcher.permission;

import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;
import com.dobbinsoft.fw.core.exception.ServiceException;
import com.dobbinsoft.fw.core.util.ISessionUtil;
import com.dobbinsoft.fw.support.properties.FwSystemProperties;
import com.dobbinsoft.fw.support.session.SessionStorage;
import com.dobbinsoft.fw.support.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 集群系统用户获取方式
 */
@Component
public class ClusterUserAuthenticator implements IUserAuthenticator {

    @Autowired
    private ISessionUtil sessionUtil;

    @Autowired
    private SessionStorage sessionStorage;

    @Autowired
    private FwSystemProperties fwSystemProperties;

    @Override
    public Mono<IdentityOwner> getUser(String accessToken) {
        if (StringUtils.isEmpty(accessToken)) {
            return null;
        }
        IdentityOwner identityOwner = sessionStorage.get(Const.USER_REDIS_PREFIX, accessToken, sessionUtil.getUserClass());
        if (identityOwner != null) {
            sessionUtil.setUser(identityOwner);
            sessionStorage.renew(Const.USER_REDIS_PREFIX, accessToken, fwSystemProperties.getUserSessionPeriod());
            return Mono.just(identityOwner);
        }
        return Mono.empty();
    }
}
