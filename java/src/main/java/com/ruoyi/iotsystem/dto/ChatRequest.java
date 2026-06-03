package com.ruoyi.iotsystem.dto;

import java.util.List;
import java.util.Map;

public class ChatRequest {
    private String sessionId;
    private List<Map<String, String>> messages;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public List<Map<String, String>> getMessages() {
        return messages;
    }

    public void setMessages(List<Map<String, String>> messages) {
        this.messages = messages;
    }
}
