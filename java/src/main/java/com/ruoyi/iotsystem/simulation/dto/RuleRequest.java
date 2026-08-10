package com.ruoyi.iotsystem.simulation.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 仿真规则创建/更新请求体。
 * 支持gt/lt比较、恢复滞回和防抖计数。
 */
public class RuleRequest {

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称不能超过100个字符")
    private String name;

    @NotBlank(message = "设备ID不能为空")
    @Size(max = 64, message = "设备ID不能超过64个字符")
    private String deviceId;

    @NotBlank(message = "监控指标不能为空")
    @Pattern(regexp = "waterLevelMm|flowRateLMin|ecMsCm|soilMoisturePct|rainfallMm",
            message = "无效的监控指标")
    private String metric;

    @NotBlank(message = "比较运算符不能为空")
    @Pattern(regexp = "gt|lt", message = "比较运算符只能是gt或lt")
    private String operator;

    @NotNull(message = "触发阈值不能为空")
    private Double threshold;

    @NotNull(message = "恢复阈值不能为空")
    private Double recoveryThreshold;

    @Min(value = 1, message = "防抖次数不能小于1")
    @Max(value = 100, message = "防抖次数不能超过100")
    private Integer debounceCount;

    @NotBlank(message = "严重级别不能为空")
    @Pattern(regexp = "INFO|WARN|CRITICAL", message = "严重级别无效")
    private String severity;

    @NotBlank(message = "触发动作不能为空")
    @Pattern(regexp = "NOTIFY|STOP_IRRIGATION|STOP_FERTILIZER|STOP_ALL", message = "触发动作无效")
    private String action;

    private Boolean enabled;

    // 获取规则名称
    public String getName() { return name; }
    // 设置规则名称
    public void setName(String name) { this.name = name; }
    // 获取设备ID
    public String getDeviceId() { return deviceId; }
    // 设置设备ID
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
    // 获取恢复阈值
    public Double getRecoveryThreshold() { return recoveryThreshold; }
    // 设置恢复阈值
    public void setRecoveryThreshold(Double recoveryThreshold) { this.recoveryThreshold = recoveryThreshold; }
    // 获取防抖计数
    public Integer getDebounceCount() { return debounceCount; }
    // 设置防抖计数
    public void setDebounceCount(Integer debounceCount) { this.debounceCount = debounceCount; }
    // 获取严重级别
    public String getSeverity() { return severity; }
    // 设置严重级别
    public void setSeverity(String severity) { this.severity = severity; }
    // 获取触发动作
    public String getAction() { return action; }
    // 设置触发动作
    public void setAction(String action) { this.action = action; }
    // 获取启用状态
    public Boolean getEnabled() { return enabled; }
    // 设置启用状态
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
