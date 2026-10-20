package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 首次微信登录时绑定或创建系统账号的请求。 */
public class WechatAccountRequest extends WechatCodeRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 255, message = "用户名不能超过255个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 1024, message = "密码长度必须为6到1024个字符")
    private String password;

    /** 获取用于绑定或注册的系统用户名。 */
    public String getUsername() { return username; }
    /** 设置用于绑定或注册的系统用户名。 */
    public void setUsername(String username) { this.username = username; }
    /** 获取用于验证或创建账号的密码。 */
    public String getPassword() { return password; }
    /** 设置用于验证或创建账号的密码。 */
    public void setPassword(String password) { this.password = password; }
}
