package com.ruoyi.iotsystem.research.controller;

import com.ruoyi.iotsystem.config.SecurityContextUtils;
import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.research.dto.RagQueryRequest;
import com.ruoyi.iotsystem.research.dto.RagQueryResponse;
import com.ruoyi.iotsystem.research.service.Bm25RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 研究RAG问答REST控制器。
 * 提供基于BM25的保守型RAG查询接口，不调用外部LLM、不下发设备命令。
 * 所有接口受JWT/Cookie保护，按当前用户隔离问答记录。
 */
@RestController
@RequestMapping("/api/research")
public class ResearchRagController {

    @Autowired
    private Bm25RagService bm25RagService;

    /**
     * RAG证据问答：基于冻结BM25索引进行检索，根据安全规则和阈值决定回答或拒答。
     * 每次查询（含拒答）均持久化到research_rag_queries表。
     * 不调用DeepSeek或任何外部LLM。
     *
     * @param request 查询请求（question 1-1000字符，可选topK 1-10）
     * @return 包含回答/拒答状态、引用和证据的响应
     */
    @PostMapping("/rag/query")
    public ApiResponse<RagQueryResponse> queryRag(@Valid @RequestBody RagQueryRequest request) {
        String owner = requireOwner();
        return ApiResponse.success(bm25RagService.query(owner, request));
    }

    // ==================== 内部方法 ====================

    /**
     * 从SecurityContext获取当前用户名，未认证时抛出异常
     */
    private String requireOwner() {
        String owner = SecurityContextUtils.currentUsernameOrNull();
        if (owner == null) {
            throw new SecurityException("未登录或登录状态已失效");
        }
        return owner;
    }
}
