package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.WebSocketTicketResponse;
import com.ruoyi.iotsystem.service.WebSocketTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "WebSocket认证", description = "签发短期一次性WebSocket握手票据")
@RestController
@RequestMapping("/api/ws")
public class WebSocketTicketController {

    private final WebSocketTicketService ticketService;

    // 注入WebSocket票据服务
    public WebSocketTicketController(WebSocketTicketService ticketService) {
        this.ticketService = ticketService;
    }

    // 为当前已认证用户签发一次性WebSocket握手票据
    @Operation(summary = "签发WebSocket票据")
    @PostMapping("/ticket")
    public ApiResponse<WebSocketTicketResponse> issueTicket(Authentication authentication) {
        return ApiResponse.success(ticketService.issueTicket(authentication.getName()));
    }
}
