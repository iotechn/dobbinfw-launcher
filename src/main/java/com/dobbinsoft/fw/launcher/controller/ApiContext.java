package com.dobbinsoft.fw.launcher.controller;

import com.dobbinsoft.fw.core.Const;
import com.dobbinsoft.fw.core.annotation.HttpExcel;
import com.dobbinsoft.fw.core.annotation.HttpMethod;
import com.dobbinsoft.fw.core.entiy.inter.CustomAccountOwner;
import com.dobbinsoft.fw.core.entiy.inter.IdentityOwner;
import com.dobbinsoft.fw.core.entiy.inter.PermissionOwner;
import com.dobbinsoft.fw.core.exception.CoreExceptionDefinition;
import com.dobbinsoft.fw.core.exception.ServiceException;
import com.dobbinsoft.fw.support.utils.CollectionUtils;
import com.dobbinsoft.fw.support.utils.JacksonUtil;
import com.dobbinsoft.fw.support.utils.RequestUtils;
import com.dobbinsoft.fw.support.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Getter
@Setter
public class ApiContext {

    Method method;
    String _gp;
    String _mt;
    HttpMethod httpMethod;
    HttpExcel httpExcel;
    private Map<String, String> parameterMap;
    private Map<String, byte[]> fileMap;
    private ServiceException serviceException;
    // WebFlux 中在findContext时，就把当前登录用户给找出来。process时直接使用即可
    private PermissionOwner admin;
    private IdentityOwner user;
    private CustomAccountOwner custom;


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
                        apiContext.setParameterMap(newMap);
                        apiContext.method = method;
                        return apiContext;
                    });
        } else if (StringUtils.isEmpty(contentType)
                || contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
            // GET请求时， contentType为null
            ApiContext apiContext = new ApiContext();
            MultiValueMap<String, String> temp = exchange.getRequest().getQueryParams();
            Map<String, String> paramterSingleMap = new HashMap<>();
            temp.forEach((k, v) -> {
                paramterSingleMap.put(k, v.getFirst());
            });
            apiContext.setParameterMap(paramterSingleMap);
            apiContext.method = method;
            contextMono = Mono.just(apiContext);
        } else if (contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            // 如果是文件上传
            contextMono = exchange.getMultipartData().flatMap(dataMap -> {
                ApiContext apiContext = new ApiContext();
                Set<String> keys = dataMap.keySet();

                // 这里使用Mono来组合所有异步操作
                List<Mono<byte[]>> fileProcessingMonos = new ArrayList<>();

                Map<String, String> paramterSingleMap = new HashMap<>();
                Map<String, byte[]> fileMap = new HashMap<>();
                for (String key : keys) {
                    List<Part> parts = dataMap.get(key);
                    if (CollectionUtils.isEmpty(parts)) {
                        continue;
                    }
                    Part first = parts.getFirst();

                    if (first instanceof FilePart filePart) {
                        String filename = filePart.filename();
                        paramterSingleMap.put(key, filename);

                        // 创建Mono处理文件内容，返回的内容会在后续操作中使用
                        Flux<DataBuffer> contentFlux = filePart.content();
                        Mono<List<DataBuffer>> contentListMono = contentFlux.collectList();
                        Mono<byte[]> fileContentMono = contentListMono.map(dataBuffers -> {
                                    // 合并所有DataBuffer为一个byte[]数组
                                    int totalLength = dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
                                    byte[] content = new byte[totalLength];
                                    int position = 0;

                                    for (DataBuffer buffer : dataBuffers) {
                                        int length = buffer.readableByteCount();
                                        buffer.read(content, position, length);
                                        position += length;
                                    }
                                    return content;
                                });


                        // 将文件处理Mono加入到异步操作的列表中
                        fileProcessingMonos.add(fileContentMono
                                .publishOn(Schedulers.boundedElastic())  // 将subscribe调度到弹性线程池
                                .doOnSuccess(content -> {
                                    // 在文件内容处理完成时更新fileMap
                                    fileMap.put(key, content);
                                }));


                    } else if (first instanceof FormFieldPart formFieldPart){
                        paramterSingleMap.put(key, formFieldPart.value());
                    }
                }

                apiContext.method = method;
                apiContext.setParameterMap(paramterSingleMap);
                apiContext.setFileMap(fileMap);
                // 确保所有的文件处理都完成后再返回apiContext
                return Mono.when(fileProcessingMonos) // 等待所有异步文件处理完成
                        .thenReturn(apiContext);
            });
        }
         else {
            ApiContext apiContext = new ApiContext();
            apiContext.method = method;
            apiContext.setServiceException(new ServiceException(CoreExceptionDefinition.LAUNCHER_CONTENT_TYPE_NOT_SUPPORT));
            contextMono = Mono.just(apiContext);
        }
        return contextMono;
    }

}
