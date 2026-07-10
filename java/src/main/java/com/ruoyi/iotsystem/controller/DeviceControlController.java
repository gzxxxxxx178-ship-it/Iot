package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.service.MqttMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "设备控制", description = "向 ESP32 设备发送启停控制指令（通过 MQTT）")
@RestController
@RequestMapping("/api/device")
@CrossOrigin(origins = "*")
public class DeviceControlController {

    @Autowired
    private MqttMessageService mqttMessageService;

    @Operation(summary = "发送控制指令", description = "向 MQTT 主题 agri/device001/control 发布 start 或 stop 指令，ESP32 订阅后执行")
    @PostMapping("/control")
    public ApiResponse<String> controlDevice(@RequestBody Map<String, String> payload) {
        String command = payload.get("command");
        if (command == null || (!command.equalsIgnoreCase("start") && !command.equalsIgnoreCase("stop"))) {
            return ApiResponse.fail("无效指令，请使用 'start' 或 'stop'");
        }

        String topic = "agri/device001/control";
        mqttMessageService.publish(topic, command);

        return ApiResponse.success("指令 '" + command + "' 已成功发送到 " + topic);
    }
}
