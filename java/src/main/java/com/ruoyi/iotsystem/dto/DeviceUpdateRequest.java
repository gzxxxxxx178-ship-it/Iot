package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class DeviceUpdateRequest {

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称不能超过100个字符")
    private String deviceName;

    @Size(max = 50, message = "设备类型不能超过50个字符")
    private String deviceType;

    @Size(max = 150, message = "部署位置不能超过150个字符")
    private String location;

    @Size(max = 500, message = "设备备注不能超过500个字符")
    private String description;

    private Boolean enabled;

    // 获取设备名称
    public String getDeviceName() { return deviceName; }

    // 设置设备名称
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    // 获取设备类型
    public String getDeviceType() { return deviceType; }

    // 设置设备类型
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    // 获取部署位置
    public String getLocation() { return location; }

    // 设置部署位置
    public void setLocation(String location) { this.location = location; }

    // 获取设备备注
    public String getDescription() { return description; }

    // 设置设备备注
    public void setDescription(String description) { this.description = description; }

    // 获取启用状态
    public Boolean getEnabled() { return enabled; }

    // 设置启用状态
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
