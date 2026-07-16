package com.ruoyi.iotsystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.iotsystem.config.GlobalExceptionHandler;
import com.ruoyi.iotsystem.config.JwtUtil;
import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.dto.LoginRequest;
import com.ruoyi.iotsystem.dto.RegisterRequest;
import com.ruoyi.iotsystem.entity.UserEntity;
import com.ruoyi.iotsystem.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // 绕过 Spring Security 过滤器，直接测试 Controller 逻辑
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private UserService userService;
    @MockBean private JwtUtil jwtUtil; // AuthController 注入了 JwtUtil

    // ==================== 登录测试 ====================

    @Test
    void login_正确凭据_应返回200并写入HttpOnlyCookie() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("password123");

        when(userService.login(any())).thenReturn(new AuthResponse("jwt.token", "testuser"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("iot_access_token=")))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    void login_错误密码_应返回401错误() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("wrong");

        when(userService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void login_用户名为空_应返回400校验错误() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 注册测试 ====================

    @Test
    void register_成功注册_应返回200并写入HttpOnlyCookie() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("password123");

        when(userService.register(any())).thenReturn(new AuthResponse("jwt.token", "newuser"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));
    }

    @Test
    void register_用户名重复_应返回400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("existing");
        req.setPassword("password123");

        when(userService.register(any())).thenThrow(new RuntimeException("用户名已存在"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void register_密码不足6位_应返回400校验错误() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("12345"); // 只有5位

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 获取当前用户测试 ====================

    @Test
    @WithMockUser(username = "testuser")
    void me_已登录用户_应返回用户信息() throws Exception {
        UserEntity user = new UserEntity("testuser", "encoded");
        user.setCreatedAt(LocalDateTime.now());
        when(userService.findByUsername("testuser")).thenReturn(user);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    void me_未登录_应返回401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
