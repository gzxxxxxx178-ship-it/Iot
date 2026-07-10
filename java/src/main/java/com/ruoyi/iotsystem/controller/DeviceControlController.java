package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.service.MqttMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/device")
@CrossOrigin(origins = "*") // Allow requests from Vue frontend
public class DeviceControlController {

    @Autowired
    private MqttMessageService mqttMessageService;

    // 发送设备控制指令 (start/stop) 到 MQTT，ESP32 订阅后执行
    @PostMapping("/control")
    public ApiResponse<String> controlDevice(@Valid @RequestBody Map<String, String> payload) {
        String command = payload.get("command");
        if (command == null || (!command.equalsIgnoreCase("start") && !command.equalsIgnoreCase("stop"))) {
            return ApiResponse.fail("无效指令，请使用 'start' 或 'stop'");
        }

        // 发布控制指令到 MQTT 主题
        String topic = "agri/device001/control";
        mqttMessageService.publish(topic, command);

        return ApiResponse.success("指令 '" + command + "' 已成功发送到 " + topic);
    }
}
