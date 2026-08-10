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
 * 仿真报警规则实体，按当前登录用户隔离。
 * 支持gt/lt双阈值、恢复滞回和防抖计数。
 */
@Entity
@Table(name = "simulation_rules", indexes = {
        @Index(name = "idx_sim_rules_owner_device", columnList = "owner_username,device_id"),
        @Index(name = "idx_sim_rules_owner_enabled", columnList = "owner_username,enabled")
})
public class SimulationRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

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

    @Column(name = "debounce_count", nullable = false)
    private Integer debounceCount;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 供JPA使用的空构造函数
    public SimulationRuleEntity() {
    }

    // 创建带完整参数的仿真报警规则
    public SimulationRuleEntity(String name, String deviceId, String ownerUsername,
            String metric, String operator, Double threshold, Double recoveryThreshold,
            Integer debounceCount, String severity, String action, Boolean enabled) {
        this.name = name;
        this.deviceId = deviceId;
        this.ownerUsername = ownerUsername;
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
        this.recoveryThreshold = recoveryThreshold;
        this.debounceCount = debounceCount;
        this.severity = severity;
        this.action = action;
        this.enabled = enabled;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // 获取主键
    public Long getId() { return id; }
    // 设置主键
    public void setId(Long id) { this.id = id; }
    // 获取规则名称
    public String getName() { return name; }
    // 设置规则名称
    public void setName(String name) { this.name = name; }
    // 获取目标设备ID，*表示全部设备
    public String getDeviceId() { return deviceId; }
    // 设置目标设备ID
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
    // 获取防抖计数
    public Integer getDebounceCount() { return debounceCount; }
    // 设置防抖计数
    public void setDebounceCount(Integer debounceCount) { this.debounceCount = debounceCount; }
    // 获取报警严重级别
    public String getSeverity() { return severity; }
    // 设置报警严重级别
    public void setSeverity(String severity) { this.severity = severity; }
    // 获取触发动作
    public String getAction() { return action; }
    // 设置触发动作
    public void setAction(String action) { this.action = action; }
    // 获取启用状态
    public Boolean getEnabled() { return enabled; }
    // 设置启用状态
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    // 获取创建时间
    public LocalDateTime getCreatedAt() { return createdAt; }
    // 设置创建时间
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    // 获取更新时间
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    // 设置更新时间
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
