package com.ruoyi.iotsystem.research.dto;

import com.ruoyi.iotsystem.research.entity.ExperimentMetricEntity;

import java.util.List;

/**
 * 实验运行响应对象，包含实验元信息和指标列表，用于列表和详情返回。
 */
public class ExperimentRunResponse {

    private Long id;
    private String runKey;
    private String ownerUsername;
    private String experimentType;
    private String title;
    private String sourceType;
    private String status;
    private String resultSummary;
    private String limitations;
    private String manifestPath;
    private String executedAt;
    private String createdAt;
    private List<ExperimentMetricEntity> metrics;

    /** 无参构造器 */
    public ExperimentRunResponse() {}

    // ==================== Getters & Setters ====================

    /** 获取实验ID */
    public Long getId() { return id; }
    /** 设置实验ID */
    public void setId(Long id) { this.id = id; }

    /** 获取运行键 */
    public String getRunKey() { return runKey; }
    /** 设置运行键 */
    public void setRunKey(String runKey) { this.runKey = runKey; }

    /** 获取所属用户 */
    public String getOwnerUsername() { return ownerUsername; }
    /** 设置所属用户 */
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    /** 获取实验类型 */
    public String getExperimentType() { return experimentType; }
    /** 设置实验类型 */
    public void setExperimentType(String experimentType) { this.experimentType = experimentType; }

    /** 获取实验标题 */
    public String getTitle() { return title; }
    /** 设置实验标题 */
    public void setTitle(String title) { this.title = title; }

    /** 获取来源类型 */
    public String getSourceType() { return sourceType; }
    /** 设置来源类型 */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    /** 获取实验状态 */
    public String getStatus() { return status; }
    /** 设置实验状态 */
    public void setStatus(String status) { this.status = status; }

    /** 获取结果摘要 */
    public String getResultSummary() { return resultSummary; }
    /** 设置结果摘要 */
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }

    /** 获取限制说明 */
    public String getLimitations() { return limitations; }
    /** 设置限制说明 */
    public void setLimitations(String limitations) { this.limitations = limitations; }

    /** 获取清单路径 */
    public String getManifestPath() { return manifestPath; }
    /** 设置清单路径 */
    public void setManifestPath(String manifestPath) { this.manifestPath = manifestPath; }

    /** 获取执行时间 */
    public String getExecutedAt() { return executedAt; }
    /** 设置执行时间 */
    public void setExecutedAt(String executedAt) { this.executedAt = executedAt; }

    /** 获取创建时间 */
    public String getCreatedAt() { return createdAt; }
    /** 设置创建时间 */
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /** 获取指标列表 */
    public List<ExperimentMetricEntity> getMetrics() { return metrics; }
    /** 设置指标列表 */
    public void setMetrics(List<ExperimentMetricEntity> metrics) { this.metrics = metrics; }
}
