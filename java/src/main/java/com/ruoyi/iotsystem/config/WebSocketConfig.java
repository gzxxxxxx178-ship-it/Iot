package com.ruoyi.iotsystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SensorWebSocketHandler sensorWebSocketHandler;
    private final WebSocketAuthHandshakeInterceptor authHandshakeInterceptor;

    // 注入传感器处理器和WebSocket握手鉴权拦截器
    public WebSocketConfig(
            SensorWebSocketHandler sensorWebSocketHandler,
            WebSocketAuthHandshakeInterceptor authHandshakeInterceptor) {
        this.sensorWebSocketHandler = sensorWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    // 注册WebSocket处理器、握手鉴权和来源白名单
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sensorWebSocketHandler, "/ws/sensor")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins(authHandshakeInterceptor.getAllowedOrigins());
    }
}
