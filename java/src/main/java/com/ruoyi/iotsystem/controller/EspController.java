package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.EspRepository;
import com.ruoyi.iotsystem.service.EspService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/esp")
@CrossOrigin(origins = "*")
public class EspController {

    private static final Logger logger = LoggerFactory.getLogger(EspController.class);

    @Autowired
    private EspService espService;

    @Autowired
    private EspRepository espRepository;

    // 健康检查端点，返回服务器当前时间
    @GetMapping("/test")
    public ApiResponse<String> test() {
        return ApiResponse.success("ESP8266 Controller is working! Current time: " + LocalDateTime.now());
    }

    // 接收 ESP8266 传感器数据：记录日志 → 持久化 → WebSocket 广播
    @PostMapping("/sensor/data")
    public ApiResponse<String> receiveSensorData(@Valid @RequestBody EspEntity sensorData) {
        logger.info("Received data from device: {}", sensorData.getDeviceId());
        logger.info("Temperature: {}°C", sensorData.getTemperature());
        logger.info("Humidity: {}%", sensorData.getHumidity());
        logger.info("Timestamp: {}", sensorData.getTimestamp());
        logger.info("Server received time: {}", LocalDateTime.now());

        String response = espService.processDataAndGenerateResponse(sensorData);
        return ApiResponse.success(response);
    }

    // 获取最近 20 条传感器历史数据
    @GetMapping("/history")
    public ApiResponse<List<EspEntity>> getRecentData() {
        List<EspEntity> data = espService.getRecentData();
        return ApiResponse.success(data);
    }

    // 按时间范围查询传感器历史数据
    @GetMapping("/history/range")
    public ApiResponse<List<EspEntity>> getHistoryByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<EspEntity> data = espRepository.findByServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(start, end);
        return ApiResponse.success(data);
    }

    // 获取所有设备及其最新读数，汇总为设备列表
    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> getDevices() {
        List<String> deviceIds = espRepository.findDistinctDeviceIds();
        List<Map<String, Object>> devices = new ArrayList<>();
        for (String id : deviceIds) {
            List<EspEntity> latest = espRepository.findLatestByDeviceId(id);
            if (!latest.isEmpty()) {
                EspEntity e = latest.get(0);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("deviceId", e.getDeviceId());
                map.put("temperature", e.getTemperature());
                map.put("humidity", e.getHumidity());
                map.put("water", e.getWater());
                map.put("rssi", e.getRssi());
                map.put("linkage", e.getLinkage());
                map.put("sendCount", e.getSendCount());
                map.put("serverReceivedTime", e.getServerReceivedTime());
                devices.add(map);
            }
        }
        return ApiResponse.success(devices);
    }
}
