package com.dobbinsoft.fw.launcher.controller;

import com.dobbinsoft.fw.launcher.manager.ClusterApiManager;
import com.dobbinsoft.fw.support.utils.JacksonUtil;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/info")
public class DocumentController {

    @Autowired
    private ClusterApiManager clusterApiManager;

    @GetMapping("/json")
    public void json(HttpServletResponse response) throws IOException  {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            OpenAPI openAPI = clusterApiManager.generateOpenApiModel();
            String jsonStringWithoutNull = JacksonUtil.toJSONStringWithoutNull(openAPI);
            outputStream.write(jsonStringWithoutNull.getBytes(StandardCharsets.UTF_8));
        }
    }

}
