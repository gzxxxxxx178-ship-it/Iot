package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class DeviceShareRequest {
    @NotBlank
    @Size(max = 100)
    private String username;

    // 创建供JSON反序列化使用的共享请求
    public DeviceShareRequest() {
    }

    // 获取被授权账号名
    public String getUsername() { return username; }

    // 设置被授权账号名
    public void setUsername(String username) { this.username = username; }
}
