package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.DeviceCreateRequest;
import com.ruoyi.iotsystem.dto.DeviceResponse;
import com.ruoyi.iotsystem.dto.DeviceUpdateRequest;
import com.ruoyi.iotsystem.entity.DeviceEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.DeviceRepository;
import com.ruoyi.iotsystem.repository.EspRepository;
import com.ruoyi.iotsystem.repository.DeviceShareRepository;
import com.ruoyi.iotsystem.repository.UserRepository;
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
    @Mock private DeviceShareRepository deviceShareRepository;
    @Mock private UserRepository userRepository;
    private DeviceService deviceService;

    // 创建使用30秒在线阈值的设备服务
    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(deviceRepository, espRepository, 30, deviceShareRepository, userRepository);
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

    // 验证受邀账号能够在设备列表中看到共享设备
    @Test
    void listDevices_受邀账号_应看到共享设备且标记为非所有者() {
        DeviceEntity device = new DeviceEntity("device001", "一号节点", "ESP8266", null, null, true, "pogacar");
        when(espRepository.findDistinctDeviceIds()).thenReturn(Collections.emptyList());
        when(deviceRepository.findByOwnerUsernameOrderByCreatedAtDesc("test")).thenReturn(Collections.emptyList());
        when(deviceShareRepository.findByGranteeUsername("test"))
                .thenReturn(Collections.singletonList(new com.ruoyi.iotsystem.entity.DeviceShareEntity("device001", "test")));
        when(deviceRepository.findByDeviceIdIn(Collections.singletonList("device001")))
                .thenReturn(Collections.singletonList(device));
        when(espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc("device001"))
                .thenReturn(Optional.empty());

        DeviceResponse result = deviceService.listDevices(false, "test").get(0);

        assertEquals("device001", result.getDeviceId());
        assertTrue(!result.isOwnedByCurrentUser());
    }

    // 验证共享账号不能修改所有者的设备档案
    @Test
    void updateDevice_共享账号_应拒绝编辑() {
        DeviceEntity device = new DeviceEntity("device001", "一号节点", "ESP8266", null, null, true, "pogacar");
        when(deviceRepository.findByDeviceId("device001")).thenReturn(Optional.of(device));
        DeviceUpdateRequest request = new DeviceUpdateRequest();

        assertThrows(SecurityException.class, () -> deviceService.updateDevice("device001", request, "test"));
    }

    // 验证设备所有者可将设备共享给已存在账号
    @Test
    void addShare_已存在账号_应创建唯一授权() {
        DeviceEntity device = new DeviceEntity("device001", "一号节点", "ESP8266", null, null, true, "pogacar");
        when(deviceRepository.findByDeviceId("device001")).thenReturn(Optional.of(device));
        when(userRepository.existsByUsername("test")).thenReturn(true);
        when(deviceShareRepository.existsByDeviceIdAndGranteeUsername("device001", "test")).thenReturn(false);
        when(deviceShareRepository.findByDeviceIdOrderByCreatedAtAsc("device001"))
                .thenReturn(Collections.singletonList(new com.ruoyi.iotsystem.entity.DeviceShareEntity("device001", "test")));

        assertEquals(Collections.singletonList("test"), deviceService.addShare("device001", "pogacar", "test"));
        verify(deviceShareRepository).save(any(com.ruoyi.iotsystem.entity.DeviceShareEntity.class));
    }

    // 验证共享账号能控制设备且未授权账号不能越权控制
    @Test
    void assertControllable_共享账号放行且其他账号拒绝() {
        DeviceEntity device = new DeviceEntity("device001", "一号节点", "ESP8266", null, null, true, "pogacar");
        when(deviceRepository.findByDeviceId("device001")).thenReturn(Optional.of(device));
        when(deviceShareRepository.existsByDeviceIdAndGranteeUsername("device001", "test")).thenReturn(true);
        when(deviceShareRepository.existsByDeviceIdAndGranteeUsername("device001", "stranger")).thenReturn(false);

        deviceService.assertControllable("device001", "test");

        assertThrows(SecurityException.class, () -> deviceService.assertControllable("device001", "stranger"));
    }

    // 验证授权不存在的用户名时不创建共享关系
    @Test
    void addShare_账号不存在_应拒绝授权() {
        DeviceEntity device = new DeviceEntity("device001", "一号节点", "ESP8266", null, null, true, "pogacar");
        when(deviceRepository.findByDeviceId("device001")).thenReturn(Optional.of(device));
        when(userRepository.existsByUsername("unknown")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> deviceService.addShare("device001", "pogacar", "unknown"));
        verify(deviceShareRepository, never()).save(any(com.ruoyi.iotsystem.entity.DeviceShareEntity.class));
    }

    // 验证设备所有者撤销共享后不再保留授权记录
    @Test
    void removeShare_所有者撤销_应删除授权并返回空名单() {
        DeviceEntity device = new DeviceEntity("device001", "一号节点", "ESP8266", null, null, true, "pogacar");
        when(deviceRepository.findByDeviceId("device001")).thenReturn(Optional.of(device));
        when(deviceShareRepository.findByDeviceIdOrderByCreatedAtAsc("device001"))
                .thenReturn(Collections.emptyList());

        assertTrue(deviceService.removeShare("device001", "pogacar", "test").isEmpty());

        verify(deviceShareRepository).deleteByDeviceIdAndGranteeUsername("device001", "test");
    }
}
