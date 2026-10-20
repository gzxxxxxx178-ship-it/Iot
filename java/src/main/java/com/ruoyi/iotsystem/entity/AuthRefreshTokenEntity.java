package com.ruoyi.iotsystem.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/** 保存已哈希的可轮换刷新凭据及其会话族状态。 */
@Entity
@Table(name = "auth_refresh_sessions", indexes = {
        @Index(name = "uk_auth_refresh_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_auth_refresh_family", columnList = "family_id"),
        @Index(name = "idx_auth_refresh_expiry", columnList = "expires_at")
})
public class AuthRefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_hash", length = 64)
    private String replacedByHash;

    /** 读取数据库主键。 */
    public Long getId() { return id; }

    /** 设置数据库主键。 */
    public void setId(Long id) { this.id = id; }

    /** 读取会话族标识。 */
    public String getFamilyId() { return familyId; }

    /** 设置会话族标识。 */
    public void setFamilyId(String familyId) { this.familyId = familyId; }

    /** 读取不可逆的令牌哈希。 */
    public String getTokenHash() { return tokenHash; }

    /** 设置不可逆的令牌哈希。 */
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    /** 读取令牌所属账号。 */
    public String getUsername() { return username; }

    /** 设置令牌所属账号。 */
    public void setUsername(String username) { this.username = username; }

    /** 读取会话绝对过期时间。 */
    public LocalDateTime getExpiresAt() { return expiresAt; }

    /** 设置会话绝对过期时间。 */
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    /** 读取当前令牌创建时间。 */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** 设置当前令牌创建时间。 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** 读取令牌撤销时间。 */
    public LocalDateTime getRevokedAt() { return revokedAt; }

    /** 设置令牌撤销时间。 */
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }

    /** 读取轮换后令牌的哈希。 */
    public String getReplacedByHash() { return replacedByHash; }

    /** 设置轮换后令牌的哈希。 */
    public void setReplacedByHash(String replacedByHash) { this.replacedByHash = replacedByHash; }
}
