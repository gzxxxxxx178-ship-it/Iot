package com.ruoyi.iotsystem.research.controller;

import com.ruoyi.iotsystem.config.SecurityContextUtils;
import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.research.dto.ExperimentRunRequest;
import com.ruoyi.iotsystem.research.dto.ExperimentRunResponse;
import com.ruoyi.iotsystem.research.dto.ResearchOverviewResponse;
import com.ruoyi.iotsystem.research.service.ResearchExperimentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 研究实验运行REST控制器。
 * 提供实验上传（幂等）、列表查询、详情查询和总览接口。
 * 所有接口受JWT/Cookie保护，按当前用户隔离数据。
 */
@RestController
@RequestMapping("/api/research")
public class ResearchExperimentController {

    @Autowired
    private ResearchExperimentService experimentService;

    // ==================== 实验管理 ====================

    /**
     * 上传一条实验运行及其指标。
     * 相同owner+runKey时幂等返回已有run，不重复插入指标。
     */
    @PostMapping("/experiments")
    public ApiResponse<ExperimentRunResponse> uploadExperiment(
            @Valid @RequestBody ExperimentRunRequest request) {
        String owner = requireOwner();
        return ApiResponse.success(experimentService.uploadExperiment(owner, request));
    }

    /**
     * 查询当前用户的实验运行摘要列表。
     * 支持按类型筛选和条数限制（默认50，最大200）。
     */
    @GetMapping("/experiments")
    public ApiResponse<List<ExperimentRunResponse>> listExperiments(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") int limit) {
        String owner = requireOwner();
        if (limit < 1 || limit > 200) {
            throw new RuntimeException("limit必须在1到200之间");
        }
        return ApiResponse.success(experimentService.listExperiments(owner, type, limit));
    }

    /**
     * 获取指定实验的详情（含完整指标列表）。
     * 仅owner可访问，非owner返回403级权限错误。
     */
    @GetMapping("/experiments/{id}")
    public ApiResponse<ExperimentRunResponse> getExperimentDetail(@PathVariable Long id) {
        String owner = requireOwner();
        return ApiResponse.success(experimentService.getExperimentDetail(owner, id));
    }

    /**
     * 研究总览：返回最近MPC、RL、RAG运行的关键指标摘要。
     * 不伪造缺失数据。
     */
    @GetMapping("/overview")
    public ApiResponse<ResearchOverviewResponse> getOverview() {
        String owner = requireOwner();
        return ApiResponse.success(experimentService.getOverview(owner));
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
