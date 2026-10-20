package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 注册请求体：用户名为1到100个字符、密码至少6位
 */
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 100, message = "用户名不能超过100个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度至少6位")
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
