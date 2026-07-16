package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.config.SecurityContextUtils;
import com.ruoyi.iotsystem.dto.DeviceControlRequest;
import com.ruoyi.iotsystem.service.DeviceService;
import com.ruoyi.iotsystem.service.MqttMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Tag(name = "设备控制", description = "通过设备级MQTT Topic发送控制指令")
@RestController
@RequestMapping("/api/device")
public class DeviceControlController {

    private final MqttMessageService mqttMessageService;
    private final DeviceService deviceService;

    // 注入MQTT消息服务和设备生命周期服务
    public DeviceControlController(MqttMessageService mqttMessageService, DeviceService deviceService) {
        this.mqttMessageService = mqttMessageService;
        this.deviceService = deviceService;
    }

    // 向经过校验的目标设备发送控制指令
    @Operation(summary = "发送设备控制指令")
    @PostMapping("/control")
    public ApiResponse<String> controlDevice(@Valid @RequestBody DeviceControlRequest request) {
        String username = SecurityContextUtils.currentUsernameOrNull();
        if (username == null) {
            deviceService.assertControllable(request.getDeviceId());
        } else {
            deviceService.assertControllable(request.getDeviceId(), username);
        }
        mqttMessageService.publishControl(request.getDeviceId(), request.getCommand());
        return ApiResponse.success(
                "指令 '" + request.getCommand() + "' 已发送到设备 " + request.getDeviceId());
    }
}
