package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.AuthRefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

/** 管理刷新会话持久化和原子轮换所需的行锁。 */
@Repository
public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshTokenEntity, Long> {

    /** 按哈希读取并锁定刷新令牌，避免同一令牌并发轮换。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AuthRefreshTokenEntity token where token.tokenHash = :tokenHash")
    Optional<AuthRefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** 撤销会话族中所有仍有效的令牌。 */
    @Modifying
    @Query("update AuthRefreshTokenEntity token set token.revokedAt = :revokedAt where token.familyId = :familyId and token.revokedAt is null")
    int revokeActiveFamily(@Param("familyId") String familyId, @Param("revokedAt") LocalDateTime revokedAt);

    /** 清理已超过绝对有效期的历史令牌。 */
    long deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
