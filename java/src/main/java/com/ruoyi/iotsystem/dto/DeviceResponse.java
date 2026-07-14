package com.ruoyi.iotsystem.dto;

import com.ruoyi.iotsystem.entity.DeviceEntity;
import com.ruoyi.iotsystem.entity.EspEntity;

import java.time.LocalDateTime;

public class DeviceResponse {

    private final Long id;
    private final String deviceId;
    private final String deviceName;
    private final String deviceType;
    private final String location;
    private final String description;
    private final Boolean enabled;
    private final String lifecycleStatus;
    private final String status;
    private final LocalDateTime lastSeen;
    private final Double temperature;
    private final Double humidity;
    private final Double water;
    private final Integer rssi;
    private final Boolean linkage;
    private final Integer sendCount;
    private final Boolean qualityValid;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    // 组合设备档案、最新读数和在线状态形成接口响应
    public DeviceResponse(DeviceEntity device, EspEntity latestReading, boolean online) {
        this.id = device.getId();
        this.deviceId = device.getDeviceId();
        this.deviceName = device.getDeviceName();
        this.deviceType = device.getDeviceType();
        this.location = device.getLocation();
        this.description = device.getDescription();
        this.enabled = device.getEnabled();
        this.lifecycleStatus = device.getLifecycleStatus();
        this.status = online ? "online" : "offline";
        this.lastSeen = latestReading == null ? null : latestReading.getServerReceivedTime();
        this.temperature = latestReading == null ? null : latestReading.getTemperature();
        this.humidity = latestReading == null ? null : latestReading.getHumidity();
        this.water = latestReading == null ? null : latestReading.getWater();
        this.rssi = latestReading == null ? null : latestReading.getRssi();
        this.linkage = latestReading == null ? null : latestReading.getLinkage();
        this.sendCount = latestReading == null ? null : latestReading.getSendCount();
        this.qualityValid = latestReading == null ? null : latestReading.getQualityValid();
        this.createdAt = device.getCreatedAt();
        this.updatedAt = device.getUpdatedAt();
    }

    // 获取设备主键
    public Long getId() { return id; }

    // 获取设备ID
    public String getDeviceId() { return deviceId; }

    // 获取设备名称
    public String getDeviceName() { return deviceName; }

    // 获取设备类型
    public String getDeviceType() { return deviceType; }

    // 获取部署位置
    public String getLocation() { return location; }

    // 获取设备备注
    public String getDescription() { return description; }

    // 获取业务启用状态
    public Boolean getEnabled() { return enabled; }

    // 获取生命周期状态
    public String getLifecycleStatus() { return lifecycleStatus; }

    // 获取实时在线状态
    public String getStatus() { return status; }

    // 获取最后上报时间
    public LocalDateTime getLastSeen() { return lastSeen; }

    // 获取最新温度
    public Double getTemperature() { return temperature; }

    // 获取最新湿度
    public Double getHumidity() { return humidity; }

    // 获取最新水位ADC
    public Double getWater() { return water; }

    // 获取最新信号强度
    public Integer getRssi() { return rssi; }

    // 获取最新联动状态
    public Boolean getLinkage() { return linkage; }

    // 获取最新发送次数
    public Integer getSendCount() { return sendCount; }

    // 获取最新数据质量状态
    public Boolean getQualityValid() { return qualityValid; }

    // 获取档案创建时间
    public LocalDateTime getCreatedAt() { return createdAt; }

    // 获取档案更新时间
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
