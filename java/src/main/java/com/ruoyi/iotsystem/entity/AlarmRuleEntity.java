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
@Table(name = "alarm_rules", indexes = {
        @Index(name = "idx_alarm_rule_enabled", columnList = "enabled")
})
public class AlarmRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String metric;

    @Column(name = "comparison_operator", nullable = false)
    private String operator;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "owner_username", length = 100)
    private String ownerUsername;

    @Column(name = "cooldown_seconds", nullable = false)
    private Integer cooldownSeconds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 创建供JPA使用的空报警规则
    public AlarmRuleEntity() {
    }

    // 创建包含完整判断条件的报警规则
    public AlarmRuleEntity(
            String metric,
            String operator,
            Double threshold,
            Boolean enabled,
            String deviceId,
            Integer cooldownSeconds) {
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
        this.enabled = enabled;
        this.deviceId = deviceId;
        this.cooldownSeconds = cooldownSeconds;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // 创建带用户归属的报警规则
    public AlarmRuleEntity(String metric, String operator, Double threshold, Boolean enabled,
            String deviceId, Integer cooldownSeconds, String ownerUsername) {
        this(metric, operator, threshold, enabled, deviceId, cooldownSeconds);
        this.ownerUsername = ownerUsername;
    }

    // 获取规则主键
    public Long getId() {
        return id;
    }

    // 设置规则主键
    public void setId(Long id) {
        this.id = id;
    }

    // 获取监控指标
    public String getMetric() {
        return metric;
    }

    // 设置监控指标
    public void setMetric(String metric) {
        this.metric = metric;
    }

    // 获取比较运算符
    public String getOperator() {
        return operator;
    }

    // 设置比较运算符
    public void setOperator(String operator) {
        this.operator = operator;
    }

    // 获取报警阈值
    public Double getThreshold() {
        return threshold;
    }

    // 设置报警阈值
    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    // 获取启用状态
    public Boolean getEnabled() {
        return enabled;
    }

    // 设置启用状态
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    // 获取规则适用设备
    public String getDeviceId() {
        return deviceId;
    }

    // 设置规则适用设备
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    // 获取规则归属用户名
    public String getOwnerUsername() { return ownerUsername; }

    // 设置规则归属用户名
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    // 获取重复报警冷却秒数
    public Integer getCooldownSeconds() {
        return cooldownSeconds;
    }

    // 设置重复报警冷却秒数
    public void setCooldownSeconds(Integer cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    // 获取创建时间
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // 设置创建时间
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // 获取更新时间
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // 设置更新时间
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
