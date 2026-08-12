package com.ruoyi.iotsystem.research.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG问答响应，包含回答或拒答信息、检索器、阈值、引用和证据。
 * 引用列表每项含 source_id、chunk_id 和 score。
 */
public class RagQueryResponse {

    private String answer;
    private boolean abstained;
    private String abstainReason;
    private String retriever;
    private double threshold;
    private double topScore;
    private List<Map<String, Object>> citations;
    private List<Map<String, Object>> evidence;

    /** 无参构造器，默认检索器为bm25，阈值为冻结阈值 */
    public RagQueryResponse() {
        this.retriever = "bm25";
        this.threshold = 27.31768531;
        this.citations = new ArrayList<>();
        this.evidence = new ArrayList<>();
    }

    // ==================== Getters & Setters ====================

    /** 获取系统回答文本 */
    public String getAnswer() { return answer; }
    /** 设置系统回答文本 */
    public void setAnswer(String answer) { this.answer = answer; }

    /** 获取是否拒答 */
    public boolean isAbstained() { return abstained; }
    /** 设置是否拒答 */
    public void setAbstained(boolean abstained) { this.abstained = abstained; }

    /** 获取拒答原因 */
    public String getAbstainReason() { return abstainReason; }
    /** 设置拒答原因 */
    public void setAbstainReason(String abstainReason) { this.abstainReason = abstainReason; }

    /** 获取检索器名称 */
    public String getRetriever() { return retriever; }
    /** 设置检索器名称 */
    public void setRetriever(String retriever) { this.retriever = retriever; }

    /** 获取检索阈值 */
    public double getThreshold() { return threshold; }
    /** 设置检索阈值 */
    public void setThreshold(double threshold) { this.threshold = threshold; }

    /** 获取最高得分 */
    public double getTopScore() { return topScore; }
    /** 设置最高得分 */
    public void setTopScore(double topScore) { this.topScore = topScore; }

    /** 获取引用列表 */
    public List<Map<String, Object>> getCitations() { return citations; }
    /** 设置引用列表 */
    public void setCitations(List<Map<String, Object>> citations) { this.citations = citations; }

    /** 获取证据列表 */
    public List<Map<String, Object>> getEvidence() { return evidence; }
    /** 设置证据列表 */
    public void setEvidence(List<Map<String, Object>> evidence) { this.evidence = evidence; }
}
