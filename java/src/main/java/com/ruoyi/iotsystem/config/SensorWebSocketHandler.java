package com.ruoyi.iotsystem.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SensorWebSocketHandler extends TextWebSocketHandler {

    private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    // WebSocket连接建立，加入会话池
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getAttributes().get("authenticatedUsername") == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.add(session);
    }

    // WebSocket连接关闭，从会话池移除
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }

    // 仅向拥有目标设备的已认证用户推送实时消息，避免跨用户泄露遥测数据
    public void broadcastToOwner(String ownerUsername, String message) {
        if (ownerUsername == null || ownerUsername.trim().isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                Object authenticatedUsername = session.getAttributes().get("authenticatedUsername");
                if (ownerUsername.equals(authenticatedUsername) && session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                // Ignore failure for specific session
            }
        }
    }
}
