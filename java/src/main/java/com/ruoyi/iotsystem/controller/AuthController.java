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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "认证管理", description = "用户登录、注册、个人信息的 REST API")
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Operation(summary = "用户登录", description = "用户名密码登录，成功返回 JWT token")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse resp = userService.login(request);
        logger.info("用户登录成功: {}", request.getUsername());
        return ApiResponse.success(resp);
    }

    @Operation(summary = "用户注册", description = "注册新用户，用户名不能重复，密码至少6位。成功自动返回 JWT token")
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse resp = userService.register(request);
        logger.info("用户注册成功: {}", request.getUsername());
        return ApiResponse.success(resp);
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
}
