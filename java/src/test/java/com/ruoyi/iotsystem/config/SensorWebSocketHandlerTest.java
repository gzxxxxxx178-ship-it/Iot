package com.ruoyi.iotsystem.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import com.ruoyi.iotsystem.service.DeviceService;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensorWebSocketHandlerTest {

    private final DeviceService devices = mock(DeviceService.class);
    private final SensorWebSocketHandler handler = new SensorWebSocketHandler(devices);
    private final WebSocketSession aliceSession = sessionFor("alice");
    private final WebSocketSession bobSession = sessionFor("bob");

    // 清理测试会话池。
    @AfterEach
    void tearDown() throws Exception {
        handler.afterConnectionClosed(aliceSession, CloseStatus.NORMAL);
        handler.afterConnectionClosed(bobSession, CloseStatus.NORMAL);
    }

    // 验证实时遥测只推送给设备所属用户
    @Test
    void broadcastToDevice_设备归属为Alice_仅向Alice推送() throws Exception {
        handler.afterConnectionEstablished(aliceSession);
        handler.afterConnectionEstablished(bobSession);

        when(devices.getTelemetryRecipients("device001")).thenReturn(Collections.singleton("alice"));
        handler.broadcastToDevice("device001", "{\"deviceId\":\"device001\"}");

        verify(aliceSession).sendMessage(any());
        verify(bobSession, never()).sendMessage(any());
    }

    // 验证未归属的历史遥测不发送给任何会话
    @Test
    void broadcastToDevice_缺少设备归属_不推送() throws Exception {
        handler.afterConnectionEstablished(aliceSession);

        when(devices.getTelemetryRecipients("legacy")).thenReturn(Collections.emptySet());
        handler.broadcastToDevice("legacy", "{\"deviceId\":\"legacy\"}");

        verify(aliceSession, never()).sendMessage(any());
    }

    // 创建带已认证用户名的可用WebSocket会话替身
    private WebSocketSession sessionFor(String username) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("authenticatedUsername", username);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
