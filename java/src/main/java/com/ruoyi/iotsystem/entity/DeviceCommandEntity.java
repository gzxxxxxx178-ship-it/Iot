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
    @Column(name = "acknowledged_at") private LocalDateTime acknowledgedAt;
    @Column(length = 500) private String message;
    public DeviceCommandEntity() { }
    public DeviceCommandEntity(String commandId, String deviceId, String ownerUsername, String command) {
        this.commandId = commandId; this.deviceId = deviceId; this.ownerUsername = ownerUsername;
        this.command = command; this.status = "PENDING"; this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; } public String getCommandId() { return commandId; }
    public String getDeviceId() { return deviceId; } public String getOwnerUsername() { return ownerUsername; }
    public String getCommand() { return command; } public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; } public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public String getMessage() { return message; }
    public void setStatus(String status) { this.status = status; }
    public void setAcknowledgedAt(LocalDateTime value) { this.acknowledgedAt = value; }
    public void setMessage(String message) { this.message = message; }
}
