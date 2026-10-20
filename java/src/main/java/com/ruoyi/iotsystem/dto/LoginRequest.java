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

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
