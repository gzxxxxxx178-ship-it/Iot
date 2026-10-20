package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 刷新或撤销小程序会话的请求体。 */
public class RefreshTokenRequest {

    @NotBlank(message = "刷新凭据不能为空")
    @Size(max = 128, message = "刷新凭据格式无效")
    private String refreshToken;

    /** 获取刷新凭据。 */
    public String getRefreshToken() { return refreshToken; }

    /** 设置刷新凭据。 */
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
