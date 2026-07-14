package com.ruoyi.iotsystem.config;

import com.ruoyi.iotsystem.service.WebSocketTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketAuthHandshakeInterceptorTest {

    private WebSocketTicketService ticketService;
    private WebSocketAuthHandshakeInterceptor interceptor;
    private WebSocketHandler handler;

    // 初始化票据服务、白名单和WebSocket处理器
    @BeforeEach
    void setUp() {
        ticketService = new WebSocketTicketService(30);
        interceptor = new WebSocketAuthHandshakeInterceptor(
                ticketService,
                "http://localhost:5173,https://app.example.com");
        handler = mock(WebSocketHandler.class);
    }

    // 验证白名单来源和有效票据可完成握手
    @Test
    void beforeHandshake_合法来源和票据_应通过() {
        String ticket = ticketService.issueTicket("alice").getTicket();
        ServerHttpRequest request = createRequest("http://localhost:5173", ticket);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, handler, attributes);

        assertTrue(result);
        assertEquals("alice", attributes.get("authenticatedUsername"));
    }

    // 验证缺失票据的握手返回未认证
    @Test
    void beforeHandshake_缺失票据_应拒绝() {
        ServerHttpRequest request = createRequest("http://localhost:5173", null);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, handler, new HashMap<>());

        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    // 验证伪造票据的握手返回未认证
    @Test
    void beforeHandshake_无效票据_应拒绝() {
        ServerHttpRequest request = createRequest("http://localhost:5173", "forged-ticket");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, handler, new HashMap<>());

        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    // 验证非白名单来源被拒绝且不会消耗合法票据
    @Test
    void beforeHandshake_来源不合法_应拒绝并保留票据() {
        String ticket = ticketService.issueTicket("alice").getTicket();
        ServerHttpRequest deniedRequest = createRequest("https://evil.example", ticket);
        ServerHttpResponse deniedResponse = mock(ServerHttpResponse.class);

        boolean denied = interceptor.beforeHandshake(
                deniedRequest, deniedResponse, handler, new HashMap<>());
        boolean allowed = interceptor.beforeHandshake(
                createRequest("https://app.example.com", ticket),
                mock(ServerHttpResponse.class),
                handler,
                new HashMap<>());

        assertFalse(denied);
        verify(deniedResponse).setStatusCode(HttpStatus.FORBIDDEN);
        assertTrue(allowed);
    }

    // 验证来源白名单禁止使用通配符
    @Test
    void constructor_通配符来源_应拒绝() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WebSocketAuthHandshakeInterceptor(ticketService, "*"));
    }

    // 创建带指定Origin和可选票据的模拟握手请求
    private ServerHttpRequest createRequest(String origin, String ticket) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        when(request.getHeaders()).thenReturn(headers);
        String uri = "ws://localhost:8080/ws/sensor";
        if (ticket != null) {
            uri += "?ticket=" + ticket;
        }
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }
}
