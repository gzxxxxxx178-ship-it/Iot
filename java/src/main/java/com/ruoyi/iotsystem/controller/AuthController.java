package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.config.JwtUtil;
import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.dto.LoginRequest;
import com.ruoyi.iotsystem.dto.RegisterRequest;
import com.ruoyi.iotsystem.entity.UserEntity;
import com.ruoyi.iotsystem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "认证管理", description = "用户登录、注册、个人信息的 REST API")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${auth.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${auth.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${jwt.expiration:900000}")
    private long tokenLifetimeMs;

    @Operation(summary = "用户登录", description = "用户名密码登录，成功写入HttpOnly JWT Cookie")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse resp = userService.login(request);
        writeAuthCookie(response, resp.getToken(), false);
        logger.info("用户登录成功: {}", request.getUsername());
        return ApiResponse.success(new AuthResponse(null, resp.getUsername()));
    }

    @Operation(summary = "用户注册", description = "注册新用户，成功写入HttpOnly JWT Cookie")
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResponse resp = userService.register(request);
        writeAuthCookie(response, resp.getToken(), false);
        logger.info("用户注册成功: {}", request.getUsername());
        return ApiResponse.success(new AuthResponse(null, resp.getUsername()));
    }

    // 清除HttpOnly认证Cookie
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        writeAuthCookie(response, "", true);
        return ApiResponse.success();
    }

    @Operation(summary = "获取当前用户信息", description = "从 SecurityContext 读取已登录用户，返回用户名和创建时间")
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ApiResponse.fail(401, "未登录");
        }
        UserEntity user = userService.findByUsername(auth.getName());
        if (user == null) {
            return ApiResponse.fail(401, "用户不存在");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.getUsername());
        map.put("createdAt", user.getCreatedAt());
        return ApiResponse.success(map);
    }

    // 写入不可被JavaScript读取的短期JWT Cookie
    private void writeAuthCookie(HttpServletResponse response, String token, boolean clear) {
        ResponseCookie cookie = ResponseCookie.from("iot_access_token", token == null ? "" : token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(clear ? Duration.ZERO : Duration.ofMillis(tokenLifetimeMs))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
