package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.TestInfrastructureConfiguration;
import com.ruoyi.iotsystem.dto.AutomationExecutionView;
import com.ruoyi.iotsystem.entity.AutomationExecutionEntity;
import com.ruoyi.iotsystem.entity.AutomationRuleEntity;
import com.ruoyi.iotsystem.entity.DeviceEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.AutomationExecutionRepository;
import com.ruoyi.iotsystem.repository.AutomationRuleRepository;
import com.ruoyi.iotsystem.repository.DeviceCommandRepository;
import com.ruoyi.iotsystem.repository.DeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "device.command.timeout-seconds=5",
        "device.command.dispatch-interval-ms=3600000",
        "device.command.timeout-scan-interval-ms=3600000"
})
@ActiveProfiles("test")
@Import(TestInfrastructureConfiguration.class)
class AutomationExecutionStatusIntegrationTest {
    @Autowired private AutomationService service;
    @Autowired private DeviceCommandService commandService;
    @Autowired private AutomationRuleRepository rules;
    @Autowired private AutomationExecutionRepository executions;
    @Autowired private DeviceCommandRepository commands;
    @Autowired private DeviceRepository devices;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private MqttMessageService mqtt;
    private String owner;
    private String deviceId;

    // 在H2中创建独立账号归属的设备和单次触发规则，不连接真实设备。
    @BeforeEach
    void setUp() {
        owner = "automation-" + UUID.randomUUID();
        deviceId = "auto-" + UUID.randomUUID();
        devices.save(new DeviceEntity(deviceId, deviceId, "ESP8266", null, null, true, owner));
        rules.save(new AutomationRuleEntity("高温采集", deviceId, "temperature", "gt", 30.0,
                "start", true, 1, 300, owner));
    }

    // 删除本测试创建的审计、规则、命令和设备。
    @AfterEach
    void cleanUp() {
        executions.deleteAll(executions.findTop100ByOwnerUsernameOrderByCreatedAtDesc(owner));
        rules.deleteAll(rules.findByOwnerUsernameOrderByIdDesc(owner));
        commands.deleteAll(commands.findTop100ByOwnerUsernameOrderByCreatedAtDesc(owner));
        devices.findByDeviceId(deviceId).ifPresent(devices::delete);
    }

    // 经真实评估、提交派发和回执/失败/超时后，列表显示当前状态且数据库保留触发快照。
    @ParameterizedTest
    @ValueSource(strings = {"ACKNOWLEDGED", "REJECTED", "FAILED", "TIMED_OUT"})
    void executionListTracksRealCommandTerminalState(String terminal) {
        if ("FAILED".equals(terminal)) {
            doThrow(new IllegalStateException("isolated publish failure"))
                    .when(mqtt).publishControl(eq(deviceId), eq("start"), anyString());
        } else if (!"TIMED_OUT".equals(terminal)) {
            doAnswer(invocation -> {
                commandService.acknowledge(deviceId, invocation.getArgument(2), terminal);
                return null;
            }).when(mqtt).publishControl(eq(deviceId), eq("start"), anyString());
        }

        service.evaluate(new EspEntity(deviceId, 31.0, 60.0, 1L), owner);
        AutomationExecutionEntity snapshot = executions.findTop100ByOwnerUsernameOrderByCreatedAtDesc(owner).get(0);
        assertNotNull(snapshot.getCommandId());
        assertEquals("PENDING", snapshot.getStatus());
        if ("TIMED_OUT".equals(terminal)) {
            jdbc.update("UPDATE device_commands SET dispatched_at = DATEADD('SECOND', -30, CURRENT_TIMESTAMP) WHERE command_id = ?",
                    snapshot.getCommandId());
            commandService.expireUnacknowledgedCommands();
        }

        AutomationExecutionView view = service.getExecutions(owner).get(0);

        assertTrue(view.isCommandLinked());
        assertEquals(snapshot.getCommandId(), view.getCommandId());
        assertEquals(terminal, view.getStatus());
        assertEquals("PENDING", view.getInitialStatus());
        assertEquals("PENDING", executions.findById(snapshot.getId()).get().getStatus());
        assertTrue(service.getExecutions("other-account").isEmpty());
        verify(mqtt, times(1)).publishControl(deviceId, "start", snapshot.getCommandId());
    }
}
