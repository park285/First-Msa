package com.dietdiary.auth.config;

import com.dietdiary.auth.websocket.handler.AdminNotificationHandler;
import com.dietdiary.auth.websocket.interceptor.AuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    private final AdminNotificationHandler adminNotificationHandler;

    public WebSocketConfig(AuthHandshakeInterceptor authHandshakeInterceptor, AdminNotificationHandler adminNotificationHandler) {
        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.adminNotificationHandler = adminNotificationHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(adminNotificationHandler, "/ws")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}