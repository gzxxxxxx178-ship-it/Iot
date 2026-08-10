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
 * 仿真报警记录实体，按当前登录用户隔离。
 * 状态流转: ACTIVE → ACKNOWLEDGED → RESOLVED，或 ACTIVE → RESOLVED。
 */
@Entity
@Table(name = "simulation_alarms", indexes = {
        @Index(name = "idx_sim_alarms_owner_device_status", columnList = "owner_username,device_id,status"),
        @Index(name = "idx_sim_alarms_rule_device", columnList = "rule_id,device_id")
})
public class SimulationAlarmEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "owner_username", nullable = false, length = 100)
    private String ownerUsername;

    @Column(nullable = false, length = 32)
    private String metric;

    @Column(name = "comparison_operator", nullable = false, length = 8)
    private String operator;

    @Column(nullable = false)
    private Double threshold;

    @Column(name = "recovery_threshold", nullable = false)
    private Double recoveryThreshold;

    @Column(name = "actual_value", nullable = false)
    private Double actualValue;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 500)
    private String message;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_value")
    private Double resolutionValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 供JPA使用的空构造函数
    public SimulationAlarmEntity() {
    }

    // 创建触发的报警记录
    public SimulationAlarmEntity(Long ruleId, String deviceId, String ownerUsername,
            String metric, String operator, Double threshold, Double recoveryThreshold,
            Double actualValue, String severity, String action, String message) {
        this.ruleId = ruleId;
        this.deviceId = deviceId;
        this.ownerUsername = ownerUsername;
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
        this.recoveryThreshold = recoveryThreshold;
        this.actualValue = actualValue;
        this.severity = severity;
        this.action = action;
        this.status = "ACTIVE";
        this.message = message;
        this.triggeredAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    // 获取主键
    public Long getId() { return id; }
    // 设置主键
    public void setId(Long id) { this.id = id; }
    // 获取触发规则ID
    public Long getRuleId() { return ruleId; }
    // 设置触发规则ID
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    // 获取设备标识
    public String getDeviceId() { return deviceId; }
    // 设置设备标识
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    // 获取归属用户名
    public String getOwnerUsername() { return ownerUsername; }
    // 设置归属用户名
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
    // 获取监控指标
    public String getMetric() { return metric; }
    // 设置监控指标
    public void setMetric(String metric) { this.metric = metric; }
    // 获取比较运算符
    public String getOperator() { return operator; }
    // 设置比较运算符
    public void setOperator(String operator) { this.operator = operator; }
    // 获取触发阈值
    public Double getThreshold() { return threshold; }
    // 设置触发阈值
    public void setThreshold(Double threshold) { this.threshold = threshold; }
    // 获取恢复阈值
    public Double getRecoveryThreshold() { return recoveryThreshold; }
    // 设置恢复阈值
    public void setRecoveryThreshold(Double recoveryThreshold) { this.recoveryThreshold = recoveryThreshold; }
    // 获取触发时实测值
    public Double getActualValue() { return actualValue; }
    // 设置触发时实测值
    public void setActualValue(Double actualValue) { this.actualValue = actualValue; }
    // 获取报警严重级别
    public String getSeverity() { return severity; }
    // 设置报警严重级别
    public void setSeverity(String severity) { this.severity = severity; }
    // 获取触发动作
    public String getAction() { return action; }
    // 设置触发动作
    public void setAction(String action) { this.action = action; }
    // 获取报警状态: ACTIVE/ACKNOWLEDGED/RESOLVED
    public String getStatus() { return status; }
    // 设置报警状态
    public void setStatus(String status) { this.status = status; }
    // 获取报警描述
    public String getMessage() { return message; }
    // 设置报警描述
    public void setMessage(String message) { this.message = message; }
    // 获取触发时间
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    // 设置触发时间
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
    // 获取确认时间
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    // 设置确认时间
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    // 获取恢复时间
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    // 设置恢复时间
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    // 获取恢复时的实测值
    public Double getResolutionValue() { return resolutionValue; }
    // 设置恢复时的实测值
    public void setResolutionValue(Double resolutionValue) { this.resolutionValue = resolutionValue; }
    // 获取记录创建时间
    public LocalDateTime getCreatedAt() { return createdAt; }
    // 设置记录创建时间
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
