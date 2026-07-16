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
@Table(name = "alarm_records", indexes = {
        @Index(name = "idx_alarm_record_rule_device_time", columnList = "rule_id,device_id,created_at"),
        @Index(name = "idx_alarm_record_created_at", columnList = "created_at")
})
public class AlarmRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "owner_username", length = 100)
    private String ownerUsername;

    @Column(nullable = false)
    private String metric;

    @Column(name = "comparison_operator", nullable = false)
    private String operator;

    @Column(nullable = false)
    private Double threshold;

    @Column(name = "actual_value", nullable = false)
    private Double actualValue;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 创建供JPA使用的空报警记录
    public AlarmRecordEntity() {
    }

    // 创建包含触发快照的报警记录
    public AlarmRecordEntity(
            Long ruleId,
            String deviceId,
            String metric,
            String operator,
            Double threshold,
            Double actualValue,
            String message) {
        this.ruleId = ruleId;
        this.deviceId = deviceId;
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
        this.actualValue = actualValue;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    // 创建带用户归属的报警记录
    public AlarmRecordEntity(Long ruleId, String deviceId, String metric, String operator,
            Double threshold, Double actualValue, String message, String ownerUsername) {
        this(ruleId, deviceId, metric, operator, threshold, actualValue, message);
        this.ownerUsername = ownerUsername;
    }

    // 获取记录主键
    public Long getId() {
        return id;
    }

    // 设置记录主键
    public void setId(Long id) {
        this.id = id;
    }

    // 获取触发规则主键
    public Long getRuleId() {
        return ruleId;
    }

    // 设置触发规则主键
    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    // 获取触发设备
    public String getDeviceId() {
        return deviceId;
    }

    // 设置触发设备
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    // 获取记录归属用户名
    public String getOwnerUsername() { return ownerUsername; }

    // 设置记录归属用户名
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    // 获取触发指标
    public String getMetric() {
        return metric;
    }

    // 设置触发指标
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

    // 获取触发时实测值
    public Double getActualValue() {
        return actualValue;
    }

    // 设置触发时实测值
    public void setActualValue(Double actualValue) {
        this.actualValue = actualValue;
    }

    // 获取报警描述
    public String getMessage() {
        return message;
    }

    // 设置报警描述
    public void setMessage(String message) {
        this.message = message;
    }

    // 获取报警发生时间
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // 设置报警发生时间
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
