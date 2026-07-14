package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.DashboardStatsResponse;
import com.ruoyi.iotsystem.dto.DeviceStatusResponse;
import com.ruoyi.iotsystem.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "仪表盘", description = "设备在线状态与最新传感器统计")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    // 注入仪表盘统计服务
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // 获取设备总数、在线数和在线设备最新温湿度平均值
    @Operation(summary = "仪表盘统计")
    @GetMapping("/stats")
    public ApiResponse<DashboardStatsResponse> getStats() {
        return ApiResponse.success(dashboardService.getStats());
    }

    // 获取在线和离线设备数量分布
    @Operation(summary = "设备状态分布")
    @GetMapping("/device-status")
    public ApiResponse<List<DeviceStatusResponse>> getDeviceStatusDistribution() {
        return ApiResponse.success(dashboardService.getDeviceStatusDistribution());
    }
}
