package com.dobbinsoft.fw.launcher.ws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class WsConfig {


    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public HandlerMapping handlerMapping() {
        WebSocketHandler webSocketHandler = applicationContext.getBean(WebSocketHandler.class);
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws.api", webSocketHandler);
        map.put("/ws.api/{_gp}/{_mt}", webSocketHandler);
        int order = -1; // before annotated controllers
        return new SimpleUrlHandlerMapping(map, order);
    }

}
