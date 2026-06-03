package com.ruoyi.iotsystem.config;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private final byte[] keyBytes;
    private final long expiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        this.keyBytes = Base64.getDecoder().decode(secret);
        this.expiration = expiration;
    }

    // 生成JWT令牌，24小时有效
    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(SignatureAlgorithm.HS256, keyBytes)
                .compact();
    }

    // 从JWT中提取用户名
    public String extractUsername(String token) {
        return Jwts.parser()
                .setSigningKey(keyBytes)
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // 验证JWT令牌是否有效
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(keyBytes).parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
