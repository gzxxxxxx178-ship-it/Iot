package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.DeviceCreateRequest;
import com.ruoyi.iotsystem.dto.DeviceResponse;
import com.ruoyi.iotsystem.dto.DeviceUpdateRequest;
import com.ruoyi.iotsystem.entity.DeviceEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.DeviceRepository;
import com.ruoyi.iotsystem.repository.EspRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private EspRepository espRepository;
    private DeviceService deviceService;

    // 创建使用30秒在线阈值的设备服务
    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(deviceRepository, espRepository, 30);
    }

    // 验证注册设备时应用默认类型和启用状态
    @Test
    void createDevice_合法请求_应创建设备档案() {
        DeviceCreateRequest request = new DeviceCreateRequest();
        request.setDeviceId("device002");
        request.setDeviceName("二号节点");
        when(deviceRepository.save(any(DeviceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response = deviceService.createDevice(request);

        assertEquals("device002", response.getDeviceId());
        assertEquals("ESP8266", response.getDeviceType());
        assertTrue(response.getEnabled());
    }

    // 验证最近上报设备在列表中被判定为在线
    @Test
    void listDevices_最近有上报_应返回在线状态() {
        DeviceEntity device = new DeviceEntity("device001", "一号节点", "ESP8266", null, null, true);
        EspEntity reading = new EspEntity("device001", 25.0, 60.0, 1L);
        reading.setServerReceivedTime(LocalDateTime.now().minusSeconds(5));
        when(espRepository.findDistinctDeviceIds()).thenReturn(Collections.singletonList("device001"));
        when(deviceRepository.existsByDeviceId("device001")).thenReturn(true);
        when(deviceRepository.findByLifecycleStatusOrderByCreatedAtDesc("ACTIVE"))
                .thenReturn(Collections.singletonList(device));
        when(espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc("device001"))
                .thenReturn(Optional.of(reading));

        DeviceResponse response = deviceService.listDevices(false).get(0);

        assertEquals("online", response.getStatus());
        assertEquals(25.0, response.getTemperature());
    }

    // 验证归档采用软删除并关闭设备
    @Test
    void archiveDevice_存在设备_应归档且停用() {
        DeviceEntity device = new DeviceEntity("device001", "一号节点", "ESP8266", null, null, true);
        when(deviceRepository.findByDeviceId("device001")).thenReturn(Optional.of(device));

        deviceService.archiveDevice("device001");

        assertEquals("ARCHIVED", device.getLifecycleStatus());
        assertEquals(Boolean.FALSE, device.getEnabled());
        verify(deviceRepository).save(device);
        verify(deviceRepository, never()).delete(any());
    }

    // 验证停用设备不能接收新的遥测数据
    @Test
    void ensureTelemetryAllowed_设备停用_应拒绝上报() {
        DeviceEntity device = new DeviceEntity("device001", "一号节点", "ESP8266", null, null, false);
        when(deviceRepository.findByDeviceId("device001")).thenReturn(Optional.of(device));

        assertThrows(RuntimeException.class,
                () -> deviceService.ensureTelemetryAllowed("device001"));
    }

    // 验证编辑档案时设备ID保持不变
    @Test
    void updateDevice_合法请求_应只更新可编辑字段() {
        DeviceEntity device = new DeviceEntity("device001", "旧名称", "ESP8266", null, null, true);
        DeviceUpdateRequest request = new DeviceUpdateRequest();
        request.setDeviceName("新名称");
        request.setDeviceType("ESP32");
        request.setEnabled(true);
        when(deviceRepository.findByDeviceId("device001")).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);

        DeviceResponse response = deviceService.updateDevice("device001", request);

        assertEquals("device001", response.getDeviceId());
        assertEquals("新名称", response.getDeviceName());
        assertEquals("ESP32", response.getDeviceType());
    }
}
