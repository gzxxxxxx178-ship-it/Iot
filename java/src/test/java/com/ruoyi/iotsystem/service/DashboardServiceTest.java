package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.DashboardStatsResponse;
import com.ruoyi.iotsystem.dto.DeviceResponse;
import com.ruoyi.iotsystem.dto.DeviceStatusResponse;
import com.ruoyi.iotsystem.entity.DeviceEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private DeviceService deviceService;
    private DashboardService dashboardService;

    // 使用统一设备服务初始化仪表盘统计服务
    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(deviceService);
    }

    // 验证无设备时返回零计数和空平均值
    @Test
    void getStats_无设备_应返回空统计() {
        when(deviceService.listDevices(false)).thenReturn(Collections.emptyList());

        DashboardStatsResponse result = dashboardService.getStats();

        assertEquals(0, result.getDeviceCount());
        assertEquals(0, result.getOnlineCount());
        assertNull(result.getAvgTemp());
        assertNull(result.getAvgHum());
    }

    // 验证只聚合在线设备最新温湿度
    @Test
    void getStats_多设备_应统计在线设备最新值() {
        when(deviceService.listDevices(false)).thenReturn(Arrays.asList(
                createDeviceResponse("device001", 20.0, 40.0, true, true),
                createDeviceResponse("device002", 30.0, 60.0, true, true),
                createDeviceResponse("device003", 99.0, 99.0, false, true)));

        DashboardStatsResponse result = dashboardService.getStats();

        assertEquals(3, result.getDeviceCount());
        assertEquals(2, result.getOnlineCount());
        assertEquals(25.0, result.getAvgTemp());
        assertEquals(50.0, result.getAvgHum());
    }

    // 验证设备状态分布同时返回在线和离线数量
    @Test
    void getDeviceStatusDistribution_应返回在线离线分布() {
        when(deviceService.listDevices(false)).thenReturn(Arrays.asList(
                createDeviceResponse("device001", 25.0, 50.0, true, true),
                createDeviceResponse("device002", 26.0, 51.0, false, true)));

        List<DeviceStatusResponse> result = dashboardService.getDeviceStatusDistribution();

        assertEquals("在线", result.get(0).getName());
        assertEquals(1, result.get(0).getValue());
        assertEquals("离线", result.get(1).getName());
        assertEquals(1, result.get(1).getValue());
    }

    // 验证异常在线数据不会进入温湿度平均值
    @Test
    void getStats_异常在线数据_应排除核心统计() {
        when(deviceService.listDevices(false)).thenReturn(Arrays.asList(
                createDeviceResponse("device001", 20.0, 40.0, true, true),
                createDeviceResponse("device002", 99.0, 99.0, true, false)));

        DashboardStatsResponse result = dashboardService.getStats();

        assertEquals(2, result.getOnlineCount());
        assertEquals(20.0, result.getAvgTemp());
        assertEquals(40.0, result.getAvgHum());
    }

    // 创建带最新读数、在线状态和质量状态的设备响应
    private DeviceResponse createDeviceResponse(
            String deviceId,
            Double temperature,
            Double humidity,
            boolean online,
            boolean qualityValid) {
        DeviceEntity device = new DeviceEntity(deviceId, deviceId, "ESP8266", null, null, true);
        EspEntity reading = new EspEntity(deviceId, temperature, humidity, 1L);
        reading.setServerReceivedTime(LocalDateTime.now());
        reading.setQualityValid(qualityValid);
        return new DeviceResponse(device, reading, online);
    }
}
