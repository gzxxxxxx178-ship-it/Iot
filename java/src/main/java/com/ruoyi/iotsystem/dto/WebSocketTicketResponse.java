package com.ruoyi.iotsystem.dto;

public class WebSocketTicketResponse {

    private final String ticket;
    private final long expiresInSeconds;

    // 创建包含一次性票据和有效期的响应
    public WebSocketTicketResponse(String ticket, long expiresInSeconds) {
        this.ticket = ticket;
        this.expiresInSeconds = expiresInSeconds;
    }

    // 获取一次性WebSocket票据
    public String getTicket() {
        return ticket;
    }

    // 获取票据剩余有效秒数
    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
