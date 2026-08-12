package com.ruoyi.iotsystem.research.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 研究总览响应，包含最新MPC、RL、RAG运行的关键摘要指标。
 * 不伪造缺失指标——仅返回实际持久化的数据。
 */
public class ResearchOverviewResponse {

    private Map<String, Object> latestMpc;
    private Map<String, Object> latestRl;
    private Map<String, Object> latestRag;

    /** 无参构造器，初始化三个类型的空Map */
    public ResearchOverviewResponse() {
        this.latestMpc = new HashMap<>();
        this.latestRl = new HashMap<>();
        this.latestRag = new HashMap<>();
    }

    // ==================== Getters & Setters ====================

    /** 获取最新MPC实验摘要 */
    public Map<String, Object> getLatestMpc() { return latestMpc; }
    /** 设置最新MPC实验摘要 */
    public void setLatestMpc(Map<String, Object> latestMpc) { this.latestMpc = latestMpc; }

    /** 获取最新RL实验摘要 */
    public Map<String, Object> getLatestRl() { return latestRl; }
    /** 设置最新RL实验摘要 */
    public void setLatestRl(Map<String, Object> latestRl) { this.latestRl = latestRl; }

    /** 获取最新RAG实验摘要 */
    public Map<String, Object> getLatestRag() { return latestRag; }
    /** 设置最新RAG实验摘要 */
    public void setLatestRag(Map<String, Object> latestRag) { this.latestRag = latestRag; }

    /**
     * 将实验运行摘要填入对应类型的Map。
     * type为MPC/RL/RAG时分别填入latestMpc/latestRl/latestRag。
     */
    public void putRun(String type, Long runId, String runKey, String status, String summary) {
        Map<String, Object> target;
        if ("MPC".equals(type)) target = latestMpc;
        else if ("RL".equals(type)) target = latestRl;
        else if ("RAG".equals(type)) target = latestRag;
        else return;

        target.put("runId", runId);
        target.put("runKey", runKey);
        target.put("status", status);
        target.put("summary", summary != null ? summary : "");
    }
}
