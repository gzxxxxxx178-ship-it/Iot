package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.DeviceControlRequest;
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

    // 注入MQTT消息服务
    public DeviceControlController(MqttMessageService mqttMessageService) {
        this.mqttMessageService = mqttMessageService;
    }

    // 向经过校验的目标设备发送控制指令
    @Operation(summary = "发送设备控制指令")
    @PostMapping("/control")
    public ApiResponse<String> controlDevice(@Valid @RequestBody DeviceControlRequest request) {
        mqttMessageService.publishControl(request.getDeviceId(), request.getCommand());
        return ApiResponse.success(
                "指令 '" + request.getCommand() + "' 已发送到设备 " + request.getDeviceId());
    }
}
