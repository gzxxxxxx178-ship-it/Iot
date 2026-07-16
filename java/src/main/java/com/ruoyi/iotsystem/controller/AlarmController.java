package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.AlarmRuleRequest;
import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.config.SecurityContextUtils;
import com.ruoyi.iotsystem.entity.AlarmRecordEntity;
import com.ruoyi.iotsystem.entity.AlarmRuleEntity;
import com.ruoyi.iotsystem.service.AlarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "报警管理", description = "报警规则配置、记录查询与越限报警")
@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

    private final AlarmService alarmService;

    // 注入报警业务服务
    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    // 获取全部报警规则
    @Operation(summary = "报警规则列表")
    @GetMapping("/rules")
    public ApiResponse<List<AlarmRuleEntity>> getRules() {
        String username = SecurityContextUtils.currentUsernameOrNull();
        return ApiResponse.success(username == null ? alarmService.getRules() : alarmService.getRules(username));
    }

    // 创建新的报警规则
    @Operation(summary = "创建报警规则")
    @PostMapping("/rules")
    public ApiResponse<AlarmRuleEntity> createRule(@Valid @RequestBody AlarmRuleRequest request) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        return ApiResponse.success(username == null
                ? alarmService.createRule(request) : alarmService.createRule(request, username));
    }

    // 更新指定报警规则
    @Operation(summary = "更新报警规则")
    @PutMapping("/rules/{id}")
    public ApiResponse<AlarmRuleEntity> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody AlarmRuleRequest request) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        return ApiResponse.success(username == null
                ? alarmService.updateRule(id, request) : alarmService.updateRule(id, request, username));
    }

    // 删除指定报警规则
    @Operation(summary = "删除报警规则")
    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        if (username == null) {
            alarmService.deleteRule(id);
        } else {
            alarmService.deleteRule(id, username);
        }
        return ApiResponse.success();
    }

    // 查询最近报警记录或指定时间范围内的记录
    @Operation(summary = "报警记录列表")
    @GetMapping("/records")
    public ApiResponse<List<AlarmRecordEntity>> getRecords(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        return ApiResponse.success(username == null
                ? alarmService.getRecords(start, end) : alarmService.getRecords(start, end, username));
    }
}
