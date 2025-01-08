package com.dobbinsoft.fw.launcher.ws;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

public interface DefaultWebSocketHandler extends WebSocketHandler {

    public default void afterConnectionEstablished(WebSocketSession session) throws Exception {}

    public default void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {}

    public default void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {}

    public default void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {}

    public default boolean supportsPartialMessages() {
        return false;
    }

}
