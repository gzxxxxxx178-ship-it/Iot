package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.WebSocketTicketResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketTicketService {

    private static final int TICKET_BYTES = 32;

    private final long ttlSeconds;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final Map<String, TicketEntry> tickets = new ConcurrentHashMap<>();

    // 使用配置的有效期创建生产环境票据服务
    @Autowired
    public WebSocketTicketService(
            @Value("${websocket.ticket-ttl-seconds:30}") long ttlSeconds) {
        this(ttlSeconds, Clock.systemUTC(), new SecureRandom());
    }

    // 使用可控时钟和随机源创建便于验证的票据服务
    WebSocketTicketService(long ttlSeconds, Clock clock, SecureRandom secureRandom) {
        if (ttlSeconds < 1 || ttlSeconds > 300) {
            throw new IllegalArgumentException("WebSocket票据有效期必须在1到300秒之间");
        }
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    // 为已认证用户签发短期一次性WebSocket票据
    public WebSocketTicketResponse issueTicket(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("已认证用户名不能为空");
        }
        removeExpiredTickets();
        byte[] randomBytes = new byte[TICKET_BYTES];
        secureRandom.nextBytes(randomBytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        tickets.put(ticket, new TicketEntry(username, clock.instant().plusSeconds(ttlSeconds)));
        return new WebSocketTicketResponse(ticket, ttlSeconds);
    }

    // 原子消费票据并返回对应用户名，失效或重复使用时返回空
    public Optional<String> consumeTicket(String ticket) {
        if (ticket == null || ticket.trim().isEmpty()) {
            return Optional.empty();
        }
        TicketEntry entry = tickets.remove(ticket);
        if (entry == null || !entry.expiresAt.isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.username);
    }

    // 清除已过期且尚未消费的票据
    private void removeExpiredTickets() {
        Instant now = clock.instant();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
    }

    private static class TicketEntry {
        private final String username;
        private final Instant expiresAt;

        // 保存票据所属用户和绝对过期时间
        private TicketEntry(String username, Instant expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }
}
