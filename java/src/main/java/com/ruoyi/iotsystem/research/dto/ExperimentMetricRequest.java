package com.ruoyi.iotsystem.research.dto;

import javax.validation.constraints.*;

/**
 * 单条实验指标请求，包含指标名称、数值和可选元信息。
 */
public class ExperimentMetricRequest {

    @Size(max = 64, message = "指标分组不能超过64个字符")
    private String metricGroup;

    @Size(max = 64, message = "方法名不能超过64个字符")
    private String methodName;

    @NotBlank(message = "指标名称不能为空")
    @Size(max = 100, message = "指标名称不能超过100个字符")
    private String metricName;

    @NotNull(message = "指标值不能为空")
    private Double metricValue;

    @Size(max = 32, message = "单位不能超过32个字符")
    private String unit;

    private Boolean higherIsBetter;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String notes;

    // ==================== Getters & Setters ====================

    /** 获取指标分组 */
    public String getMetricGroup() { return metricGroup; }
    /** 设置指标分组 */
    public void setMetricGroup(String metricGroup) { this.metricGroup = metricGroup; }

    /** 获取方法名 */
    public String getMethodName() { return methodName; }
    /** 设置方法名 */
    public void setMethodName(String methodName) { this.methodName = methodName; }

    /** 获取指标名称 */
    public String getMetricName() { return metricName; }
    /** 设置指标名称 */
    public void setMetricName(String metricName) { this.metricName = metricName; }

    /** 获取指标数值 */
    public Double getMetricValue() { return metricValue; }
    /** 设置指标数值 */
    public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }

    /** 获取指标单位 */
    public String getUnit() { return unit; }
    /** 设置指标单位 */
    public void setUnit(String unit) { this.unit = unit; }

    /** 获取是否越高越好 */
    public Boolean getHigherIsBetter() { return higherIsBetter; }
    /** 设置是否越高越好 */
    public void setHigherIsBetter(Boolean higherIsBetter) { this.higherIsBetter = higherIsBetter; }

    /** 获取备注 */
    public String getNotes() { return notes; }
    /** 设置备注 */
    public void setNotes(String notes) { this.notes = notes; }
}
