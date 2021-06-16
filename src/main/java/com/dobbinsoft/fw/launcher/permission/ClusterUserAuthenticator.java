package com.dobbinsoft.fw.launcher.permission;

import com.alibaba.fastjson.JSONObject;
import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;
import com.dobbinsoft.fw.core.util.ISessionUtil;
import com.dobbinsoft.fw.support.properties.FwSystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 集群系统用户获取方式
 */
@Component
public class ClusterUserAuthenticator implements IUserAuthenticator {

    @Autowired
    private StringRedisTemplate userRedisTemplate;

    @Autowired
    private ISessionUtil sessionUtil;

    @Autowired
    private FwSystemProperties fwSystemProperties;

    @Override
    public IdentityOwner getUser(String accessToken) {
        if (!StringUtils.isEmpty(accessToken)) {
            String userJson = userRedisTemplate.opsForValue().get(Const.USER_REDIS_PREFIX + accessToken);
            if (!StringUtils.isEmpty(userJson)) {
                IdentityOwner userDTO = (IdentityOwner) JSONObject.parseObject(userJson, sessionUtil.getUserClass());
                sessionUtil.setUser(userDTO);
                userRedisTemplate.expire(Const.USER_REDIS_PREFIX + accessToken, fwSystemProperties.getUserSessionPeriod(), TimeUnit.MINUTES);
                return userDTO;
            }
        }
        return null;
    }
}
