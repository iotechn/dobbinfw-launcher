package com.dobbinsoft.fw.launcher.controller;

import com.dobbinsoft.fw.launcher.manager.ClusterApiManager;
import com.dobbinsoft.fw.support.utils.JacksonUtil;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/info")
public class DocumentController {

    @Autowired
    private ClusterApiManager clusterApiManager;

    @GetMapping("/json")
    public Mono<ResponseEntity<Object>> json() throws IOException  {
        OpenAPI openAPI = clusterApiManager.generateOpenApiModel();
        String jsonStringWithoutNull = JacksonUtil.toJSONStringWithoutNull(openAPI);
        Map<String, Object> map = JacksonUtil.toMap(jsonStringWithoutNull);
        return Mono.just(ResponseEntity.ok(map));
    }

}
