package com.ruoyi.iotsystem.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_shares")
public class DeviceShareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "grantee_username", nullable = false, length = 100)
    private String granteeUsername;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 创建供JPA加载的空授权实体
    public DeviceShareEntity() {
    }

    // 创建设备与被授权账号之间的共享关系
    public DeviceShareEntity(String deviceId, String granteeUsername) {
        this.deviceId = deviceId;
        this.granteeUsername = granteeUsername;
        this.createdAt = LocalDateTime.now();
    }

    // 获取共享关系主键
    public Long getId() { return id; }

    // 获取共享设备ID
    public String getDeviceId() { return deviceId; }

    // 获取被授权账号名
    public String getGranteeUsername() { return granteeUsername; }

    // 获取授权创建时间
    public LocalDateTime getCreatedAt() { return createdAt; }
}
