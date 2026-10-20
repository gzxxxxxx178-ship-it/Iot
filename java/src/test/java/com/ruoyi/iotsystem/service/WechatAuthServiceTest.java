package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.dto.LoginRequest;
import com.ruoyi.iotsystem.dto.RegisterRequest;
import com.ruoyi.iotsystem.entity.WechatIdentityEntity;
import com.ruoyi.iotsystem.exception.BusinessException;
import com.ruoyi.iotsystem.repository.WechatIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WechatAuthServiceTest {

    @Mock private WechatIdentityRepository identityRepository;
    @Mock private WechatCodeExchangeClient codeExchangeClient;
    @Mock private UserService userService;
    @Mock private AuthRefreshTokenService refreshTokenService;

    private WechatAuthService service;

    /** 建立使用隔离配置与模拟微信服务的身份认证服务。 */
    @BeforeEach
    void setUp() {
        service = new WechatAuthService(identityRepository, codeExchangeClient, userService,
                refreshTokenService, "wx-test-app", "secret-only-in-test");
        when(codeExchangeClient.exchange("wx-test-app", "secret-only-in-test", "one-time-code"))
                .thenReturn("openid-123");
        lenient().when(refreshTokenService.createSession("alice"))
                .thenReturn(new AuthResponse("access", "alice", "refresh", 900));
    }

    /** 已绑定OpenID直接创建短期访问令牌和可撤销刷新会话。 */
    @Test
    void login_已绑定微信_签发账号会话() {
        WechatIdentityEntity identity = identity("alice");
        when(identityRepository.findByAppIdAndOpenId("wx-test-app", "openid-123"))
                .thenReturn(Optional.of(identity));

        AuthResponse response = service.login("one-time-code").orElseThrow(AssertionError::new);

        assertEquals("alice", response.getUsername());
        verify(refreshTokenService).createSession("alice");
    }

    /** 未绑定OpenID不自动创建不可恢复的孤立账号。 */
    @Test
    void login_未绑定微信_返回待绑定状态() {
        when(identityRepository.findByAppIdAndOpenId("wx-test-app", "openid-123"))
                .thenReturn(Optional.empty());

        assertFalse(service.login("one-time-code").isPresent());
        verify(refreshTokenService, never()).createSession(any());
    }

    /** 绑定前验证现有系统密码并保存唯一身份映射。 */
    @Test
    void linkExistingAccount_认证成功后绑定并签发会话() {
        when(identityRepository.findByAppIdAndOpenId("wx-test-app", "openid-123"))
                .thenReturn(Optional.empty());
        when(userService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("access", "alice", "refresh", 900));

        AuthResponse response = service.linkExistingAccount("one-time-code", "alice", "password1");

        ArgumentCaptor<LoginRequest> loginCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(userService).login(loginCaptor.capture());
        assertEquals("alice", loginCaptor.getValue().getUsername());
        assertEquals("password1", loginCaptor.getValue().getPassword());
        assertTrue(loginCaptor.getValue().isRememberMe());
        ArgumentCaptor<WechatIdentityEntity> identityCaptor = ArgumentCaptor.forClass(WechatIdentityEntity.class);
        verify(identityRepository).saveAndFlush(identityCaptor.capture());
        assertEquals("wx-test-app", identityCaptor.getValue().getAppId());
        assertEquals("openid-123", identityCaptor.getValue().getOpenId());
        assertEquals("alice", identityCaptor.getValue().getUsername());
        assertEquals("alice", response.getUsername());
    }

    /** 已绑定的微信身份不可被覆盖到另一个系统账号。 */
    @Test
    void linkExistingAccount_微信已绑定其他账号_拒绝重绑() {
        when(identityRepository.findByAppIdAndOpenId("wx-test-app", "openid-123"))
                .thenReturn(Optional.of(identity("bob")));

        assertThrows(BusinessException.class,
                () -> service.linkExistingAccount("one-time-code", "alice", "password1"));
        verify(userService, never()).login(any(LoginRequest.class));
        verify(identityRepository, never()).saveAndFlush(any(WechatIdentityEntity.class));
    }

    /** 微信注册将账号创建和身份映射放在同一事务边界。 */
    @Test
    void registerWechatAccount_新账号注册后保存微信映射() {
        when(identityRepository.findByAppIdAndOpenId("wx-test-app", "openid-123"))
                .thenReturn(Optional.empty());
        when(userService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse("access", "newuser", "refresh", 900));

        AuthResponse response = service.registerWechatAccount("one-time-code", "newuser", "password1");

        ArgumentCaptor<RegisterRequest> registerCaptor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(userService).register(registerCaptor.capture());
        assertEquals("newuser", registerCaptor.getValue().getUsername());
        assertTrue(registerCaptor.getValue().isRememberMe());
        verify(identityRepository).saveAndFlush(any(WechatIdentityEntity.class));
        assertEquals("newuser", response.getUsername());
    }

    /** 构造指定账号的微信身份记录。 */
    private WechatIdentityEntity identity(String username) {
        WechatIdentityEntity identity = new WechatIdentityEntity();
        identity.setAppId("wx-test-app");
        identity.setOpenId("openid-123");
        identity.setUsername(username);
        return identity;
    }
}
