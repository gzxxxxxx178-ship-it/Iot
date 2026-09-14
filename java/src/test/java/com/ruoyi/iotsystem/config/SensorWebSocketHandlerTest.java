package com.ruoyi.iotsystem.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensorWebSocketHandlerTest {

    private final SensorWebSocketHandler handler = new SensorWebSocketHandler();
    private final WebSocketSession aliceSession = sessionFor("alice");
    private final WebSocketSession bobSession = sessionFor("bob");

    // 清理静态会话池，避免影响其他测试用例
    @AfterEach
    void tearDown() throws Exception {
        handler.afterConnectionClosed(aliceSession, CloseStatus.NORMAL);
        handler.afterConnectionClosed(bobSession, CloseStatus.NORMAL);
    }

    // 验证实时遥测只推送给设备所属用户
    @Test
    void broadcastToOwner_设备归属为Alice_仅向Alice推送() throws Exception {
        handler.afterConnectionEstablished(aliceSession);
        handler.afterConnectionEstablished(bobSession);

        handler.broadcastToOwner("alice", "{\"deviceId\":\"device001\"}");

        verify(aliceSession).sendMessage(any());
        verify(bobSession, never()).sendMessage(any());
    }

    // 验证未归属的历史遥测不发送给任何会话
    @Test
    void broadcastToOwner_缺少设备归属_不推送() throws Exception {
        handler.afterConnectionEstablished(aliceSession);

        handler.broadcastToOwner(null, "{\"deviceId\":\"legacy\"}");

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
