package com.ruoyi.iotsystem.research.dto;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

/**
 * 实验运行上传请求，包含实验元信息和指标列表。
 * runKey + owner 决定幂等；枚举字段使用有限值。
 */
public class ExperimentRunRequest {

    @NotBlank(message = "runKey不能为空")
    @Size(max = 128, message = "runKey不能超过128个字符")
    private String runKey;

    @NotBlank(message = "实验类型不能为空")
    @Pattern(regexp = "MPC|RL|RAG", message = "实验类型必须为MPC、RL或RAG")
    private String experimentType;

    @NotBlank(message = "实验标题不能为空")
    @Size(max = 255, message = "标题不能超过255个字符")
    private String title;

    @NotBlank(message = "来源类型不能为空")
    @Pattern(regexp = "SIMULATION|INTERNAL_BENCHMARK", message = "来源类型必须为SIMULATION或INTERNAL_BENCHMARK")
    private String sourceType;

    @NotBlank(message = "实验状态不能为空")
    @Pattern(regexp = "PASSED|FAILED|MIXED", message = "状态必须为PASSED、FAILED或MIXED")
    private String status;

    @Size(max = 5000, message = "结论摘要不能超过5000个字符")
    private String resultSummary;

    @Size(max = 2000, message = "限制说明不能超过2000个字符")
    private String limitations;

    @Size(max = 500, message = "清单路径不能超过500个字符")
    private String manifestPath;

    @NotNull(message = "执行时间不能为空")
    private String executedAt;

    @Valid
    @NotEmpty(message = "指标列表不能为空")
    @Size(min = 1, max = 200, message = "指标数量必须在1到200之间")
    private List<@Valid ExperimentMetricRequest> metrics;

    // ==================== Getters & Setters ====================

    /** 获取运行键 */
    public String getRunKey() { return runKey; }
    /** 设置运行键 */
    public void setRunKey(String runKey) { this.runKey = runKey; }

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

    /** 获取指标列表 */
    public List<ExperimentMetricRequest> getMetrics() { return metrics; }
    /** 设置指标列表 */
    public void setMetrics(List<ExperimentMetricRequest> metrics) { this.metrics = metrics; }
}
