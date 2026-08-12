package com.ruoyi.iotsystem.research.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * RAG问答记录实体，持久化每次用户查询及系统回答（含拒答）。
 */
@Entity
@Table(name = "research_rag_queries")
public class RagQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_username", length = 100, nullable = false)
    private String ownerUsername;

    @Column(length = 1000, nullable = false)
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(nullable = false)
    private Boolean abstained = false;

    @Column(name = "abstain_reason", length = 128)
    private String abstainReason;

    @Column(length = 32, nullable = false)
    private String retriever = "bm25";

    @Column(name = "threshold_value", nullable = false)
    private Double thresholdValue;

    @Column(name = "top_score")
    private Double topScore;

    @Column(name = "citations_json", columnDefinition = "TEXT")
    private String citationsJson;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 无参构造器，供JPA使用 */
    public RagQueryEntity() {}

    // ==================== Getters & Setters ====================

    /** 获取数据库主键ID */
    public Long getId() { return id; }
    /** 设置数据库主键ID */
    public void setId(Long id) { this.id = id; }

    /** 获取问答所属用户 */
    public String getOwnerUsername() { return ownerUsername; }
    /** 设置问答所属用户 */
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    /** 获取用户问题 */
    public String getQuestion() { return question; }
    /** 设置用户问题 */
    public void setQuestion(String question) { this.question = question; }

    /** 获取系统回答 */
    public String getAnswer() { return answer; }
    /** 设置系统回答 */
    public void setAnswer(String answer) { this.answer = answer; }

    /** 获取是否拒答 */
    public Boolean getAbstained() { return abstained; }
    /** 设置是否拒答 */
    public void setAbstained(Boolean abstained) { this.abstained = abstained; }

    /** 获取拒答原因 */
    public String getAbstainReason() { return abstainReason; }
    /** 设置拒答原因 */
    public void setAbstainReason(String abstainReason) { this.abstainReason = abstainReason; }

    /** 获取检索器名称 */
    public String getRetriever() { return retriever; }
    /** 设置检索器名称 */
    public void setRetriever(String retriever) { this.retriever = retriever; }

    /** 获取阈值 */
    public Double getThresholdValue() { return thresholdValue; }
    /** 设置阈值 */
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }

    /** 获取最高得分 */
    public Double getTopScore() { return topScore; }
    /** 设置最高得分 */
    public void setTopScore(Double topScore) { this.topScore = topScore; }

    /** 获取引用JSON字符串 */
    public String getCitationsJson() { return citationsJson; }
    /** 设置引用JSON字符串 */
    public void setCitationsJson(String citationsJson) { this.citationsJson = citationsJson; }

    /** 获取证据JSON字符串 */
    public String getEvidenceJson() { return evidenceJson; }
    /** 设置证据JSON字符串 */
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }

    /** 获取记录创建时间 */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** 设置记录创建时间 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
