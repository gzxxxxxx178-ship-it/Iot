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
@Table(name = "automation_rules", indexes = {
        @Index(name = "idx_automation_rule_enabled_device", columnList = "enabled,device_id")
})
public class AutomationRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(nullable = false, length = 32)
    private String metric;

    @Column(name = "comparison_operator", nullable = false, length = 8)
    private String operator;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false, length = 16)
    private String action;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "debounce_count", nullable = false)
    private Integer debounceCount;

    @Column(name = "cooldown_seconds", nullable = false)
    private Integer cooldownSeconds;

    @Column(name = "consecutive_matches", nullable = false)
    private Integer consecutiveMatches;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 创建供JPA使用的空自动化规则
    public AutomationRuleEntity() {
    }

    // 创建包含条件、动作、防抖和冷却参数的自动化规则
    public AutomationRuleEntity(String name, String deviceId, String metric, String operator,
            Double threshold, String action, Boolean enabled, Integer debounceCount,
            Integer cooldownSeconds) {
        this.name = name;
        this.deviceId = deviceId;
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
        this.action = action;
        this.enabled = enabled;
        this.debounceCount = debounceCount;
        this.cooldownSeconds = cooldownSeconds;
        this.consecutiveMatches = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // 获取规则主键
    public Long getId() { return id; }
    // 设置规则主键
    public void setId(Long id) { this.id = id; }
    // 获取规则名称
    public String getName() { return name; }
    // 设置规则名称
    public void setName(String name) { this.name = name; }
    // 获取目标设备ID
    public String getDeviceId() { return deviceId; }
    // 设置目标设备ID
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
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
    // 获取触发动作
    public String getAction() { return action; }
    // 设置触发动作
    public void setAction(String action) { this.action = action; }
    // 获取启用状态
    public Boolean getEnabled() { return enabled; }
    // 设置启用状态
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    // 获取连续命中次数要求
    public Integer getDebounceCount() { return debounceCount; }
    // 设置连续命中次数要求
    public void setDebounceCount(Integer debounceCount) { this.debounceCount = debounceCount; }
    // 获取动作冷却秒数
    public Integer getCooldownSeconds() { return cooldownSeconds; }
    // 设置动作冷却秒数
    public void setCooldownSeconds(Integer cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    // 获取当前连续命中次数
    public Integer getConsecutiveMatches() { return consecutiveMatches; }
    // 设置当前连续命中次数
    public void setConsecutiveMatches(Integer consecutiveMatches) { this.consecutiveMatches = consecutiveMatches; }
    // 获取最近触发时间
    public LocalDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    // 设置最近触发时间
    public void setLastTriggeredAt(LocalDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
    // 获取创建时间
    public LocalDateTime getCreatedAt() { return createdAt; }
    // 设置创建时间
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    // 获取更新时间
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    // 设置更新时间
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
