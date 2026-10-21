package com.ruoyi.iotsystem.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_commands", indexes = @Index(name = "idx_device_command_owner_created", columnList = "owner_username,created_at"))
public class DeviceCommandEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "command_id", nullable = false, unique = true, length = 64) private String commandId;
    @Column(name = "device_id", nullable = false, length = 64) private String deviceId;
    @Column(name = "owner_username", length = 100) private String ownerUsername;
    @Column(nullable = false, length = 16) private String command;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "dispatched_at") private LocalDateTime dispatchedAt;
    @Column(name = "acknowledged_at") private LocalDateTime acknowledgedAt;
    @Column(length = 500) private String message;
    // 创建供JPA使用的空命令记录。
    public DeviceCommandEntity() { }
    // 创建尚未派发的持久化命令意图。
    public DeviceCommandEntity(String commandId, String deviceId, String ownerUsername, String command) {
        this.commandId = commandId; this.deviceId = deviceId; this.ownerUsername = ownerUsername;
        this.command = command; this.status = "PENDING"; this.createdAt = LocalDateTime.now();
    }
    // 获取数据库主键。
    public Long getId() { return id; }
    // 获取用于设备幂等处理的命令标识。
    public String getCommandId() { return commandId; }
    // 获取目标设备标识。
    public String getDeviceId() { return deviceId; }
    // 获取发起账号。
    public String getOwnerUsername() { return ownerUsername; }
    // 获取命令动作。
    public String getCommand() { return command; }
    // 获取审计状态。
    public String getStatus() { return status; }
    // 获取意图创建时间。
    public LocalDateTime getCreatedAt() { return createdAt; }
    // 获取抢占派发时间，未派发时为空。
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    // 获取设备确认时间。
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    // 获取状态说明。
    public String getMessage() { return message; }
    // 设置状态，生产状态流转由仓库条件更新维护。
    public void setStatus(String status) { this.status = status; }
    // 设置设备确认时间。
    public void setAcknowledgedAt(LocalDateTime value) { this.acknowledgedAt = value; }
    // 设置状态说明。
    public void setMessage(String message) { this.message = message; }
}
