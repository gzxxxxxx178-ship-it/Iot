package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.dto.LoginRequest;
import com.ruoyi.iotsystem.dto.RegisterRequest;
import com.ruoyi.iotsystem.entity.WechatIdentityEntity;
import com.ruoyi.iotsystem.exception.BusinessException;
import com.ruoyi.iotsystem.repository.WechatIdentityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/** 处理微信身份登录、绑定现有账号及创建新账号。 */
@Service
public class WechatAuthService {

    private final WechatIdentityRepository identityRepository;
    private final WechatCodeExchangeClient codeExchangeClient;
    private final UserService userService;
    private final AuthRefreshTokenService authRefreshTokenService;
    private final String appId;
    private final String appSecret;

    /** 注入微信配置、身份映射仓库和系统账号服务。 */
    public WechatAuthService(WechatIdentityRepository identityRepository,
            WechatCodeExchangeClient codeExchangeClient, UserService userService,
            AuthRefreshTokenService authRefreshTokenService,
            @Value("${wechat.miniapp.app-id:}") String appId,
            @Value("${wechat.miniapp.app-secret:}") String appSecret) {
        this.identityRepository = identityRepository;
        this.codeExchangeClient = codeExchangeClient;
        this.userService = userService;
        this.authRefreshTokenService = authRefreshTokenService;
        this.appId = appId;
        this.appSecret = appSecret;
    }

    /** 登录已绑定微信；未绑定时返回空结果引导用户绑定或注册。 */
    public Optional<AuthResponse> login(String code) {
        String openId = exchangeCode(code);
        return identityRepository.findByAppIdAndOpenId(appId, openId)
                .map(identity -> createRememberedSession(identity.getUsername()));
    }

    /** 验证现有系统账号密码并绑定当前微信身份。 */
    @Transactional
    public AuthResponse linkExistingAccount(String code, String username, String password) {
        String openId = exchangeCode(code);
        Optional<WechatIdentityEntity> existing = identityRepository.findByAppIdAndOpenId(appId, openId);
        if (existing.isPresent()) {
            if (!existing.get().getUsername().equals(username)) throw new BusinessException("该微信已绑定其他系统账号");
            return createRememberedSession(username);
        }
        if (identityRepository.existsByAppIdAndUsername(appId, username)) {
            throw new BusinessException("该系统账号已绑定其他微信身份");
        }
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);
        loginRequest.setRememberMe(true);
        AuthResponse response = userService.login(loginRequest);
        saveIdentity(openId, username);
        return response;
    }

    /** 创建系统账号并原子绑定当前微信身份。 */
    @Transactional
    public AuthResponse registerWechatAccount(String code, String username, String password) {
        String openId = exchangeCode(code);
        if (username == null || username.length() > 100) {
            throw new BusinessException("注册用户名不能超过100个字符");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException("注册密码至少需要6个字符");
        }
        if (identityRepository.findByAppIdAndOpenId(appId, openId).isPresent()) {
            throw new BusinessException("该微信已绑定账号，请直接微信登录");
        }
        if (identityRepository.existsByAppIdAndUsername(appId, username)) {
            throw new BusinessException("该系统账号已绑定其他微信身份");
        }
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setPassword(password);
        registerRequest.setRememberMe(true);
        AuthResponse response = userService.register(registerRequest);
        saveIdentity(openId, username);
        return response;
    }

    /** 检查本机凭据配置并通过微信服务端校验一次性code。 */
    private String exchangeCode(String code) {
        if (appId == null || appId.trim().isEmpty() || appSecret == null || appSecret.trim().isEmpty()) {
            throw new BusinessException("微信登录尚未配置，请在本机设置小程序AppID和AppSecret");
        }
        return codeExchangeClient.exchange(appId, appSecret, code);
    }

    /** 为已确认账号签发小程序短期访问令牌与刷新会话。 */
    private AuthResponse createRememberedSession(String username) {
        return authRefreshTokenService.createSession(username);
    }

    /** 持久化小程序、OpenID和系统账号之间的唯一映射。 */
    private void saveIdentity(String openId, String username) {
        WechatIdentityEntity identity = new WechatIdentityEntity();
        identity.setAppId(appId);
        identity.setOpenId(openId);
        identity.setUsername(username);
        identity.setCreatedAt(LocalDateTime.now());
        try {
            identityRepository.saveAndFlush(identity);
        } catch (DataIntegrityViolationException error) {
            throw new BusinessException("该微信或系统账号已完成绑定");
        }
    }
}
