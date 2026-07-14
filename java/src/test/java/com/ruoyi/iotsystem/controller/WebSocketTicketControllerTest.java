package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.WebSocketTicketResponse;
import com.ruoyi.iotsystem.service.WebSocketTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebSocketTicketControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WebSocketTicketService ticketService;

    // 创建仅包含WebSocket票据控制器的MockMvc环境
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new WebSocketTicketController(ticketService)).build();
    }

    // 验证接口使用当前认证用户名签发统一格式的票据响应
    @Test
    void issueTicket_已认证用户_应返回票据() throws Exception {
        when(ticketService.issueTicket("alice"))
                .thenReturn(new WebSocketTicketResponse("one-time-ticket", 30));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("alice", null);

        mockMvc.perform(post("/api/ws/ticket").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ticket").value("one-time-ticket"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(30));
        verify(ticketService).issueTicket("alice");
    }
}
