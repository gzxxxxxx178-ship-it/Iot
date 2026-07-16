package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.config.SecurityContextUtils;
import com.ruoyi.iotsystem.dto.DeviceCreateRequest;
import com.ruoyi.iotsystem.dto.DeviceResponse;
import com.ruoyi.iotsystem.dto.DeviceUpdateRequest;
import com.ruoyi.iotsystem.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "设备管理", description = "设备档案注册、编辑、归档、恢复和状态查询")
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    // 注入设备生命周期服务
    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    // 查询设备列表并按需包含归档设备
    @Operation(summary = "查询设备列表")
    @GetMapping
    public ApiResponse<List<DeviceResponse>> listDevices(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        return ApiResponse.success(username == null
                ? deviceService.listDevices(includeArchived)
                : deviceService.listDevices(includeArchived, username));
    }

    // 查询指定设备档案和最新状态
    @Operation(summary = "查询设备详情")
    @GetMapping("/{deviceId}")
    public ApiResponse<DeviceResponse> getDevice(@PathVariable String deviceId) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        return ApiResponse.success(username == null
                ? deviceService.getDevice(deviceId) : deviceService.getDevice(deviceId, username));
    }

    // 注册新的设备档案
    @Operation(summary = "注册设备")
    @PostMapping
    public ApiResponse<DeviceResponse> createDevice(
            @Valid @RequestBody DeviceCreateRequest request) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        return ApiResponse.success(username == null
                ? deviceService.createDevice(request) : deviceService.createDevice(request, username));
    }

    // 修改指定设备的可编辑档案
    @Operation(summary = "修改设备")
    @PutMapping("/{deviceId}")
    public ApiResponse<DeviceResponse> updateDevice(
            @PathVariable String deviceId,
            @Valid @RequestBody DeviceUpdateRequest request) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        return ApiResponse.success(username == null
                ? deviceService.updateDevice(deviceId, request)
                : deviceService.updateDevice(deviceId, request, username));
    }

    // 将设备软删除为归档状态并保留历史数据
    @Operation(summary = "归档设备")
    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> archiveDevice(@PathVariable String deviceId) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        if (username == null) {
            deviceService.archiveDevice(deviceId);
        } else {
            deviceService.archiveDevice(deviceId, username);
        }
        return ApiResponse.success(null);
    }

    // 恢复已经归档的设备档案
    @Operation(summary = "恢复设备")
    @PostMapping("/{deviceId}/restore")
    public ApiResponse<DeviceResponse> restoreDevice(@PathVariable String deviceId) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        return ApiResponse.success(username == null
                ? deviceService.restoreDevice(deviceId)
                : deviceService.restoreDevice(deviceId, username));
    }
}
