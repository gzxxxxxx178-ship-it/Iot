package com.ruoyi.iotsystem.research.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 研究实验运行实体，记录一次完整实验的元信息和结论。
 * owner_username 由服务端填写，不接受客户端传入。
 */
@Entity
@Table(name = "research_experiment_runs")
public class ExperimentRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_key", length = 128, nullable = false)
    private String runKey;

    @Column(name = "owner_username", length = 100, nullable = false)
    private String ownerUsername;

    /** 实验类型：MPC/RL/RAG */
    @Column(name = "experiment_type", length = 32, nullable = false)
    private String experimentType;

    @Column(length = 255, nullable = false)
    private String title;

    /** 数据来源固定为 SIMULATION 或 INTERNAL_BENCHMARK */
    @Column(name = "source_type", length = 32, nullable = false)
    private String sourceType;

    /** 实验结论：PASSED/FAILED/MIXED */
    @Column(length = 16, nullable = false)
    private String status;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(columnDefinition = "TEXT")
    private String limitations;

    @Column(name = "manifest_path", length = 500)
    private String manifestPath;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 无参构造器，供JPA使用 */
    public ExperimentRunEntity() {}

    // ==================== Getters & Setters ====================

    /** 获取数据库主键ID */
    public Long getId() { return id; }
    /** 设置数据库主键ID */
    public void setId(Long id) { this.id = id; }

    /** 获取运行键（与owner共同决定幂等） */
    public String getRunKey() { return runKey; }
    /** 设置运行键 */
    public void setRunKey(String runKey) { this.runKey = runKey; }

    /** 获取实验所属用户 */
    public String getOwnerUsername() { return ownerUsername; }
    /** 设置实验所属用户 */
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    /** 获取实验类型：MPC/RL/RAG */
    public String getExperimentType() { return experimentType; }
    /** 设置实验类型 */
    public void setExperimentType(String experimentType) { this.experimentType = experimentType; }

    /** 获取实验标题 */
    public String getTitle() { return title; }
    /** 设置实验标题 */
    public void setTitle(String title) { this.title = title; }

    /** 获取数据来源类型 */
    public String getSourceType() { return sourceType; }
    /** 设置数据来源类型 */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    /** 获取实验结论状态 */
    public String getStatus() { return status; }
    /** 设置实验结论状态 */
    public void setStatus(String status) { this.status = status; }

    /** 获取结果摘要 */
    public String getResultSummary() { return resultSummary; }
    /** 设置结果摘要 */
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }

    /** 获取限制说明 */
    public String getLimitations() { return limitations; }
    /** 设置限制说明 */
    public void setLimitations(String limitations) { this.limitations = limitations; }

    /** 获取清单文件路径 */
    public String getManifestPath() { return manifestPath; }
    /** 设置清单文件路径 */
    public void setManifestPath(String manifestPath) { this.manifestPath = manifestPath; }

    /** 获取实验执行时间 */
    public LocalDateTime getExecutedAt() { return executedAt; }
    /** 设置实验执行时间 */
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    /** 获取记录创建时间 */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** 设置记录创建时间 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
