package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.AutomationExecutionEntity;
import com.ruoyi.iotsystem.entity.AutomationRuleEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.AutomationExecutionRepository;
import com.ruoyi.iotsystem.repository.AutomationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationServiceTest {

    @Mock private AutomationRuleRepository ruleRepository;
    @Mock private AutomationExecutionRepository executionRepository;
    @Mock private DeviceCommandService deviceCommandService;

    private AutomationService service;

    // 创建自动化服务测试对象
    @BeforeEach
    void setUp() {
        service = new AutomationService(ruleRepository, executionRepository, deviceCommandService);
    }

    // 验证条件连续命中设定次数后才发布设备动作
    @Test
    void evaluate_连续命中两次_应仅执行一次() {
        AutomationRuleEntity rule = createRule(2, 0);
        when(ruleRepository.findByEnabledTrueOrderByIdAsc())
                .thenReturn(Collections.singletonList(rule));
        EspEntity reading = new EspEntity("device001", 31.0, 60.0, 1L);

        service.evaluate(reading);
        verify(deviceCommandService, never()).issue("device001", null, "start");

        when(deviceCommandService.issue("device001", null, "start"))
                .thenReturn(command("DISPATCHED", "已发布，等待设备确认"));
        service.evaluate(reading);
        verify(deviceCommandService).issue("device001", null, "start");
        ArgumentCaptor<AutomationExecutionEntity> captor =
                ArgumentCaptor.forClass(AutomationExecutionEntity.class);
        verify(executionRepository).save(captor.capture());
        assertEquals("DISPATCHED", captor.getValue().getStatus());
        assertEquals(31.0, captor.getValue().getActualValue());
        assertNotNull(rule.getLastTriggeredAt());
    }

    // 验证冷却期内即使条件满足也不会重复下发动作
    @Test
    void evaluate_处于冷却期_应跳过动作() {
        AutomationRuleEntity rule = createRule(1, 300);
        rule.setLastTriggeredAt(LocalDateTime.now());
        when(ruleRepository.findByEnabledTrueOrderByIdAsc())
                .thenReturn(Collections.singletonList(rule));

        service.evaluate(new EspEntity("device001", 31.0, 60.0, 1L));

        verify(deviceCommandService, never()).issue(any(), any(), any());
        verify(executionRepository, never()).save(any());
    }

    // 验证未启用规则不会进入动作执行路径
    @Test
    void evaluate_没有启用规则_应不执行动作() {
        when(ruleRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(Collections.emptyList());

        service.evaluate(new EspEntity("device001", 31.0, 60.0, 1L));

        verify(deviceCommandService, never()).issue(any(), any(), any());
        verify(executionRepository, never()).save(any());
    }

    // 验证命令服务返回失败状态时仍会保存自动化审计信息
    @Test
    void evaluate_Mqtt发布失败_应保存失败记录() {
        AutomationRuleEntity rule = createRule(1, 0);
        when(ruleRepository.findByEnabledTrueOrderByIdAsc())
                .thenReturn(Collections.singletonList(rule));
        when(deviceCommandService.issue("device001", null, "start"))
                .thenReturn(command("FAILED", "MQTT发布失败"));

        service.evaluate(new EspEntity("device001", 31.0, 60.0, 1L));

        ArgumentCaptor<AutomationExecutionEntity> captor =
                ArgumentCaptor.forClass(AutomationExecutionEntity.class);
        verify(executionRepository).save(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertEquals("FAILED", captor.getValue().getStatus());
    }

    // 创建测试使用的高温启动规则
    private AutomationRuleEntity createRule(int debounceCount, int cooldownSeconds) {
        AutomationRuleEntity rule = new AutomationRuleEntity(
                "高温启动", "device001", "temperature", "gt", 30.0,
                "start", true, debounceCount, cooldownSeconds);
        rule.setId(1L);
        return rule;
    }

    private com.ruoyi.iotsystem.entity.DeviceCommandEntity command(String status, String message) {
        com.ruoyi.iotsystem.entity.DeviceCommandEntity command =
                new com.ruoyi.iotsystem.entity.DeviceCommandEntity("command-1", "device001", null, "start");
        command.setStatus(status);
        command.setMessage(message);
        return command;
    }
}
