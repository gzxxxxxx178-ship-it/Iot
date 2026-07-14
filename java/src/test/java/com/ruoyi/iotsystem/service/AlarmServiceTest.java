package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.AlarmRuleRequest;
import com.ruoyi.iotsystem.entity.AlarmRecordEntity;
import com.ruoyi.iotsystem.entity.AlarmRuleEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.AlarmRecordRepository;
import com.ruoyi.iotsystem.repository.AlarmRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmServiceTest {

    @Mock
    private AlarmRuleRepository alarmRuleRepository;

    @Mock
    private AlarmRecordRepository alarmRecordRepository;

    private AlarmService alarmService;

    // 初始化报警服务
    @BeforeEach
    void setUp() {
        alarmService = new AlarmService(alarmRuleRepository, alarmRecordRepository);
    }

    // 验证创建规则时规范化设备范围并应用默认配置
    @Test
    void createRule_空设备范围_应应用默认值() {
        AlarmRuleRequest request = createRequest("temperature", "gt", 30.0);
        request.setDeviceId(" ");
        request.setEnabled(null);
        request.setCooldownSeconds(null);
        when(alarmRuleRepository.save(any(AlarmRuleEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AlarmRuleEntity result = alarmService.createRule(request);

        assertEquals("*", result.getDeviceId());
        assertEquals(Boolean.TRUE, result.getEnabled());
        assertEquals(300, result.getCooldownSeconds());
    }

    // 验证超出物理范围的湿度阈值会被拒绝
    @Test
    void createRule_湿度阈值超限_应拒绝() {
        AlarmRuleRequest request = createRequest("humidity", "gt", 120.0);

        assertThrows(RuntimeException.class, () -> alarmService.createRule(request));
        verify(alarmRuleRepository, never()).save(any());
    }

    // 验证更新规则会保留主键并替换全部业务字段
    @Test
    void updateRule_规则存在_应更新配置() {
        AlarmRuleEntity existing = createRule(1L, "temperature", "gt", 30.0, "*", 300);
        AlarmRuleRequest request = createRequest("humidity", "lt", 40.0);
        request.setDeviceId("device002");
        request.setCooldownSeconds(60);
        when(alarmRuleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(alarmRuleRepository.save(existing)).thenReturn(existing);

        AlarmRuleEntity result = alarmService.updateRule(1L, request);

        assertEquals(1L, result.getId());
        assertEquals("humidity", result.getMetric());
        assertEquals("lt", result.getOperator());
        assertEquals(40.0, result.getThreshold());
        assertEquals("device002", result.getDeviceId());
        assertEquals(60, result.getCooldownSeconds());
    }

    // 验证传感器越限时生成包含规则快照的报警记录
    @Test
    void evaluate_温度越限_应生成报警记录() {
        AlarmRuleEntity rule = createRule(1L, "temperature", "gt", 30.0, "*", 300);
        EspEntity reading = createReading("device001", 31.2, 60.0, 200.0);
        when(alarmRuleRepository.findByEnabledTrueOrderByIdAsc())
                .thenReturn(Collections.singletonList(rule));
        when(alarmRecordRepository.findFirstByRuleIdAndDeviceIdOrderByCreatedAtDesc(1L, "device001"))
                .thenReturn(Optional.empty());

        alarmService.evaluate(reading);

        ArgumentCaptor<AlarmRecordEntity> captor = ArgumentCaptor.forClass(AlarmRecordEntity.class);
        verify(alarmRecordRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getRuleId());
        assertEquals("device001", captor.getValue().getDeviceId());
        assertEquals(31.2, captor.getValue().getActualValue());
    }

    // 验证冷却期内的重复越限不会生成新记录
    @Test
    void evaluate_冷却期内重复越限_应抑制报警() {
        AlarmRuleEntity rule = createRule(1L, "temperature", "gt", 30.0, "*", 300);
        EspEntity reading = createReading("device001", 31.2, 60.0, 200.0);
        AlarmRecordEntity latest = new AlarmRecordEntity(
                1L, "device001", "temperature", "gt", 30.0, 31.0, "existing");
        latest.setCreatedAt(LocalDateTime.now().minusSeconds(30));
        when(alarmRuleRepository.findByEnabledTrueOrderByIdAsc())
                .thenReturn(Collections.singletonList(rule));
        when(alarmRecordRepository.findFirstByRuleIdAndDeviceIdOrderByCreatedAtDesc(1L, "device001"))
                .thenReturn(Optional.of(latest));

        alarmService.evaluate(reading);

        verify(alarmRecordRepository, never()).save(any());
    }

    // 验证指定设备规则不会作用于其他设备
    @Test
    void evaluate_设备不匹配_不应生成报警() {
        AlarmRuleEntity rule = createRule(1L, "temperature", "gt", 30.0, "device002", 300);
        EspEntity reading = createReading("device001", 31.2, 60.0, 200.0);
        when(alarmRuleRepository.findByEnabledTrueOrderByIdAsc())
                .thenReturn(Collections.singletonList(rule));

        alarmService.evaluate(reading);

        verify(alarmRecordRepository, never()).save(any());
    }

    // 验证只提供单侧时间参数时拒绝查询
    @Test
    void getRecords_时间范围不完整_应拒绝() {
        assertThrows(RuntimeException.class, () -> alarmService.getRecords(LocalDateTime.now(), null));
    }

    // 创建报警规则请求测试数据
    private AlarmRuleRequest createRequest(String metric, String operator, Double threshold) {
        AlarmRuleRequest request = new AlarmRuleRequest();
        request.setMetric(metric);
        request.setOperator(operator);
        request.setThreshold(threshold);
        return request;
    }

    // 创建已持久化的报警规则测试数据
    private AlarmRuleEntity createRule(
            Long id,
            String metric,
            String operator,
            Double threshold,
            String deviceId,
            Integer cooldownSeconds) {
        AlarmRuleEntity rule = new AlarmRuleEntity(
                metric, operator, threshold, true, deviceId, cooldownSeconds);
        rule.setId(id);
        return rule;
    }

    // 创建传感器读数测试数据
    private EspEntity createReading(
            String deviceId,
            Double temperature,
            Double humidity,
            Double water) {
        return new EspEntity(
                deviceId,
                temperature,
                humidity,
                System.currentTimeMillis(),
                water,
                false,
                1,
                -50);
    }
}
