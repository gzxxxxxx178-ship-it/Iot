package com.ruoyi.iotsystem.simulation.controller;

import com.ruoyi.iotsystem.config.SecurityContextUtils;
import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.simulation.dto.CommandFeedbackRequest;
import com.ruoyi.iotsystem.simulation.dto.RuleRequest;
import com.ruoyi.iotsystem.simulation.dto.TelemetryRequest;
import com.ruoyi.iotsystem.simulation.entity.SimulationAlarmEntity;
import com.ruoyi.iotsystem.simulation.entity.SimulationCommandEntity;
import com.ruoyi.iotsystem.simulation.entity.SimulationRuleEntity;
import com.ruoyi.iotsystem.simulation.entity.SimulationTelemetryEntity;
import com.ruoyi.iotsystem.simulation.service.SimulationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 仿真软件在环REST控制器，提供MATLAB遥测上传、规则管理、报警管理和命令反馈接口。
 * 所有接口均受JWT/Cookie保护，按当前登录用户隔离数据。
 */
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    // 注入仿真域核心服务
    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    // ==================== 遥测 ====================

    // 上传仿真遥测数据，幂等处理并运行规则引擎
    @PostMapping("/telemetry")
    public ApiResponse<SimulationTelemetryEntity> uploadTelemetry(@Valid @RequestBody TelemetryRequest request) {
        String owner = requireOwner();
        return ApiResponse.success(simulationService.uploadTelemetry(owner, request));
    }

    // 查询指定设备最新一条遥测
    @GetMapping("/telemetry/latest")
    public ApiResponse<SimulationTelemetryEntity> getLatestTelemetry(
            @RequestParam String deviceId) {
        String owner = requireOwner();
        SimulationTelemetryEntity telemetry = simulationService.getLatestTelemetry(owner, deviceId);
        return ApiResponse.success(telemetry);
    }

    // 查询指定设备历史遥测，按发生时间倒序
    @GetMapping("/telemetry/history")
    public ApiResponse<List<SimulationTelemetryEntity>> getTelemetryHistory(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "50") int limit) {
        String owner = requireOwner();
        if (limit < 1 || limit > 500) {
            throw new RuntimeException("limit必须在1到500之间");
        }
        return ApiResponse.success(simulationService.getTelemetryHistory(owner, deviceId, limit));
    }

    // ==================== 规则CRUD ====================

    // 查询当前用户全部仿真规则
    @GetMapping("/rules")
    public ApiResponse<List<SimulationRuleEntity>> getRules() {
        String owner = requireOwner();
        return ApiResponse.success(simulationService.getRules(owner));
    }

    // 创建新的仿真规则
    @PostMapping("/rules")
    public ApiResponse<SimulationRuleEntity> createRule(@Valid @RequestBody RuleRequest request) {
        String owner = requireOwner();
        return ApiResponse.success(simulationService.createRule(owner, request));
    }

    // 更新指定仿真规则
    @PutMapping("/rules/{id}")
    public ApiResponse<SimulationRuleEntity> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody RuleRequest request) {
        String owner = requireOwner();
        return ApiResponse.success(simulationService.updateRule(owner, id, request));
    }

    // 删除指定仿真规则
    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        String owner = requireOwner();
        simulationService.deleteRule(owner, id);
        return ApiResponse.success();
    }

    // ==================== 报警 ====================

    // 按设备和可选状态查询报警
    @GetMapping("/alarms")
    public ApiResponse<List<SimulationAlarmEntity>> getAlarms(
            @RequestParam String deviceId,
            @RequestParam(required = false) String status) {
        String owner = requireOwner();
        return ApiResponse.success(simulationService.getAlarms(owner, deviceId, status));
    }

    // 确认指定报警
    @PostMapping("/alarms/{id}/acknowledge")
    public ApiResponse<SimulationAlarmEntity> acknowledgeAlarm(@PathVariable Long id) {
        String owner = requireOwner();
        return ApiResponse.success(simulationService.acknowledgeAlarm(owner, id));
    }

    // ==================== 命令 ====================

    // 查询指定设备待处理的仿真命令
    @GetMapping("/commands/pending")
    public ApiResponse<List<SimulationCommandEntity>> getPendingCommands(@RequestParam String deviceId) {
        String owner = requireOwner();
        return ApiResponse.success(simulationService.getPendingCommands(owner, deviceId));
    }

    // 提交命令执行反馈
    @PostMapping("/commands/{id}/feedback")
    public ApiResponse<SimulationCommandEntity> submitFeedback(
            @PathVariable Long id,
            @Valid @RequestBody CommandFeedbackRequest request) {
        String owner = requireOwner();
        return ApiResponse.success(simulationService.submitFeedback(owner, id, request));
    }

    // ==================== 内部方法 ====================

    // 从SecurityContext获取当前用户名，未认证时抛出异常
    private String requireOwner() {
        String owner = SecurityContextUtils.currentUsernameOrNull();
        if (owner == null) {
            throw new SecurityException("未登录或登录状态已失效");
        }
        return owner;
    }
}
