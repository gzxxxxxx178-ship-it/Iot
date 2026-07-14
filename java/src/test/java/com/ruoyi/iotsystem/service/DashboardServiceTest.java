package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.DashboardStatsResponse;
import com.ruoyi.iotsystem.dto.DeviceStatusResponse;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.EspRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private EspRepository espRepository;

    private DashboardService dashboardService;

    // 使用30秒在线超时阈值初始化被测服务
    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(espRepository, 30);
    }

    // 验证无设备时返回零计数和空平均值
    @Test
    void getStats_无设备_应返回空统计() {
        when(espRepository.findDistinctDeviceIds()).thenReturn(Collections.emptyList());

        DashboardStatsResponse result = dashboardService.getStats();

        assertEquals(0, result.getDeviceCount());
        assertEquals(0, result.getOnlineCount());
        assertNull(result.getAvgTemp());
        assertNull(result.getAvgHum());
    }

    // 验证在线口径依据最近接收时间且平均值排除离线设备
    @Test
    void getStats_多设备_应统计在线设备最新值() {
        EspEntity onlineOne = createReading("device001", 20.0, 40.0, LocalDateTime.now().minusSeconds(5));
        EspEntity onlineTwo = createReading("device002", 30.0, 60.0, LocalDateTime.now().minusSeconds(10));
        EspEntity offline = createReading("device003", 99.0, 99.0, LocalDateTime.now().minusMinutes(2));
        when(espRepository.findDistinctDeviceIds())
                .thenReturn(Arrays.asList("device001", "device002", "device003"));
        when(espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc("device001"))
                .thenReturn(Optional.of(onlineOne));
        when(espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc("device002"))
                .thenReturn(Optional.of(onlineTwo));
        when(espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc("device003"))
                .thenReturn(Optional.of(offline));

        DashboardStatsResponse result = dashboardService.getStats();

        assertEquals(3, result.getDeviceCount());
        assertEquals(2, result.getOnlineCount());
        assertEquals(25.0, result.getAvgTemp());
        assertEquals(50.0, result.getAvgHum());
    }

    // 验证设备状态分布同时返回在线和离线数量
    @Test
    void getDeviceStatusDistribution_应返回在线离线分布() {
        EspEntity online = createReading("device001", 25.0, 50.0, LocalDateTime.now().minusSeconds(5));
        EspEntity offline = createReading("device002", 26.0, 51.0, LocalDateTime.now().minusMinutes(2));
        when(espRepository.findDistinctDeviceIds()).thenReturn(Arrays.asList("device001", "device002"));
        when(espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc("device001"))
                .thenReturn(Optional.of(online));
        when(espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc("device002"))
                .thenReturn(Optional.of(offline));

        List<DeviceStatusResponse> result = dashboardService.getDeviceStatusDistribution();

        assertEquals(2, result.size());
        assertEquals("在线", result.get(0).getName());
        assertEquals(1, result.get(0).getValue());
        assertEquals("离线", result.get(1).getName());
        assertEquals(1, result.get(1).getValue());
    }

    // 验证已标记异常的数据不会进入温湿度平均值
    @Test
    void getStats_异常在线数据_应排除核心统计() {
        EspEntity valid = createReading("device001", 20.0, 40.0, LocalDateTime.now().minusSeconds(5));
        EspEntity invalid = createReading("device002", 99.0, 99.0, LocalDateTime.now().minusSeconds(5));
        invalid.setQualityValid(false);
        when(espRepository.findDistinctDeviceIds()).thenReturn(Arrays.asList("device001", "device002"));
        when(espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc("device001"))
                .thenReturn(Optional.of(valid));
        when(espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc("device002"))
                .thenReturn(Optional.of(invalid));

        DashboardStatsResponse result = dashboardService.getStats();

        assertEquals(2, result.getOnlineCount());
        assertEquals(20.0, result.getAvgTemp());
        assertEquals(40.0, result.getAvgHum());
    }

    // 创建带指定服务端接收时间的测试读数
    private EspEntity createReading(
            String deviceId,
            Double temperature,
            Double humidity,
            LocalDateTime serverReceivedTime) {
        EspEntity reading = new EspEntity(deviceId, temperature, humidity, System.currentTimeMillis());
        reading.setServerReceivedTime(serverReceivedTime);
        return reading;
    }
}
