package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.config.JwtUtil;
import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.dto.LoginRequest;
import com.ruoyi.iotsystem.dto.RegisterRequest;
import com.ruoyi.iotsystem.entity.UserEntity;
import com.ruoyi.iotsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserService userService;

    private final String username = "testuser";
    private final String password = "password123";
    private final String encodedPassword = "$2a$10$encoded";
    private final String token = "jwt.token.here";

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.encode(any())).thenReturn(encodedPassword);
        lenient().when(jwtUtil.generateToken(any())).thenReturn(token);
    }

    // ==================== 注册测试 ====================

    @Test
    void register_新用户_应返回token和用户名() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse resp = userService.register(req);

        assertNotNull(resp);
        assertEquals(token, resp.getToken());
        assertEquals(username, resp.getUsername());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void register_用户名已存在_应抛异常() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);

        when(userRepository.existsByUsername(username)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.register(req));
        assertEquals("用户名已存在", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    // ==================== 登录测试 ====================

    @Test
    void login_正确的用户名密码_应返回token() {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);

        AuthResponse resp = userService.login(req);

        assertNotNull(resp);
        assertEquals(token, resp.getToken());
        assertEquals(username, resp.getUsername());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_错误的密码_应抛BadCredentialsException() {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> userService.login(req));
    }

    // ==================== 用户查询测试 ====================

    @Test
    void loadUserByUsername_用户存在_应返回UserDetails() {
        UserEntity user = new UserEntity(username, password);
        user.setPassword(password); // 本地用户有密码
        when(userRepository.findByUsername(username)).thenReturn(user);

        org.springframework.security.core.userdetails.UserDetails userDetails =
                userService.loadUserByUsername(username);

        assertNotNull(userDetails);
        assertEquals(username, userDetails.getUsername());
    }

    @Test
    void loadUserByUsername_用户不存在_应抛UsernameNotFoundException() {
        when(userRepository.findByUsername(username)).thenReturn(null);

        assertThrows(
                org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> userService.loadUserByUsername(username)
        );
    }

    @Test
    void findByUsername_应返回用户实体() {
        UserEntity user = new UserEntity(username, password);
        when(userRepository.findByUsername(username)).thenReturn(user);

        UserEntity result = userService.findByUsername(username);

        assertEquals(username, result.getUsername());
    }
}
