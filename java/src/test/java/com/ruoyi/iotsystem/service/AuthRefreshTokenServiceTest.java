package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.config.JwtUtil;
import com.ruoyi.iotsystem.dto.AuthResponse;
import com.ruoyi.iotsystem.entity.AuthRefreshTokenEntity;
import com.ruoyi.iotsystem.repository.AuthRefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthRefreshTokenServiceTest {

    @Mock private AuthRefreshTokenRepository repository;
    @Mock private JwtUtil jwtUtil;

    private AuthRefreshTokenService service;

    /** 创建使用测试有效期的刷新会话服务。 */
    @BeforeEach
    void setUp() {
        service = new AuthRefreshTokenService(repository, jwtUtil, 30, 900000, 30);
        lenient().when(jwtUtil.generateToken(any(String.class), anyLong())).thenReturn("short.jwt");
    }

    /** 新会话只将刷新令牌哈希保存到数据库。 */
    @Test
    void createSession_只持久化刷新凭据哈希并返回访问令牌() {
        AuthResponse response = service.createSession("alice");

        ArgumentCaptor<AuthRefreshTokenEntity> captor = ArgumentCaptor.forClass(AuthRefreshTokenEntity.class);
        verify(repository).save(captor.capture());
        AuthRefreshTokenEntity stored = captor.getValue();
        assertEquals("alice", stored.getUsername());
        assertEquals(64, stored.getTokenHash().length());
        assertNotEquals(response.getRefreshToken(), stored.getTokenHash());
        assertEquals("short.jwt", response.getToken());
        assertEquals(900, response.getExpiresIn());
        assertTrue(stored.getExpiresAt().isAfter(LocalDateTime.now().plusDays(29)));
        assertTrue(stored.getExpiresAt().isBefore(LocalDateTime.now().plusDays(31)));
    }

    /** 轮换令牌时撤销旧记录并沿用原会话的固定过期时间。 */
    @Test
    void rotate_轮换旧令牌并保持绝对过期时间() throws Exception {
        LocalDateTime expiry = LocalDateTime.now().plusDays(20);
        AuthRefreshTokenEntity stored = token("family-1", hash("old.refresh"), "alice", expiry, null);
        when(repository.findByTokenHashForUpdate(hash("old.refresh"))).thenReturn(Optional.of(stored));
        when(repository.save(any(AuthRefreshTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = service.rotate("old.refresh").orElseThrow(AssertionError::new);

        assertEquals("alice", response.getUsername());
        assertNotEquals("old.refresh", response.getRefreshToken());
        assertNotNull(stored.getRevokedAt());
        assertEquals(hash(response.getRefreshToken()), stored.getReplacedByHash());
        ArgumentCaptor<AuthRefreshTokenEntity> captor = ArgumentCaptor.forClass(AuthRefreshTokenEntity.class);
        verify(repository, times(2)).save(captor.capture());
        AuthRefreshTokenEntity replacement = captor.getAllValues().get(1);
        assertEquals("family-1", replacement.getFamilyId());
        assertEquals(expiry, replacement.getExpiresAt());
        assertEquals(hash(response.getRefreshToken()), replacement.getTokenHash());
    }

    /** 重放已轮换的令牌时撤销该会话族的全部活跃凭据。 */
    @Test
    void rotate_检测到重放后撤销整个会话族() throws Exception {
        AuthRefreshTokenEntity replayed = token("family-2", hash("replayed"), "alice", LocalDateTime.now().plusDays(10), LocalDateTime.now().minusMinutes(1));
        when(repository.findByTokenHashForUpdate(hash("replayed"))).thenReturn(Optional.of(replayed));

        assertFalse(service.rotate("replayed").isPresent());
        verify(repository).revokeActiveFamily(eq("family-2"), any(LocalDateTime.class));
        verify(repository, never()).save(any(AuthRefreshTokenEntity.class));
    }

    /** 令牌轮换响应丢失时，在短暂恢复窗口内重发同一后继令牌。 */
    @Test
    void rotate_短暂网络重试返回同一后继令牌() throws Exception {
        LocalDateTime expiry = LocalDateTime.now().plusDays(10);
        AuthRefreshTokenEntity old = token("family-retry", hash("old-retry"), "alice", expiry, null);
        old.setId(8L);
        AuthRefreshTokenEntity successor = new AuthRefreshTokenEntity();
        successor.setFamilyId("family-retry");
        successor.setUsername("alice");
        successor.setExpiresAt(expiry);
        when(repository.findByTokenHashForUpdate(hash("old-retry"))).thenReturn(Optional.of(old));
        when(repository.save(any(AuthRefreshTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse first = service.rotate("old-retry").orElseThrow(AssertionError::new);
        successor.setTokenHash(hash(first.getRefreshToken()));
        old.setRevokedAt(LocalDateTime.now());
        when(repository.findByTokenHashForUpdate(hash(first.getRefreshToken()))).thenReturn(Optional.of(successor));

        AuthResponse retried = service.rotate("old-retry").orElseThrow(AssertionError::new);

        assertEquals(first.getRefreshToken(), retried.getRefreshToken());
        verify(repository, never()).revokeActiveFamily(any(String.class), any(LocalDateTime.class));
    }

    /** 过期刷新令牌不能签发访问令牌，并撤销会话族。 */
    @Test
    void rotate_过期令牌被拒绝并撤销会话族() throws Exception {
        AuthRefreshTokenEntity expired = token("family-3", hash("expired"), "alice", LocalDateTime.now().minusSeconds(1), null);
        when(repository.findByTokenHashForUpdate(hash("expired"))).thenReturn(Optional.of(expired));

        assertFalse(service.rotate("expired").isPresent());
        verify(repository).revokeActiveFamily(eq("family-3"), any(LocalDateTime.class));
        verify(jwtUtil, never()).generateToken(any(String.class), anyLong());
    }

    /** 退出时按刷新令牌撤销当前会话族。 */
    @Test
    void revoke_只撤销刷新令牌所属会话族() throws Exception {
        AuthRefreshTokenEntity stored = token("family-4", hash("logout"), "alice", LocalDateTime.now().plusDays(4), null);
        when(repository.findByTokenHashForUpdate(hash("logout"))).thenReturn(Optional.of(stored));

        service.revoke("logout");

        verify(repository).revokeActiveFamily(eq("family-4"), any(LocalDateTime.class));
    }

    /** 构造刷新令牌实体测试数据。 */
    private AuthRefreshTokenEntity token(String family, String tokenHash, String username, LocalDateTime expiry, LocalDateTime revokedAt) {
        AuthRefreshTokenEntity entity = new AuthRefreshTokenEntity();
        entity.setId(1L);
        entity.setFamilyId(family);
        entity.setTokenHash(tokenHash);
        entity.setUsername(username);
        entity.setExpiresAt(expiry);
        entity.setCreatedAt(LocalDateTime.now().minusDays(1));
        entity.setRevokedAt(revokedAt);
        return entity;
    }

    /** 计算测试输入的SHA-256十六进制摘要。 */
    private static String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
}
