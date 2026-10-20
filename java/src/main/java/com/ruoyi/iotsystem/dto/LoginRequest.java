package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 登录请求体：用户名为1到255个字符，密码不能为空
 */
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 255, message = "用户名不能超过255个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private boolean rememberMe;

    /** 获取用户名。 */
    public String getUsername() { return username; }
    /** 设置用户名。 */
    public void setUsername(String username) { this.username = username; }

    /** 获取密码。 */
    public String getPassword() { return password; }
    /** 设置密码。 */
    public void setPassword(String password) { this.password = password; }

    /** 判断是否创建可恢复登录会话。 */
    public boolean isRememberMe() { return rememberMe; }
    /** 设置是否创建可恢复登录会话。 */
    public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }
}
