package com.ruoyi.iotsystem.simulation.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 仿真命令实体，按当前登录用户隔离。
 * 状态流转: PENDING → SUCCESS 或 FAILED，终态不可回退。
 */
@Entity
@Table(name = "simulation_commands", indexes = {
        @Index(name = "idx_sim_cmds_owner_device_status", columnList = "owner_username,device_id,status")
})
public class SimulationCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alarm_id", nullable = false)
    private Long alarmId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "owner_username", nullable = false, length = 100)
    private String ownerUsername;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 500)
    private String message;

    @Column(name = "feedback_at")
    private LocalDateTime feedbackAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 供JPA使用的空构造函数
    public SimulationCommandEntity() {
    }

    // 创建待执行的仿真命令
    public SimulationCommandEntity(Long alarmId, String deviceId, String ownerUsername,
            String action) {
        this.alarmId = alarmId;
        this.deviceId = deviceId;
        this.ownerUsername = ownerUsername;
        this.action = action;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    // 获取主键
    public Long getId() { return id; }
    // 设置主键
    public void setId(Long id) { this.id = id; }
    // 获取关联报警ID
    public Long getAlarmId() { return alarmId; }
    // 设置关联报警ID
    public void setAlarmId(Long alarmId) { this.alarmId = alarmId; }
    // 获取设备标识
    public String getDeviceId() { return deviceId; }
    // 设置设备标识
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    // 获取归属用户名
    public String getOwnerUsername() { return ownerUsername; }
    // 设置归属用户名
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
    // 获取执行动作
    public String getAction() { return action; }
    // 设置执行动作
    public void setAction(String action) { this.action = action; }
    // 获取命令状态: PENDING/SUCCESS/FAILED
    public String getStatus() { return status; }
    // 设置命令状态
    public void setStatus(String status) { this.status = status; }
    // 获取反馈消息
    public String getMessage() { return message; }
    // 设置反馈消息
    public void setMessage(String message) { this.message = message; }
    // 获取反馈时间
    public LocalDateTime getFeedbackAt() { return feedbackAt; }
    // 设置反馈时间
    public void setFeedbackAt(LocalDateTime feedbackAt) { this.feedbackAt = feedbackAt; }
    // 获取创建时间
    public LocalDateTime getCreatedAt() { return createdAt; }
    // 设置创建时间
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
