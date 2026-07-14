package com.ruoyi.iotsystem.config;

import com.ruoyi.iotsystem.service.WebSocketTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final WebSocketTicketService ticketService;
    private final Set<String> allowedOrigins;

    // 注入票据服务并解析WebSocket来源白名单
    public WebSocketAuthHandshakeInterceptor(
            WebSocketTicketService ticketService,
            @Value("${websocket.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.ticketService = ticketService;
        this.allowedOrigins = parseAllowedOrigins(allowedOrigins);
    }

    // 校验浏览器来源和一次性票据，并把认证用户名写入会话属性
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (!isAllowedOrigin(origin)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");
        Optional<String> username = ticketService.consumeTicket(ticket);
        if (!username.isPresent()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put("authenticatedUsername", username.get());
        return true;
    }

    // 握手完成后无需追加处理
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op: ticket is consumed before the connection is established.
    }

    // 获取供WebSocket注册器复用的来源白名单
    public String[] getAllowedOrigins() {
        return allowedOrigins.toArray(new String[0]);
    }

    // 判断请求Origin是否与标准化白名单完全匹配
    private boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.trim().isEmpty()) {
            return false;
        }
        try {
            return allowedOrigins.contains(normalizeOrigin(origin));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    // 解析逗号分隔的来源配置并拒绝通配符或空白名单
    private Set<String> parseAllowedOrigins(String configuredOrigins) {
        Set<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(this::normalizeOrigin)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (origins.isEmpty() || origins.contains("*")) {
            throw new IllegalArgumentException("WebSocket来源白名单不能为空或使用通配符");
        }
        return origins;
    }

    // 将HTTP来源标准化为scheme、host和显式非默认端口
    private String normalizeOrigin(String origin) {
        URI uri;
        try {
            uri = URI.create(origin.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("无效的WebSocket来源: " + origin, exception);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null
                || (!("http".equalsIgnoreCase(scheme)) && !("https".equalsIgnoreCase(scheme)))) {
            throw new IllegalArgumentException("WebSocket来源必须是HTTP或HTTPS源");
        }
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme.toLowerCase() + "://" + host.toLowerCase()
                + (defaultPort ? "" : ":" + port);
    }
}
