package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.config.JwtUtil;
import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.dto.LoginRequest;
import com.ruoyi.iotsystem.dto.RegisterRequest;
import com.ruoyi.iotsystem.entity.UserEntity;
import com.ruoyi.iotsystem.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    // 用户名密码登录：认证成功返回 JWT 令牌，失败由全局异常处理器统一处理
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse resp = userService.login(request);
        logger.info("用户登录成功: {}", request.getUsername());
        return ApiResponse.success(resp);
    }

    // 用户注册：校验唯一性、加密密码、返回 JWT。用户名已存在时抛 RuntimeException，由全局异常处理器统一返回错误信息
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse resp = userService.register(request);
        logger.info("用户注册成功: {}", request.getUsername());
        return ApiResponse.success(resp);
    }

    // 获取当前登录用户信息：从 SecurityContext 读取认证用户，查询数据库返回用户名和创建时间
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
