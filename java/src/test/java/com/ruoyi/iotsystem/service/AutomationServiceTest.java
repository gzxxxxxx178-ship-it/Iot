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
    @Mock private MqttMessageService mqttMessageService;

    private AutomationService service;

    // 创建自动化服务测试对象
    @BeforeEach
    void setUp() {
        service = new AutomationService(ruleRepository, executionRepository, mqttMessageService);
    }

    // 验证条件连续命中设定次数后才发布设备动作
    @Test
    void evaluate_连续命中两次_应仅执行一次() {
        AutomationRuleEntity rule = createRule(2, 0);
        when(ruleRepository.findByEnabledTrueOrderByIdAsc())
                .thenReturn(Collections.singletonList(rule));
        EspEntity reading = new EspEntity("device001", 31.0, 60.0, 1L);

        service.evaluate(reading);
        verify(mqttMessageService, never()).publishControl("device001", "start");

        service.evaluate(reading);
        verify(mqttMessageService).publishControl("device001", "start");
        ArgumentCaptor<AutomationExecutionEntity> captor =
                ArgumentCaptor.forClass(AutomationExecutionEntity.class);
        verify(executionRepository).save(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getStatus());
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

        verify(mqttMessageService, never()).publishControl(any(), any());
        verify(executionRepository, never()).save(any());
    }

    // 验证未启用规则不会进入动作执行路径
    @Test
    void evaluate_没有启用规则_应不执行动作() {
        when(ruleRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(Collections.emptyList());

        service.evaluate(new EspEntity("device001", 31.0, 60.0, 1L));

        verify(mqttMessageService, never()).publishControl(any(), any());
        verify(executionRepository, never()).save(any());
    }

    // 验证MQTT发布异常会保存失败记录而不会丢失审计信息
    @Test
    void evaluate_Mqtt发布失败_应保存失败记录() {
        AutomationRuleEntity rule = createRule(1, 0);
        when(ruleRepository.findByEnabledTrueOrderByIdAsc())
                .thenReturn(Collections.singletonList(rule));
        doThrow(new IllegalStateException("MQTT连接不可用"))
                .when(mqttMessageService).publishControl("device001", "start");

        service.evaluate(new EspEntity("device001", 31.0, 60.0, 1L));

        ArgumentCaptor<AutomationExecutionEntity> captor =
                ArgumentCaptor.forClass(AutomationExecutionEntity.class);
        verify(executionRepository).save(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertEquals("MQTT连接不可用", captor.getValue().getMessage());
    }

    // 创建测试使用的高温启动规则
    private AutomationRuleEntity createRule(int debounceCount, int cooldownSeconds) {
        AutomationRuleEntity rule = new AutomationRuleEntity(
                "高温启动", "device001", "temperature", "gt", 30.0,
                "start", true, debounceCount, cooldownSeconds);
        rule.setId(1L);
        return rule;
    }
}
