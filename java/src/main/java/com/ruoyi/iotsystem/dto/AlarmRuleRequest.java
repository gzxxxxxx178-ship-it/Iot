package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class AlarmRuleRequest {

    @NotBlank(message = "监控指标不能为空")
    @Pattern(regexp = "temperature|humidity|water", message = "监控指标仅支持temperature、humidity或water")
    private String metric;

    @NotBlank(message = "比较运算符不能为空")
    @Pattern(regexp = "gt|lt|eq", message = "比较运算符仅支持gt、lt或eq")
    private String operator;

    @NotNull(message = "报警阈值不能为空")
    private Double threshold;

    private Boolean enabled = true;

    private String deviceId = "*";

    @Min(value = 0, message = "冷却时间不能小于0秒")
    @Max(value = 86400, message = "冷却时间不能超过86400秒")
    private Integer cooldownSeconds = 300;

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

    // 获取重复报警冷却秒数
    public Integer getCooldownSeconds() {
        return cooldownSeconds;
    }

    // 设置重复报警冷却秒数
    public void setCooldownSeconds(Integer cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }
}
