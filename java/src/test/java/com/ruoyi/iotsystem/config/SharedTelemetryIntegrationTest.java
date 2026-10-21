package com.ruoyi.iotsystem.config;

import com.ruoyi.iotsystem.TestInfrastructureConfiguration;
import com.ruoyi.iotsystem.entity.DeviceEntity;
import com.ruoyi.iotsystem.entity.DeviceShareEntity;
import com.ruoyi.iotsystem.repository.DeviceRepository;
import com.ruoyi.iotsystem.repository.DeviceShareRepository;
import com.ruoyi.iotsystem.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfiguration.class)
class SharedTelemetryIntegrationTest {
    @Autowired private SensorWebSocketHandler handler;
    @Autowired private DeviceService service;
    @Autowired private DeviceRepository devices;
    @Autowired private DeviceShareRepository shares;
    private WebSocketSession owner;
    private WebSocketSession grantee;
    private WebSocketSession outsider;
    private String deviceId;
    private String otherId;

    // 建立三个长期连接及两个设备，授权变化使用H2真实提交。
    @BeforeEach
    void setUp() throws Exception {
        deviceId = "ws-" + UUID.randomUUID();
        otherId = "ws-" + UUID.randomUUID();
        devices.save(new DeviceEntity(deviceId, deviceId, "ESP8266", null, null, true, "ws-owner"));
        devices.save(new DeviceEntity(otherId, otherId, "ESP8266", null, null, true, "ws-other"));
        shares.save(new DeviceShareEntity(otherId, "ws-outsider"));
        owner = connect("ws-owner");
        grantee = connect("ws-grantee");
        outsider = connect("ws-outsider");
    }

    // 断开测试连接并清理本测试的设备和共享关系。
    @AfterEach
    void cleanUp() throws Exception {
        for (WebSocketSession session : new WebSocketSession[]{owner, grantee, outsider}) {
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        }
        for (String id : new String[]{deviceId, otherId}) {
            shares.deleteAll(shares.findByDeviceIdOrderByCreatedAtAsc(id));
            devices.findByDeviceId(id).ifPresent(devices::delete);
        }
    }

    // 在同一连接上验证授权前、授权后和撤销后的接收范围，其他设备的授权不串用。
    @Test
    void grantsAndRevocationsAffectExistingConnections() throws Exception {
        handler.broadcastToDevice(deviceId, "before");
        verify(owner).sendMessage(any());
        verify(grantee, never()).sendMessage(any());
        verify(outsider, never()).sendMessage(any());
        clearInvocations(owner, grantee, outsider);

        shares.save(new DeviceShareEntity(deviceId, "ws-grantee"));
        handler.broadcastToDevice(deviceId, "shared");
        verify(owner).sendMessage(any());
        verify(grantee).sendMessage(any());
        verify(outsider, never()).sendMessage(any());
        clearInvocations(owner, grantee, outsider);

        service.removeShare(deviceId, "ws-owner", "ws-grantee");
        handler.broadcastToDevice(deviceId, "revoked");
        verify(owner).sendMessage(any());
        verify(grantee, never()).sendMessage(any());
        verify(outsider, never()).sendMessage(any());
    }

    // 无主设备即使残留共享关系也不能向任何旧连接泄露数据。
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void unownedDevicesNeverPushThroughStaleShares(String username) throws Exception {
        DeviceEntity device = devices.findByDeviceId(deviceId).get();
        device.setOwnerUsername(username);
        devices.save(device);
        shares.save(new DeviceShareEntity(deviceId, "ws-grantee"));

        handler.broadcastToDevice(deviceId, "unowned");

        verify(owner, never()).sendMessage(any());
        verify(grantee, never()).sendMessage(any());
        verify(outsider, never()).sendMessage(any());
    }

    // 停用或归档后不再向原所有者和共享连接分发遥测。
    @ParameterizedTest
    @ValueSource(strings = {"DISABLED", "ARCHIVED"})
    void inactiveDeviceStopsAllRecipients(String state) throws Exception {
        shares.save(new DeviceShareEntity(deviceId, "ws-grantee"));
        DeviceEntity device = devices.findByDeviceId(deviceId).get();
        if ("ARCHIVED".equals(state)) device.setLifecycleStatus("ARCHIVED");
        else device.setEnabled(false);
        devices.save(device);

        handler.broadcastToDevice(deviceId, "inactive");

        verify(owner, never()).sendMessage(any());
        verify(grantee, never()).sendMessage(any());
    }

    // 创建已认证的长期会话替身，不建立真实网络连接。
    private WebSocketSession connect(String username) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Collections.singletonMap("authenticatedUsername", username));
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
        return session;
    }
}
