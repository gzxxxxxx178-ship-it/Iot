package com.ruoyi.iotsystem.research.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 研究实验指标实体，关联到一次实验运行的单一指标值。
 */
@Entity
@Table(name = "research_experiment_metrics")
public class ExperimentMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "metric_group", length = 64)
    private String metricGroup;

    @Column(name = "method_name", length = 64)
    private String methodName;

    @Column(name = "metric_name", length = 100, nullable = false)
    private String metricName;

    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @Column(length = 32)
    private String unit;

    @Column(name = "higher_is_better")
    private Boolean higherIsBetter;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 无参构造器，供JPA使用 */
    public ExperimentMetricEntity() {}

    // ==================== Getters & Setters ====================

    /** 获取数据库主键ID */
    public Long getId() { return id; }
    /** 设置数据库主键ID */
    public void setId(Long id) { this.id = id; }

    /** 获取关联的运行ID */
    public Long getRunId() { return runId; }
    /** 设置关联的运行ID */
    public void setRunId(Long runId) { this.runId = runId; }

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

    /** 获取记录创建时间 */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** 设置记录创建时间 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
