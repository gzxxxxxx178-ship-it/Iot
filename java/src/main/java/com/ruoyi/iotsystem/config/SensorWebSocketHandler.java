package com.ruoyi.iotsystem.config;

import com.ruoyi.iotsystem.service.DeviceService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Set;

@Component
public class SensorWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final DeviceService deviceService;

    // 注入设备级授权查询，连接不缓存可访问设备集合。
    public SensorWebSocketHandler(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

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

    // 按当前设备授权分发，新增和撤销共享对已有连接的下一条消息生效。
    public void broadcastToDevice(String deviceId, String message) {
        Set<String> recipients = deviceService.getTelemetryRecipients(deviceId);
        for (WebSocketSession session : sessions) {
            try {
                Object authenticatedUsername = session.getAttributes().get("authenticatedUsername");
                if (authenticatedUsername instanceof String && recipients.contains(authenticatedUsername)) {
                    synchronized (session) {
                        if (session.isOpen()) session.sendMessage(new TextMessage(message));
                    }
                }
            } catch (IOException | IllegalStateException e) {
                sessions.remove(session);
            }
        }
    }
}
