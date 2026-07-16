package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * AI 对话请求体：sessionId 和 messages 均不能为空
 */
public class ChatRequest {

    @NotBlank(message = "会话ID不能为空")
    @Size(max = 64, message = "会话ID不能超过64个字符")
    private String sessionId;

    @NotEmpty(message = "消息列表不能为空")
    @Size(max = 50, message = "消息数量不能超过50条")
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
