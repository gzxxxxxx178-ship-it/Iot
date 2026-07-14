package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.AutomationRuleRequest;
import com.ruoyi.iotsystem.entity.AutomationExecutionEntity;
import com.ruoyi.iotsystem.entity.AutomationRuleEntity;
import com.ruoyi.iotsystem.service.AutomationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "自动化管理", description = "自动化规则配置、条件执行与审计记录")
@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private final AutomationService automationService;

    // 注入自动化业务服务
    public AutomationController(AutomationService automationService) {
        this.automationService = automationService;
    }

    // 获取全部自动化规则
    @Operation(summary = "自动化规则列表")
    @GetMapping("/rules")
    public ApiResponse<List<AutomationRuleEntity>> getRules() {
        return ApiResponse.success(automationService.getRules());
    }

    // 创建新的自动化规则
    @Operation(summary = "创建自动化规则")
    @PostMapping("/rules")
    public ApiResponse<AutomationRuleEntity> createRule(
            @Valid @RequestBody AutomationRuleRequest request) {
        return ApiResponse.success(automationService.createRule(request));
    }

    // 更新指定自动化规则
    @Operation(summary = "更新自动化规则")
    @PutMapping("/rules/{id}")
    public ApiResponse<AutomationRuleEntity> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody AutomationRuleRequest request) {
        return ApiResponse.success(automationService.updateRule(id, request));
    }

    // 删除指定自动化规则
    @Operation(summary = "删除自动化规则")
    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        automationService.deleteRule(id);
        return ApiResponse.success();
    }

    // 查询最近一百条自动化执行记录
    @Operation(summary = "自动化执行记录")
    @GetMapping("/executions")
    public ApiResponse<List<AutomationExecutionEntity>> getExecutions() {
        return ApiResponse.success(automationService.getExecutions());
    }
}
