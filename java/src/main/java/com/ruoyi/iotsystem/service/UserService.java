package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.dto.LoginRequest;
import com.ruoyi.iotsystem.dto.RegisterRequest;
import com.ruoyi.iotsystem.entity.UserEntity;
import com.ruoyi.iotsystem.repository.UserRepository;
import com.ruoyi.iotsystem.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Lazy
    @Autowired
    private AuthenticationManager authenticationManager;

    // 用户注册：校验用户名唯一性、加密密码、保存用户、生成JWT
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        UserEntity user = new UserEntity(request.getUsername(),
                passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername());
    }

    // 用户登录：AuthenticationManager 认证、生成JWT
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        String token = jwtUtil.generateToken(request.getUsername());
        return new AuthResponse(token, request.getUsername());
    }

    // 按用户名查找用户
    public UserEntity findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    // Spring Security 用户加载：查数据库，OAuth用户密码null时兜底空串
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        // OAuth 用户没有密码，给空字符串避免 Spring Security User 构造函数报错
        String password = user.getPassword() != null ? user.getPassword() : "";
        return new User(user.getUsername(), password, Collections.emptyList());
    }
}
