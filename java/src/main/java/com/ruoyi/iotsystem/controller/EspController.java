package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.ApiResponse;
import com.ruoyi.iotsystem.dto.PagedResponse;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.EspRepository;
import com.ruoyi.iotsystem.service.EspService;
import com.ruoyi.iotsystem.service.SensorHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "传感器数据", description = "ESP8266传感器数据上报、治理、查询和导出")
@RestController
@RequestMapping("/esp")
public class EspController {

    private static final Logger logger = LoggerFactory.getLogger(EspController.class);

    private final EspService espService;
    private final EspRepository espRepository;
    private final SensorHistoryService sensorHistoryService;

    // 注入传感器数据、历史查询和设备汇总依赖
    public EspController(EspService espService, EspRepository espRepository,
            SensorHistoryService sensorHistoryService) {
        this.espService = espService;
        this.espRepository = espRepository;
        this.sensorHistoryService = sensorHistoryService;
    }

    // 返回ESP8266接口健康状态
    @Operation(summary = "健康检查")
    @GetMapping("/test")
    public ApiResponse<String> test() {
        return ApiResponse.success("ESP8266 Controller is working! Current time: " + LocalDateTime.now());
    }

    // 接收并持久化传感器数据
    @Operation(summary = "接收传感器数据", description = "接收JSON数据、执行质量标记并持久化")
    @PostMapping("/sensor/data")
    public ApiResponse<String> receiveSensorData(@Valid @RequestBody EspEntity sensorData) {
        logger.info("Received sensor data from device {}", sensorData.getDeviceId());
        return ApiResponse.success(espService.processDataAndGenerateResponse(sensorData));
    }

    // 获取最近二十条传感器历史记录
    @Operation(summary = "最近传感器数据")
    @GetMapping("/history")
    public ApiResponse<List<EspEntity>> getRecentData() {
        return ApiResponse.success(espService.getRecentData());
    }

    // 按设备和时间范围查询受五千条上限保护的兼容列表
    @Operation(summary = "按设备和时间范围查询")
    @GetMapping("/history/range")
    public ApiResponse<List<EspEntity>> getHistoryByRange(
            @RequestParam(required = false) String deviceId,
            @Parameter(description = "开始时间")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "结束时间")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ApiResponse.success(sensorHistoryService.queryRange(deviceId, start, end));
    }

    // 按设备、时间范围和受限分页参数查询历史数据
    @Operation(summary = "传感器历史分页查询")
    @GetMapping("/history/page")
    public ApiResponse<PagedResponse<EspEntity>> getHistoryPage(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<EspEntity> result = sensorHistoryService.queryPage(
                deviceId, start, end, page, size);
        return ApiResponse.success(PagedResponse.of(result));
    }

    // 将所选设备和时间范围内的完整历史数据导出为UTF-8 CSV
    @Operation(summary = "导出传感器历史CSV")
    @GetMapping(value = "/history/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportHistoryCsv(
            @RequestParam(required = false) String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        byte[] csv = sensorHistoryService.exportCsv(deviceId, start, end);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sensor-history.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }

    // 获取所有设备及其最新一条读数汇总
    @Operation(summary = "设备列表")
    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> getDevices() {
        List<String> deviceIds = espRepository.findDistinctDeviceIds();
        List<Map<String, Object>> devices = new ArrayList<>();
        for (String id : deviceIds) {
            espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc(id)
                    .ifPresent(reading -> devices.add(toDeviceSummary(reading)));
        }
        return ApiResponse.success(devices);
    }

    // 将最新读数转换为设备列表摘要
    private Map<String, Object> toDeviceSummary(EspEntity reading) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("deviceId", reading.getDeviceId());
        map.put("temperature", reading.getTemperature());
        map.put("humidity", reading.getHumidity());
        map.put("water", reading.getWater());
        map.put("rssi", reading.getRssi());
        map.put("linkage", reading.getLinkage());
        map.put("sendCount", reading.getSendCount());
        map.put("qualityValid", reading.getQualityValid());
        map.put("qualityIssues", reading.getQualityIssues());
        map.put("serverReceivedTime", reading.getServerReceivedTime());
        return map;
    }
}
