package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.config.JwtUtil;
import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.dto.LoginRequest;
import com.ruoyi.iotsystem.dto.RegisterRequest;
import com.ruoyi.iotsystem.dto.RefreshTokenRequest;
import com.ruoyi.iotsystem.dto.WechatAccountRequest;
import com.ruoyi.iotsystem.dto.WechatCodeRequest;
import com.ruoyi.iotsystem.entity.UserEntity;
import com.ruoyi.iotsystem.service.AuthRefreshTokenService;
import com.ruoyi.iotsystem.service.UserService;
import com.ruoyi.iotsystem.service.WechatAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Tag(name = "认证管理", description = "用户登录、注册、个人信息的 REST API")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private AuthRefreshTokenService authRefreshTokenService;

    @Autowired
    private WechatAuthService wechatAuthService;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${auth.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${auth.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${jwt.expiration:900000}")
    private long tokenLifetimeMs;

    // 使用账号密码登录；Web写入HttpOnly Cookie，小程序按需取得可轮换的刷新会话。
    @Operation(summary = "用户登录", description = "用户名密码登录，Web使用HttpOnly Cookie，小程序可选择刷新会话")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse resp = userService.login(request);
        if (!request.isRememberMe()) writeAuthCookie(response, resp.getToken(), false);
        logger.info("用户登录成功: {}", request.getUsername());
        return ApiResponse.success(resp);
    }

    // 注册账号；Web写入HttpOnly Cookie，小程序按需取得可轮换的刷新会话。
    @Operation(summary = "用户注册", description = "注册新用户，Web使用HttpOnly Cookie，小程序可选择刷新会话")
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResponse resp = userService.register(request);
        if (!request.isRememberMe()) writeAuthCookie(response, resp.getToken(), false);
        logger.info("用户注册成功: {}", request.getUsername());
        return ApiResponse.success(resp);
    }

    // 撤销请求携带的刷新会话并清除HttpOnly认证Cookie。
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshTokenRequest request, HttpServletResponse response) {
        if (request != null) authRefreshTokenService.revoke(request.getRefreshToken());
        writeAuthCookie(response, "", true);
        return ApiResponse.success();
    }

    /** 轮换小程序刷新凭据并签发新的短期访问令牌。 */
    @Operation(summary = "刷新小程序会话", description = "原子轮换刷新凭据并返回短期JWT")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        Optional<AuthResponse> result = authRefreshTokenService.rotate(request.getRefreshToken());
        if (!result.isPresent()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(401, "登录已过期，请重新登录"));
        }
        return ResponseEntity.ok(ApiResponse.success(result.get()));
    }

    /** 使用微信一次性登录凭证登录已绑定的系统账号。 */
    @Operation(summary = "微信小程序登录", description = "服务端验证微信登录凭证；首次登录需绑定现有账号或创建账号")
    @PostMapping("/wechat/login")
    public ResponseEntity<ApiResponse<AuthResponse>> wechatLogin(@Valid @RequestBody WechatCodeRequest request) {
        Optional<AuthResponse> result = wechatAuthService.login(request.getCode());
        if (!result.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.fail(409, "首次使用微信登录，请绑定已有账号或创建账号"));
        }
        return ResponseEntity.ok(ApiResponse.success(result.get()));
    }

    /** 验证现有账号密码并将当前微信身份绑定到该账号。 */
    @Operation(summary = "绑定微信身份", description = "首次使用微信登录时验证现有系统账号并建立身份映射")
    @PostMapping("/wechat/link")
    public ApiResponse<AuthResponse> linkWechat(@Valid @RequestBody WechatAccountRequest request) {
        return ApiResponse.success(wechatAuthService.linkExistingAccount(
                request.getCode(), request.getUsername().trim(), request.getPassword()));
    }

    /** 创建系统账号并在同一事务中绑定当前微信身份。 */
    @Operation(summary = "微信注册", description = "为首次使用微信登录的用户创建系统账号并建立身份映射")
    @PostMapping("/wechat/register")
    public ApiResponse<AuthResponse> registerWechat(@Valid @RequestBody WechatAccountRequest request) {
        return ApiResponse.success(wechatAuthService.registerWechatAccount(
                request.getCode(), request.getUsername().trim(), request.getPassword()));
    }

    // 获取当前认证用户信息。
    @Operation(summary = "获取当前用户信息", description = "从 SecurityContext 读取已登录用户，返回用户名和创建时间")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(401, "未登录"));
        }
        UserEntity user = userService.findByUsername(auth.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(401, "用户不存在"));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.getUsername());
        map.put("createdAt", user.getCreatedAt());
        return ResponseEntity.ok(ApiResponse.success(map));
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
