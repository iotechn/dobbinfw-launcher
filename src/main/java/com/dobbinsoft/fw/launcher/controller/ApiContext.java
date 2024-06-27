package com.dobbinsoft.fw.launcher.controller;

import com.dobbinsoft.fw.core.annotation.HttpExcel;
import com.dobbinsoft.fw.core.annotation.HttpMethod;
import com.dobbinsoft.fw.support.utils.JacksonUtil;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Method;
import java.util.Map;

public class ApiContext {

    Method method;
    String _gp;
    String _mt;
    HttpMethod httpMethod;
    HttpExcel httpExcel;
    // 私有化这个两个对象，对外提供代理接口
    @Setter
    @Getter
    private Map<String, String[]> parameterMap;
    @Setter
    @Getter
    private Map<String, String> parameterSingleMap;

    public String requestLogMap() {
        if (parameterMap != null) {
            return JacksonUtil.toJSONString(parameterMap);
        } else if (parameterSingleMap != null) {
            return JacksonUtil.toJSONString(parameterSingleMap);
        }
        return "";
    }

    public String getParameter(String param) {
        if (parameterMap != null) {
            String[] strings = parameterMap.get(param);
            if (strings != null && strings.length > 0) {
                return strings[0];
            }
        } else if (parameterSingleMap != null) {
            return parameterSingleMap.get(param);
        }

        return null;
    }

}
