package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.config.JwtUtil;
import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.entity.AuthRefreshTokenEntity;
import com.ruoyi.iotsystem.repository.AuthRefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** 签发、轮换和撤销最多30天的刷新会话。 */
@Service
public class AuthRefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthRefreshTokenRepository repository;
    private final JwtUtil jwtUtil;
    private final long validityDays;
    private final long accessTokenValidityMs;
    private final long rotationGraceSeconds;

    /** 注入令牌仓库与访问令牌配置。 */
    public AuthRefreshTokenService(AuthRefreshTokenRepository repository, JwtUtil jwtUtil,
            @Value("${auth.refresh-token.validity-days:30}") long validityDays,
            @Value("${auth.refresh-token.access-expiration-ms:900000}") long accessTokenValidityMs,
            @Value("${auth.refresh-token.rotation-grace-seconds:30}") long rotationGraceSeconds) {
        this.repository = repository;
        this.jwtUtil = jwtUtil;
        this.validityDays = validityDays;
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.rotationGraceSeconds = rotationGraceSeconds;
        if (validityDays < 1 || accessTokenValidityMs < 1 || rotationGraceSeconds < 0) {
            throw new IllegalArgumentException("刷新会话有效期配置无效");
        }
    }

    /** 为已认证账号创建固定绝对过期时间的刷新会话。 */
    @Transactional
    public AuthResponse createSession(String username) {
        LocalDateTime now = LocalDateTime.now();
        repository.deleteByExpiresAtBefore(now);
        String rawToken = newRawToken();
        LocalDateTime expiresAt = now.plusDays(validityDays);
        AuthRefreshTokenEntity entity = newEntity(UUID.randomUUID().toString(), hashToken(rawToken), username, expiresAt, now);
        repository.save(entity);
        return response(username, rawToken);
    }

    /** 原子轮换当前刷新令牌，并对已重放令牌撤销整个会话族。 */
    @Transactional
    public Optional<AuthResponse> rotate(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) return Optional.empty();
        Optional<AuthRefreshTokenEntity> found = repository.findByTokenHashForUpdate(hashToken(rawToken));
        if (!found.isPresent()) return Optional.empty();
        AuthRefreshTokenEntity current = found.get();
        LocalDateTime now = LocalDateTime.now();
        if (current.getRevokedAt() != null) {
            if (isRecoverableRotation(rawToken, current, now)) {
                return Optional.of(response(current.getUsername(), deriveNextToken(rawToken, current)));
            }
            revokeFamily(current.getFamilyId(), now);
            return Optional.empty();
        }
        if (current.getExpiresAt() == null || !current.getExpiresAt().isAfter(now)) {
            revokeFamily(current.getFamilyId(), now);
            return Optional.empty();
        }

        String nextRawToken = deriveNextToken(rawToken, current);
        String nextHash = hashToken(nextRawToken);
        current.setRevokedAt(now);
        current.setReplacedByHash(nextHash);
        repository.save(current);
        repository.save(newEntity(current.getFamilyId(), nextHash, current.getUsername(), current.getExpiresAt(), now));
        return Optional.of(response(current.getUsername(), nextRawToken));
    }

    /** 撤销刷新令牌所属的全部会话族令牌。 */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) return;
        Optional<AuthRefreshTokenEntity> found = repository.findByTokenHashForUpdate(hashToken(rawToken));
        if (found.isPresent()) revokeFamily(found.get().getFamilyId(), LocalDateTime.now());
    }

    /** 将原始令牌转换为哈希后撤销同一会话族。 */
    private void revokeFamily(String familyId, LocalDateTime now) {
        repository.revokeActiveFamily(familyId, now);
    }

    /** 在短暂网络恢复窗口内，仅重发仍然活跃的同一轮换结果。 */
    private boolean isRecoverableRotation(String rawToken, AuthRefreshTokenEntity current, LocalDateTime now) {
        if (current.getReplacedByHash() == null || current.getRevokedAt() == null
                || !current.getRevokedAt().isAfter(now.minusSeconds(rotationGraceSeconds))
                || current.getExpiresAt() == null || !current.getExpiresAt().isAfter(now)) return false;
        String expectedNextHash = hashToken(deriveNextToken(rawToken, current));
        if (!expectedNextHash.equals(current.getReplacedByHash())) return false;
        Optional<AuthRefreshTokenEntity> successor = repository.findByTokenHashForUpdate(current.getReplacedByHash());
        return successor.isPresent() && successor.get().getRevokedAt() == null
                && successor.get().getExpiresAt() != null && successor.get().getExpiresAt().isAfter(now);
    }

    /** 创建绑定指定会话族和过期时间的令牌记录。 */
    private AuthRefreshTokenEntity newEntity(String familyId, String tokenHash, String username, LocalDateTime expiresAt, LocalDateTime createdAt) {
        AuthRefreshTokenEntity entity = new AuthRefreshTokenEntity();
        entity.setFamilyId(familyId);
        entity.setTokenHash(tokenHash);
        entity.setUsername(username);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    /** 创建签名访问令牌并返回唯一一次可读取的刷新令牌。 */
    private AuthResponse response(String username, String refreshToken) {
        return new AuthResponse(jwtUtil.generateToken(username, accessTokenValidityMs), username,
                refreshToken, accessTokenValidityMs / 1000);
    }

    /** 生成256位密码学安全的不透明刷新令牌。 */
    private String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 从旧随机令牌和行标识确定性派生轮换令牌，以便短暂网络重试得到同一凭据。 */
    private String deriveNextToken(String currentRawToken, AuthRefreshTokenEntity current) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(currentRawToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String context = "auth-refresh-v1:" + current.getFamilyId() + ":" + current.getId();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(context.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("运行环境不支持HmacSHA256", error);
        }
    }

    /** 对刷新令牌计算SHA-256十六进制摘要，避免明文落库。 */
    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("运行环境不支持SHA-256", error);
        }
    }
}
