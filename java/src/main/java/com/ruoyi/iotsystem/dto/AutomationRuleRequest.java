package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class AutomationRuleRequest {

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称不能超过100个字符")
    private String name;

    @NotBlank(message = "设备ID不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "设备ID格式无效")
    private String deviceId;

    @NotBlank(message = "监控指标不能为空")
    @Pattern(regexp = "temperature|humidity|water", message = "监控指标无效")
    private String metric;

    @NotBlank(message = "比较运算符不能为空")
    @Pattern(regexp = "gt|lt|eq", message = "比较运算符无效")
    private String operator;

    @NotNull(message = "触发阈值不能为空")
    private Double threshold;

    @NotBlank(message = "执行动作不能为空")
    @Pattern(regexp = "start|stop|notify", message = "执行动作无效")
    private String action;

    private Boolean enabled = true;

    @Min(value = 1, message = "防抖次数不能小于1")
    @Max(value = 20, message = "防抖次数不能超过20")
    private Integer debounceCount = 2;

    @Min(value = 0, message = "冷却时间不能小于0秒")
    @Max(value = 86400, message = "冷却时间不能超过86400秒")
    private Integer cooldownSeconds = 300;

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
    // 获取执行动作
    public String getAction() { return action; }
    // 设置执行动作
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
}
