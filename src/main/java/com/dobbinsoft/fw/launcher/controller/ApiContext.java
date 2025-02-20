package com.dobbinsoft.fw.launcher.controller;

import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.annotation.HttpExcel;
import com.dobbinsoft.fw.core.annotation.HttpMethod;
import com.dobbinsoft.fw.core.entiy.inter.CustomAccountOwner;
import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;
import com.dobbinsoft.fw.core.entiy.inter.PermissionOwner;
import com.dobbinsoft.fw.core.exception.CoreExceptionDefinition;
import com.dobbinsoft.fw.core.exception.ServiceException;
import com.dobbinsoft.fw.support.utils.JacksonUtil;
import com.dobbinsoft.fw.support.utils.RequestUtils;
import com.dobbinsoft.fw.support.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

    @Getter
    @Setter
    private ServiceException serviceException;

    // WebFlux 中在findContext时，就把当前登录用户给找出来。process时直接使用即可

    @Getter
    @Setter
    private PermissionOwner admin;

    @Getter
    @Setter
    private IdentityOwner user;

    @Getter
    @Setter
    private CustomAccountOwner custom;

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


    public static Mono<ApiContext> getApiContextMono(ServerWebExchange exchange, HttpHeaders headers, Method method) {
        Mono<ApiContext> contextMono;
        // 判断请求类型
        String contentType = RequestUtils.getHeaderValue(headers, HttpHeaders.CONTENT_TYPE);
        if (MediaType.APPLICATION_JSON_VALUE.equals(contentType)) {
            contextMono = exchange.getRequest().getBody()
                    .reduce(DataBuffer::write)
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        ApiContext apiContext = new ApiContext();
                        Map<String, Object> map = JacksonUtil.toMap(new String(bytes, StandardCharsets.UTF_8), String.class, Object.class);
                        Map<String, String> newMap = new HashMap<>();
                        if (map != null) {
                            // 支持Post空传参
                            map.forEach((k, v) -> {
                                if (v == null) {
                                    return;
                                }
                                if (Const.IGNORE_PARAM_LIST.contains(v.getClass())) {
                                    newMap.put(k, v.toString());
                                } else {
                                    newMap.put(k, JacksonUtil.toJSONString(v));
                                }
                            });
                        }
                        apiContext.setParameterSingleMap(newMap);
                        apiContext.method = method;
                        return apiContext;
                    });
        } else if (StringUtils.isEmpty(contentType)
                || contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                || contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            // GET请求时， contentType为null
            ApiContext apiContext = new ApiContext();
            MultiValueMap<String, String> temp = exchange.getRequest().getQueryParams();
            Map<String, String> paramterSingleMap = new HashMap<>();
            temp.forEach((k, v) -> {
                paramterSingleMap.put(k, v.getFirst());
            });
            apiContext.setParameterSingleMap(paramterSingleMap);
            apiContext.method = method;
            contextMono = Mono.just(apiContext);
        } else {
            ApiContext apiContext = new ApiContext();
            apiContext.method = method;
            apiContext.setServiceException(new ServiceException(CoreExceptionDefinition.LAUNCHER_CONTENT_TYPE_NOT_SUPPORT));
            contextMono = Mono.just(apiContext);
        }
        return contextMono;
    }

}
