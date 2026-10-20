package com.ruoyi.iotsystem.dto;

public class AuthResponse {
    private String token;
    private String username;
    private String refreshToken;
    private long expiresIn;

    public AuthResponse() {}

    public AuthResponse(String token, String username) {
        this.token = token;
        this.username = username;
    }

    public AuthResponse(String token, String username, String refreshToken, long expiresIn) {
        this.token = token;
        this.username = username;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    /** 读取刷新凭据。 */
    public String getRefreshToken() { return refreshToken; }
    /** 设置刷新凭据。 */
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    /** 读取访问令牌有效秒数。 */
    public long getExpiresIn() { return expiresIn; }
    /** 设置访问令牌有效秒数。 */
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
}
