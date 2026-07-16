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
@Table(name = "automation_executions", indexes = {
        @Index(name = "idx_automation_execution_created", columnList = "created_at"),
        @Index(name = "idx_automation_execution_rule", columnList = "rule_id,created_at")
})
public class AutomationExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "owner_username", length = 100)
    private String ownerUsername;

    @Column(nullable = false, length = 16)
    private String action;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "actual_value")
    private Double actualValue;

    @Column(length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 创建供JPA使用的空执行记录
    public AutomationExecutionEntity() {
    }

    // 创建自动化动作执行审计记录
    public AutomationExecutionEntity(Long ruleId, String deviceId, String action,
            String status, Double actualValue, String message) {
        this.ruleId = ruleId;
        this.deviceId = deviceId;
        this.action = action;
        this.status = status;
        this.actualValue = actualValue;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    // 创建带用户归属的自动化执行记录
    public AutomationExecutionEntity(Long ruleId, String deviceId, String action, String status,
            Double actualValue, String message, String ownerUsername) {
        this(ruleId, deviceId, action, status, actualValue, message);
        this.ownerUsername = ownerUsername;
    }

    // 获取记录主键
    public Long getId() { return id; }
    // 设置记录主键
    public void setId(Long id) { this.id = id; }
    // 获取来源规则主键
    public Long getRuleId() { return ruleId; }
    // 设置来源规则主键
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    // 获取目标设备ID
    public String getDeviceId() { return deviceId; }
    // 设置目标设备ID
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    // 获取记录归属用户名
    public String getOwnerUsername() { return ownerUsername; }
    // 设置记录归属用户名
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
    // 获取执行动作
    public String getAction() { return action; }
    // 设置执行动作
    public void setAction(String action) { this.action = action; }
    // 获取执行状态
    public String getStatus() { return status; }
    // 设置执行状态
    public void setStatus(String status) { this.status = status; }
    // 获取触发时的实测值
    public Double getActualValue() { return actualValue; }
    // 设置触发时的实测值
    public void setActualValue(Double actualValue) { this.actualValue = actualValue; }
    // 获取执行结果描述
    public String getMessage() { return message; }
    // 设置执行结果描述
    public void setMessage(String message) { this.message = message; }
    // 获取记录时间
    public LocalDateTime getCreatedAt() { return createdAt; }
    // 设置记录时间
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
