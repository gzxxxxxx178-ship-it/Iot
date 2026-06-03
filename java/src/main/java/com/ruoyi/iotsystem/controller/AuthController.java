package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.config.JwtUtil;
import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.dto.LoginRequest;
import com.ruoyi.iotsystem.dto.RegisterRequest;
import com.ruoyi.iotsystem.entity.UserEntity;
import com.ruoyi.iotsystem.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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

    // 用户名密码登录，返回JWT令牌
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse resp = userService.login(request);
            return ResponseEntity.ok(resp);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body("用户名或密码错误");
        }
    }

    // 用户注册：校验唯一性、加密密码、返回JWT
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse resp = userService.register(request);
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    // 获取当前登录用户信息
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(401).body("未登录");
        }
        UserEntity user = userService.findByUsername(auth.getName());
        if (user == null) {
            return ResponseEntity.status(401).body("用户不存在");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.getUsername());
        map.put("createdAt", user.getCreatedAt());
        return ResponseEntity.ok(map);
    }
}
