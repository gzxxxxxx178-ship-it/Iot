package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.TestInfrastructureConfiguration;
import com.ruoyi.iotsystem.dto.DeviceUpdateRequest;
import com.ruoyi.iotsystem.entity.DeviceEntity;
import com.ruoyi.iotsystem.entity.DeviceShareEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.DeviceRepository;
import com.ruoyi.iotsystem.repository.DeviceShareRepository;
import com.ruoyi.iotsystem.repository.EspRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfiguration.class)
@Transactional
class DeviceOwnershipIntegrationTest {

    @Autowired private DeviceService deviceService;
    @Autowired private SensorHistoryService sensorHistoryService;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private EspRepository espRepository;
    @Autowired private DeviceShareRepository deviceShareRepository;
    @PersistenceContext private EntityManager em;

    // 写入一条带归属的设备档案并返回
    private DeviceEntity saveDevice(String deviceId, String ownerUsername) {
        return deviceRepository.save(new DeviceEntity(
                deviceId, deviceId, "ESP8266", null, null, Boolean.TRUE, ownerUsername));
    }

    // 写入一条带归属的传感器历史读数并返回
    private EspEntity saveReading(String deviceId, String ownerUsername) {
        EspEntity reading = new EspEntity(deviceId, 25.0, 60.0, 1L);
        reading.setOwnerUsername(ownerUsername);
        return espRepository.save(reading);
    }

    // 清空一级缓存后从数据库重新加载设备档案
    private DeviceEntity reloadDevice(String deviceId) {
        em.flush();
        em.clear();
        return deviceRepository.findByDeviceId(deviceId).orElse(null);
    }

    // 清空一级缓存后从数据库重新加载设备最新读数
    private EspEntity reloadReading(String deviceId) {
        em.flush();
        em.clear();
        Optional<EspEntity> reading = espRepository.findFirstByDeviceIdOrderByServerReceivedTimeDesc(deviceId);
        return reading.orElse(null);
    }

    // 验证普通账号列表查询不会把无主设备或历史认领到自己名下
    @Test
    void listDevices_普通账号_不认领无主设备与历史() {
        saveDevice("unowned", null);
        saveReading("unowned", null);

        List<com.ruoyi.iotsystem.dto.DeviceResponse> result = deviceService.listDevices(false, "alice");

        assertTrue(result.isEmpty());
        assertNull(reloadDevice("unowned").getOwnerUsername());
        assertNull(reloadReading("unowned").getOwnerUsername());
    }

    // 验证普通账号读取无主设备详情会被拒绝且不改变归属
    @Test
    void getDevice_普通账号访问无主设备_应拒绝且不改变归属() {
        saveDevice("unowned", null);
        saveReading("unowned", null);

        assertThrows(SecurityException.class, () -> deviceService.getDevice("unowned", "alice"));

        assertNull(reloadDevice("unowned").getOwnerUsername());
        assertNull(reloadReading("unowned").getOwnerUsername());
    }

    // 验证编辑、归档、恢复和控制都不能借访问改变无主设备归属
    @Test
    void 写操作与控制_无主设备_应拒绝且不改变归属() {
        saveDevice("unowned", null);
        saveReading("unowned", null);
        DeviceUpdateRequest updateRequest = new DeviceUpdateRequest();
        updateRequest.setDeviceName("抢注名称");
        updateRequest.setEnabled(Boolean.TRUE);

        assertThrows(SecurityException.class, () -> deviceService.updateDevice("unowned", updateRequest, "alice"));
        assertThrows(SecurityException.class, () -> deviceService.archiveDevice("unowned", "alice"));
        assertThrows(SecurityException.class, () -> deviceService.restoreDevice("unowned", "alice"));
        assertThrows(SecurityException.class, () -> deviceService.assertControllable("unowned", "alice"));

        DeviceEntity reloaded = reloadDevice("unowned");
        assertNull(reloaded.getOwnerUsername());
        assertEquals("ACTIVE", reloaded.getLifecycleStatus());
        assertNull(reloadReading("unowned").getOwnerUsername());
    }

    // 验证历史分页、范围和导出路径对无主设备都拒绝且不改归属
    @Test
    void 历史查询与导出_无主设备_应拒绝且不改变归属() {
        saveDevice("unowned", null);
        saveReading("unowned", null);
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);

        assertThrows(SecurityException.class,
                () -> sensorHistoryService.queryPage("unowned", null, null, 0, 20, "alice"));
        assertThrows(SecurityException.class,
                () -> sensorHistoryService.queryRange("unowned", start, end, "alice"));
        assertThrows(SecurityException.class,
                () -> sensorHistoryService.exportCsv("unowned", start, end, "alice"));

        assertNull(reloadDevice("unowned").getOwnerUsername());
        assertNull(reloadReading("unowned").getOwnerUsername());
    }

    // 验证即使存在遗留共享关系也不能访问或控制无主设备
    @Test
    void 无主设备_存在共享关系_仍拒绝访问与控制() {
        saveDevice("unowned-shared", null);
        saveReading("unowned-shared", null);
        deviceShareRepository.save(new DeviceShareEntity("unowned-shared", "alice"));

        assertTrue(deviceService.listDevices(false, "alice").isEmpty());
        assertThrows(SecurityException.class, () -> deviceService.getDevice("unowned-shared", "alice"));
        assertThrows(SecurityException.class, () -> deviceService.assertCanAccess("unowned-shared", "alice"));
        assertThrows(SecurityException.class, () -> deviceService.assertControllable("unowned-shared", "alice"));
        assertThrows(SecurityException.class,
                () -> sensorHistoryService.queryPage("unowned-shared", null, null, 0, 20, "alice"));

        assertNull(reloadDevice("unowned-shared").getOwnerUsername());
        assertNull(reloadReading("unowned-shared").getOwnerUsername());
    }

    // 验证首次发现的历史设备保持无主且普通账号不能授权共享
    @Test
    void 历史自动发现与共享管理_不授予访问者所有权() {
        saveReading("discovered", null);

        assertTrue(deviceService.listDevices(false, "alice").isEmpty());
        assertNull(reloadDevice("discovered").getOwnerUsername());
        assertThrows(SecurityException.class, () -> deviceService.listShares("discovered", "alice"));
        assertThrows(SecurityException.class, () -> deviceService.addShare("discovered", "alice", "bob"));
        assertThrows(SecurityException.class, () -> deviceService.removeShare("discovered", "alice", "bob"));
        assertTrue(deviceShareRepository.findByDeviceIdOrderByCreatedAtAsc("discovered").isEmpty());
        assertNull(reloadDevice("discovered").getOwnerUsername());
        assertNull(reloadReading("discovered").getOwnerUsername());
    }

    // 验证本人设备与合法共享设备访问权限保持不变
    @Test
    void 本人与共享设备_访问权限保持正常() {
        saveDevice("owned", "alice");
        saveReading("owned", "alice");
        saveDevice("shared", "bob");
        saveReading("shared", "bob");
        deviceShareRepository.save(new DeviceShareEntity("shared", "alice"));

        List<com.ruoyi.iotsystem.dto.DeviceResponse> visible = deviceService.listDevices(false, "alice");
        assertEquals(2, visible.size());
        com.ruoyi.iotsystem.dto.DeviceResponse owned = visible.stream()
                .filter(device -> "owned".equals(device.getDeviceId())).findFirst().get();
        com.ruoyi.iotsystem.dto.DeviceResponse shared = visible.stream()
                .filter(device -> "shared".equals(device.getDeviceId())).findFirst().get();
        assertTrue(owned.isOwnedByCurrentUser());
        assertFalse(shared.isOwnedByCurrentUser());

        deviceService.getDevice("owned", "alice");
        deviceService.getDevice("shared", "alice");
        deviceService.assertCanAccess("owned", "alice");
        deviceService.assertCanAccess("shared", "alice");
        deviceService.assertControllable("owned", "alice");
        deviceService.assertControllable("shared", "alice");
        assertEquals(1, sensorHistoryService.queryPage("owned", null, null, 0, 20, "alice")
                .getTotalElements());
        assertEquals(1, sensorHistoryService.queryPage("shared", null, null, 0, 20, "alice")
                .getTotalElements());

        assertThrows(SecurityException.class, () -> deviceService.assertCanAccess("owned", "stranger"));
        assertThrows(SecurityException.class, () -> deviceService.assertControllable("shared", "stranger"));

        assertEquals("alice", reloadDevice("owned").getOwnerUsername());
        assertEquals("bob", reloadDevice("shared").getOwnerUsername());
    }
}
