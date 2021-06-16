package com.dobbinsoft.fw.launcher.permission;

import com.alibaba.fastjson.JSONObject;
import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.entiy.inter.PermissionOwner;
import com.dobbinsoft.fw.core.util.ISessionUtil;
import com.dobbinsoft.fw.support.properties.FwSystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Component
public class ClusterAdminAuthenticator implements IAdminAuthenticator {

    @Autowired
    private StringRedisTemplate userRedisTemplate;

    @Autowired
    private ISessionUtil sessionUtil;

    @Autowired
    private FwSystemProperties fwSystemProperties;

    @Override
    public PermissionOwner getAdmin(String accessToken) {
        if (!StringUtils.isEmpty(accessToken)) {
            String userJson = userRedisTemplate.opsForValue().get(Const.ADMIN_REDIS_PREFIX + accessToken);
            if (!StringUtils.isEmpty(userJson)) {
                PermissionOwner adminDTO = (PermissionOwner) JSONObject.parseObject(userJson, sessionUtil.getAdminClass());
                sessionUtil.setAdmin(adminDTO);
                userRedisTemplate.expire(Const.ADMIN_REDIS_PREFIX + accessToken, fwSystemProperties.getAdminSessionPeriod(), TimeUnit.MINUTES);
                return adminDTO;
            }
        }
        return null;
    }
}
