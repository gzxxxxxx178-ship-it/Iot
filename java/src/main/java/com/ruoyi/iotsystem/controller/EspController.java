package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.PagedResponse;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.EspRepository;
import com.ruoyi.iotsystem.service.EspService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "传感器数据", description = "ESP32 传感器数据上报、历史数据查询")
@RestController
@RequestMapping("/esp")
@CrossOrigin(origins = "*")
public class EspController {

    private static final Logger logger = LoggerFactory.getLogger(EspController.class);

    @Autowired
    private EspService espService;

    @Autowired
    private EspRepository espRepository;

    @Operation(summary = "健康检查")
    @GetMapping("/test")
    public ApiResponse<String> test() {
        return ApiResponse.success("ESP8266 Controller is working! Current time: " + LocalDateTime.now());
    }

    @Operation(summary = "接收传感器数据", description = "ESP32 通过 MQTT 网关上送的 JSON 数据，持久化到数据库并通过 WebSocket 广播")
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

    @Operation(summary = "最近传感器数据", description = "获取最近 20 条传感器历史记录，用于仪表盘和实时监控初始化")
    @GetMapping("/history")
    public ApiResponse<List<EspEntity>> getRecentData() {
        List<EspEntity> data = espService.getRecentData();
        return ApiResponse.success(data);
    }

    @Operation(summary = "按时间范围查询", description = "根据 ISO 格式时间范围查询传感器历史，返回全部匹配记录")
    @GetMapping("/history/range")
    public ApiResponse<List<EspEntity>> getHistoryByRange(
            @Parameter(description = "开始时间 (ISO格式)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "结束时间 (ISO格式)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<EspEntity> data = espRepository.findByServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(start, end);
        return ApiResponse.success(data);
    }

    @Operation(summary = "传感器历史分页查询", description = "支持分页的时间范围查询。传 page(页码0-based) 和 size(每页条数,默认20)")
    @GetMapping("/history/page")
    public ApiResponse<PagedResponse<EspEntity>> getHistoryPage(
            @Parameter(description = "开始时间 (可选)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "结束时间 (可选)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @Parameter(description = "页码 (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {

        Page<EspEntity> result;
        if (start != null && end != null) {
            result = espRepository.findByServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
                    start, end, PageRequest.of(page, size));
        } else {
            result = espRepository.findAllByOrderByServerReceivedTimeDesc(PageRequest.of(page, size));
        }
        return ApiResponse.success(PagedResponse.of(result));
    }

    @Operation(summary = "设备列表", description = "获取所有设备及其最新读数汇总")
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
