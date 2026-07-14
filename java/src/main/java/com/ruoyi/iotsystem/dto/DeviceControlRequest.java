package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class DeviceControlRequest {

    @NotBlank(message = "设备ID不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "设备ID格式无效")
    private String deviceId;

    @NotBlank(message = "控制指令不能为空")
    @Pattern(regexp = "start|stop|read|status", message = "控制指令不受支持")
    private String command;

    // 获取目标设备ID
    public String getDeviceId() { return deviceId; }

    // 设置目标设备ID
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    // 获取控制指令
    public String getCommand() { return command; }

    // 设置控制指令
    public void setCommand(String command) { this.command = command; }
}
