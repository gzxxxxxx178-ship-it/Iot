package com.ruoyi.iotsystem.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "iot_devices", indexes = {
        @Index(name = "uk_iot_device_device_id", columnList = "device_id", unique = true),
        @Index(name = "idx_iot_device_lifecycle", columnList = "lifecycle_status,enabled")
})
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true, length = 64)
    private String deviceId;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "device_type", nullable = false, length = 50)
    private String deviceType;

    @Column(length = 150)
    private String location;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private String lifecycleStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 创建供JPA反射使用的空设备实体
    public DeviceEntity() {
    }

    // 创建处于启用状态的设备档案
    public DeviceEntity(String deviceId, String deviceName, String deviceType,
            String location, String description, Boolean enabled) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.location = location;
        this.description = description;
        this.enabled = enabled;
        this.lifecycleStatus = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // 获取数据库主键
    public Long getId() { return id; }

    // 设置数据库主键
    public void setId(Long id) { this.id = id; }

    // 获取不可变设备ID
    public String getDeviceId() { return deviceId; }

    // 设置设备ID
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    // 获取设备显示名称
    public String getDeviceName() { return deviceName; }

    // 设置设备显示名称
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    // 获取设备类型
    public String getDeviceType() { return deviceType; }

    // 设置设备类型
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    // 获取设备部署位置
    public String getLocation() { return location; }

    // 设置设备部署位置
    public void setLocation(String location) { this.location = location; }

    // 获取设备备注
    public String getDescription() { return description; }

    // 设置设备备注
    public void setDescription(String description) { this.description = description; }

    // 获取业务启用状态
    public Boolean getEnabled() { return enabled; }

    // 设置业务启用状态
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    // 获取生命周期状态
    public String getLifecycleStatus() { return lifecycleStatus; }

    // 设置生命周期状态
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }

    // 获取创建时间
    public LocalDateTime getCreatedAt() { return createdAt; }

    // 设置创建时间
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // 获取更新时间
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // 设置更新时间
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
