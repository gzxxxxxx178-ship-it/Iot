package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.WebSocketTicketResponse;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketTicketServiceTest {

    // 验证合法用户可签发并消费票据
    @Test
    void issueAndConsume_合法用户_应返回用户名() {
        WebSocketTicketService service = createService(30);

        WebSocketTicketResponse response = service.issueTicket("alice");

        assertFalse(response.getTicket().isEmpty());
        assertEquals(30, response.getExpiresInSeconds());
        assertEquals("alice", service.consumeTicket(response.getTicket()).orElse(null));
    }

    // 验证票据只能成功消费一次
    @Test
    void consumeTicket_重复使用_第二次应失败() {
        WebSocketTicketService service = createService(30);
        String ticket = service.issueTicket("alice").getTicket();

        assertTrue(service.consumeTicket(ticket).isPresent());
        assertFalse(service.consumeTicket(ticket).isPresent());
    }

    // 验证超过有效期的票据不能用于握手
    @Test
    void consumeTicket_票据过期_应失败() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-14T00:00:00Z"));
        WebSocketTicketService service = new WebSocketTicketService(30, clock, new SecureRandom());
        String ticket = service.issueTicket("alice").getTicket();

        clock.advance(Duration.ofSeconds(31));

        assertFalse(service.consumeTicket(ticket).isPresent());
    }

    // 验证空用户名不能领取票据
    @Test
    void issueTicket_空用户名_应拒绝() {
        WebSocketTicketService service = createService(30);

        assertThrows(IllegalArgumentException.class, () -> service.issueTicket(" "));
    }

    // 验证异常长票据有效期不能启动服务
    @Test
    void constructor_有效期超过上限_应拒绝() {
        assertThrows(IllegalArgumentException.class, () -> createService(301));
    }

    // 创建使用固定时钟的票据服务
    private WebSocketTicketService createService(long ttlSeconds) {
        return new WebSocketTicketService(
                ttlSeconds,
                Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC),
                new SecureRandom());
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        // 创建从指定时刻开始的可推进时钟
        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        // 获取时钟时区
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        // 创建切换到指定时区的时钟
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        // 获取当前模拟时刻
        @Override
        public Instant instant() {
            return instant;
        }

        // 将模拟时钟向前推进指定时长
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
