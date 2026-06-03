package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.service.MqttMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/device")
@CrossOrigin(origins = "*") // Allow requests from Vue frontend
public class DeviceControlController {

    @Autowired
    private MqttMessageService mqttMessageService;

    // 发送设备控制指令(start/stop)到MQTT
    @PostMapping("/control")
    public ResponseEntity<String> controlDevice(@RequestBody Map<String, String> payload) {
        String command = payload.get("command");
        if (command == null || (!command.equalsIgnoreCase("start") && !command.equalsIgnoreCase("stop"))) {
            return ResponseEntity.badRequest().body("Invalid command. Use 'start' or 'stop'.");
        }

        // Send command to MQTT topic
        String topic = "agri/device001/control";
        mqttMessageService.publish(topic, command);

        return ResponseEntity.ok("Command '" + command + "' sent successfully to " + topic);
    }
}
