package com.ruoyi.iotsystem.research.dto;

import javax.validation.constraints.*;

/**
 * RAG问答请求：问题文本（1-1000字符）和可选topK（1-10，默认5）。
 */
public class RagQueryRequest {

    @NotBlank(message = "问题不能为空")
    @Size(min = 1, max = 1000, message = "问题长度必须在1到1000个字符之间")
    private String question;

    @Min(value = 1, message = "topK最小为1")
    @Max(value = 10, message = "topK最大为10")
    private Integer topK;

    /** 无参构造器，默认topK为5 */
    public RagQueryRequest() {
        this.topK = 5;
    }

    // ==================== Getters & Setters ====================

    /** 获取问题文本 */
    public String getQuestion() { return question; }
    /** 设置问题文本 */
    public void setQuestion(String question) { this.question = question; }

    /** 获取返回结果数量 */
    public Integer getTopK() { return topK; }
    /** 设置返回结果数量 */
    public void setTopK(Integer topK) { this.topK = topK; }
}
