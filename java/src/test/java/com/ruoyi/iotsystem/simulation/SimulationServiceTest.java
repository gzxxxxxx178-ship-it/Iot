package com.ruoyi.iotsystem.simulation;

import com.ruoyi.iotsystem.simulation.dto.CommandFeedbackRequest;
import com.ruoyi.iotsystem.simulation.dto.RuleRequest;
import com.ruoyi.iotsystem.simulation.dto.TelemetryRequest;
import com.ruoyi.iotsystem.simulation.entity.SimulationAlarmEntity;
import com.ruoyi.iotsystem.simulation.entity.SimulationCommandEntity;
import com.ruoyi.iotsystem.simulation.entity.SimulationRuleEntity;
import com.ruoyi.iotsystem.simulation.entity.SimulationTelemetryEntity;
import com.ruoyi.iotsystem.simulation.repository.SimulationAlarmRepository;
import com.ruoyi.iotsystem.simulation.repository.SimulationCommandRepository;
import com.ruoyi.iotsystem.simulation.repository.SimulationRuleRepository;
import com.ruoyi.iotsystem.simulation.repository.SimulationTelemetryRepository;
import com.ruoyi.iotsystem.simulation.service.SimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    @Mock
    private SimulationTelemetryRepository telemetryRepository;

    @Mock
    private SimulationRuleRepository ruleRepository;

    @Mock
    private SimulationAlarmRepository alarmRepository;

    @Mock
    private SimulationCommandRepository commandRepository;

    private SimulationService simulationService;

    // 初始化仿真服务并注入mock仓库
    @BeforeEach
    void setUp() {
        simulationService = new SimulationService(
                telemetryRepository, ruleRepository, alarmRepository, commandRepository);
    }

    // ==================== 遥测上传 ====================

    // 合法遥测应持久化并运行规则引擎
    @Test
    void uploadTelemetry_合法数据_应保存并评估规则() {
        TelemetryRequest req = createTelemetryRequest("sample-001", "dev-01", 150.0, null, null, null, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "sample-001"))
                .thenReturn(Optional.empty());
        when(telemetryRepository.save(any(SimulationTelemetryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleRepository.findByEnabledTrueAndOwnerUsernameOrderByIdAsc("userA"))
                .thenReturn(Collections.emptyList());
        when(alarmRepository.findByOwnerUsernameAndDeviceIdAndStatusNot("userA", "dev-01", "RESOLVED"))
                .thenReturn(Collections.emptyList());

        SimulationTelemetryEntity result = simulationService.uploadTelemetry("userA", req);

        assertNotNull(result);
        assertEquals("sample-001", result.getSampleId());
        assertEquals("SIMULATION", result.getSourceType());
    }

    // 重复sampleId应返回已有记录不重复执行规则
    @Test
    void uploadTelemetry_重复sampleId_应返回已有记录() {
        TelemetryRequest req = createTelemetryRequest("sample-001", "dev-01", 150.0, null, null, null, null);
        SimulationTelemetryEntity existing = createTelemetryEntity("userA", req);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "sample-001"))
                .thenReturn(Optional.of(existing));

        SimulationTelemetryEntity result = simulationService.uploadTelemetry("userA", req);

        assertEquals(existing, result);
        // 不应触发规则引擎
        verify(ruleRepository, never()).findByEnabledTrueAndOwnerUsernameOrderByIdAsc(anyString());
    }

    // 无指标数据应拒绝
    @Test
    void uploadTelemetry_无指标_应拒绝() {
        TelemetryRequest req = createTelemetryRequest("sample-001", "dev-01",
                null, null, null, null, null);
        assertThrows(RuntimeException.class, () -> simulationService.uploadTelemetry("userA", req));
    }

    // NaN值应拒绝
    @Test
    void uploadTelemetry_NaN值_应拒绝() {
        TelemetryRequest req = createTelemetryRequest("sample-001", "dev-01",
                Double.NaN, null, null, null, null);
        assertThrows(RuntimeException.class, () -> simulationService.uploadTelemetry("userA", req));
    }

    // ==================== 规则CRUD ====================

    // 创建规则时gt的recoveryThreshold >= threshold应拒绝
    @Test
    void createRule_gt规则恢复阈值不小于触发阈值_应拒绝() {
        RuleRequest req = createRuleRequest("dev-01", "waterLevelMm", "gt", 200.0, 250.0);
        assertThrows(RuntimeException.class, () -> simulationService.createRule("userA", req));
    }

    // 创建规则时lt的recoveryThreshold <= threshold应拒绝
    @Test
    void createRule_lt规则恢复阈值不大于触发阈值_应拒绝() {
        RuleRequest req = createRuleRequest("dev-01", "waterLevelMm", "lt", 100.0, 50.0);
        assertThrows(RuntimeException.class, () -> simulationService.createRule("userA", req));
    }

    // 创建合法规则应成功持久化
    @Test
    void createRule_合法参数_应保存() {
        RuleRequest req = createRuleRequest("dev-01", "waterLevelMm", "gt", 200.0, 150.0);
        when(ruleRepository.save(any(SimulationRuleEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SimulationRuleEntity result = simulationService.createRule("userA", req);

        assertNotNull(result);
        assertEquals("userA", result.getOwnerUsername());
        assertEquals("waterLevelMm", result.getMetric());
    }

    // 查询规则按用户过滤
    @Test
    void getRules_应返回用户规则() {
        when(ruleRepository.findByOwnerUsernameOrderByIdDesc("userA"))
                .thenReturn(Collections.emptyList());

        List<SimulationRuleEntity> result = simulationService.getRules("userA");
        assertNotNull(result);
    }

    // ==================== 规则引擎 ====================

    // gt规则水位越限且达到防抖次数应触发报警
    @Test
    void evaluateRules_gt越限达防抖_应触发报警() {
        SimulationRuleEntity rule = createRuleEntity(1L, "userA", "*", "waterLevelMm",
                "gt", 200.0, 150.0, 2, "WARN", "NOTIFY");
        when(ruleRepository.findByEnabledTrueAndOwnerUsernameOrderByIdAsc("userA"))
                .thenReturn(Arrays.asList(rule));
        // 无未恢复报警
        when(alarmRepository.findFirstByOwnerUsernameAndRuleIdAndDeviceIdAndStatusNotOrderByTriggeredAtDesc(
                "userA", 1L, "dev-01", "RESOLVED")).thenReturn(Optional.empty());
        when(alarmRepository.findByOwnerUsernameAndDeviceIdAndStatusNot("userA", "dev-01", "RESOLVED"))
                .thenReturn(Collections.emptyList());
        // 遥测数据准备好
        TelemetryRequest req = createTelemetryRequest("s-001", "dev-01", 250.0, null, null, null, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "s-001"))
                .thenReturn(Optional.empty());
        when(telemetryRepository.save(any(SimulationTelemetryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 第一次上传：计数=1，不到debounceCount=2
        simulationService.uploadTelemetry("userA", req);
        verify(alarmRepository, never()).save(any(SimulationAlarmEntity.class));

        TelemetryRequest req2 = createTelemetryRequest("s-002", "dev-01", 260.0, null, null, null, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "s-002"))
                .thenReturn(Optional.empty());

        // 第二次上传：计数=2，达到debounceCount，触发报警
        simulationService.uploadTelemetry("userA", req2);
        verify(alarmRepository, times(1)).save(any(SimulationAlarmEntity.class));
    }

    // 越限后数值回到正常范围应重置防抖计数
    @Test
    void evaluateRules_越限后恢复_应重置防抖() {
        SimulationRuleEntity rule = createRuleEntity(1L, "userA", "*", "waterLevelMm",
                "gt", 200.0, 150.0, 3, "WARN", "NOTIFY");
        when(ruleRepository.findByEnabledTrueAndOwnerUsernameOrderByIdAsc("userA"))
                .thenReturn(Arrays.asList(rule));
        when(alarmRepository.findByOwnerUsernameAndDeviceIdAndStatusNot("userA", "dev-01", "RESOLVED"))
                .thenReturn(Collections.emptyList());
        // 第一次越限
        TelemetryRequest req1 = createTelemetryRequest("s-001", "dev-01", 250.0, null, null, null, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "s-001"))
                .thenReturn(Optional.empty());
        when(telemetryRepository.save(any(SimulationTelemetryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        simulationService.uploadTelemetry("userA", req1);

        // 第二次正常（不越限）- 重置计数
        TelemetryRequest req2 = createTelemetryRequest("s-002", "dev-01", 150.0, null, null, null, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "s-002"))
                .thenReturn(Optional.empty());
        simulationService.uploadTelemetry("userA", req2);

        // 第三次越限 - 计数从头开始
        TelemetryRequest req3 = createTelemetryRequest("s-003", "dev-01", 260.0, null, null, null, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "s-003"))
                .thenReturn(Optional.empty());
        simulationService.uploadTelemetry("userA", req3);

        // 第四次越限 - debounceCount=3仍未达到
        TelemetryRequest req4 = createTelemetryRequest("s-004", "dev-01", 270.0, null, null, null, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "s-004"))
                .thenReturn(Optional.empty());
        simulationService.uploadTelemetry("userA", req4);

        // 仍未触发（因为第一次后重置了）
        verify(alarmRepository, never()).save(any(SimulationAlarmEntity.class));
    }

    // 非NOTIFY报警触发时应创建仿真命令
    @Test
    void evaluateRules_STOP_IRRIGATION报警_应创建命令() {
        SimulationRuleEntity rule = createRuleEntity(1L, "userA", "*", "waterLevelMm",
                "gt", 200.0, 150.0, 1, "CRITICAL", "STOP_IRRIGATION");
        when(ruleRepository.findByEnabledTrueAndOwnerUsernameOrderByIdAsc("userA"))
                .thenReturn(Arrays.asList(rule));
        when(alarmRepository.findByOwnerUsernameAndDeviceIdAndStatusNot("userA", "dev-01", "RESOLVED"))
                .thenReturn(Collections.emptyList());
        when(alarmRepository.findFirstByOwnerUsernameAndRuleIdAndDeviceIdAndStatusNotOrderByTriggeredAtDesc(
                "userA", 1L, "dev-01", "RESOLVED")).thenReturn(Optional.empty());

        TelemetryRequest req = createTelemetryRequest("s-001", "dev-01", 250.0, null, null, null, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "s-001"))
                .thenReturn(Optional.empty());
        when(telemetryRepository.save(any(SimulationTelemetryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        simulationService.uploadTelemetry("userA", req);

        verify(alarmRepository, times(1)).save(any(SimulationAlarmEntity.class));
        verify(commandRepository, times(1)).save(any(SimulationCommandEntity.class));
    }

    // NOTIFY报警不应创建命令
    @Test
    void evaluateRules_NOTIFY报警_不应创建命令() {
        SimulationRuleEntity rule = createRuleEntity(1L, "userA", "*", "waterLevelMm",
                "gt", 200.0, 150.0, 1, "WARN", "NOTIFY");
        when(ruleRepository.findByEnabledTrueAndOwnerUsernameOrderByIdAsc("userA"))
                .thenReturn(Arrays.asList(rule));
        when(alarmRepository.findByOwnerUsernameAndDeviceIdAndStatusNot("userA", "dev-01", "RESOLVED"))
                .thenReturn(Collections.emptyList());
        when(alarmRepository.findFirstByOwnerUsernameAndRuleIdAndDeviceIdAndStatusNotOrderByTriggeredAtDesc(
                "userA", 1L, "dev-01", "RESOLVED")).thenReturn(Optional.empty());

        TelemetryRequest req = createTelemetryRequest("s-001", "dev-01", 250.0, null, null, null, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "s-001"))
                .thenReturn(Optional.empty());
        when(telemetryRepository.save(any(SimulationTelemetryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        simulationService.uploadTelemetry("userA", req);

        verify(alarmRepository, times(1)).save(any(SimulationAlarmEntity.class));
        verify(commandRepository, never()).save(any(SimulationCommandEntity.class));
    }

    // ==================== 恢复滞回 ====================

    // gt报警：数值降到recoveryThreshold及以下应恢复
    @Test
    void checkRecovery_gt报警数值降到恢复阈值以下_应恢复() {
        SimulationAlarmEntity alarm = createAlarmEntity(1L, "dev-01", "userA", "waterLevelMm",
                "gt", 200.0, 150.0, 250.0, "ACTIVE");
        when(alarmRepository.findByOwnerUsernameAndDeviceIdAndStatusNot("userA", "dev-01", "RESOLVED"))
                .thenReturn(Arrays.asList(alarm));
        when(ruleRepository.findByEnabledTrueAndOwnerUsernameOrderByIdAsc("userA"))
                .thenReturn(Collections.emptyList());

        // 数值降到130（≤150），应恢复
        TelemetryRequest req = createTelemetryRequest("s-001", "dev-01", 130.0, null, null, null, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "s-001"))
                .thenReturn(Optional.empty());
        when(telemetryRepository.save(any(SimulationTelemetryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        simulationService.uploadTelemetry("userA", req);

        ArgumentCaptor<SimulationAlarmEntity> captor = ArgumentCaptor.forClass(SimulationAlarmEntity.class);
        verify(alarmRepository, times(1)).save(captor.capture());
        assertEquals("RESOLVED", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getResolvedAt());
    }

    // lt报警：数值升到recoveryThreshold及以上应恢复
    @Test
    void checkRecovery_lt报警数值升到恢复阈值以上_应恢复() {
        SimulationAlarmEntity alarm = createAlarmEntity(1L, "dev-01", "userA", "soilMoisturePct",
                "lt", 20.0, 40.0, 10.0, "ACTIVE");
        when(alarmRepository.findByOwnerUsernameAndDeviceIdAndStatusNot("userA", "dev-01", "RESOLVED"))
                .thenReturn(Arrays.asList(alarm));
        when(ruleRepository.findByEnabledTrueAndOwnerUsernameOrderByIdAsc("userA"))
                .thenReturn(Collections.emptyList());

        // 数值升到45（≥40），应恢复
        TelemetryRequest req = createTelemetryRequest("s-001", "dev-01", null, null, null, 45.0, null);
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userA", "s-001"))
                .thenReturn(Optional.empty());
        when(telemetryRepository.save(any(SimulationTelemetryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        simulationService.uploadTelemetry("userA", req);

        ArgumentCaptor<SimulationAlarmEntity> captor = ArgumentCaptor.forClass(SimulationAlarmEntity.class);
        verify(alarmRepository, times(1)).save(captor.capture());
        assertEquals("RESOLVED", captor.getValue().getStatus());
    }

    // ==================== 报警确认 ====================

    // ACTIVE报警可被确认
    @Test
    void acknowledgeAlarm_ACTIVE状态_应变为ACKNOWLEDGED() {
        SimulationAlarmEntity alarm = createAlarmEntity(1L, "dev-01", "userA", "waterLevelMm",
                "gt", 200.0, 150.0, 250.0, "ACTIVE");
        when(alarmRepository.findById(1L)).thenReturn(Optional.of(alarm));
        when(alarmRepository.save(any(SimulationAlarmEntity.class))).thenReturn(alarm);

        SimulationAlarmEntity result = simulationService.acknowledgeAlarm("userA", 1L);

        assertEquals("ACKNOWLEDGED", result.getStatus());
        assertNotNull(result.getAcknowledgedAt());
    }

    // ACKNOWLEDGED报警不可再次确认
    @Test
    void acknowledgeAlarm_已确认状态_应拒绝() {
        SimulationAlarmEntity alarm = createAlarmEntity(1L, "dev-01", "userA", "waterLevelMm",
                "gt", 200.0, 150.0, 250.0, "ACKNOWLEDGED");
        when(alarmRepository.findById(1L)).thenReturn(Optional.of(alarm));

        assertThrows(RuntimeException.class, () -> simulationService.acknowledgeAlarm("userA", 1L));
    }

    // ==================== 命令反馈 ====================

    // PENDING命令可反馈为SUCCESS
    @Test
    void submitFeedback_PENDING状态_可设为SUCCESS() {
        SimulationCommandEntity cmd = new SimulationCommandEntity(1L, "dev-01", "userA", "STOP_IRRIGATION");
        when(commandRepository.findById(1L)).thenReturn(Optional.of(cmd));
        when(commandRepository.save(any(SimulationCommandEntity.class))).thenReturn(cmd);

        CommandFeedbackRequest feedbackReq = new CommandFeedbackRequest();
        feedbackReq.setStatus("SUCCESS");
        feedbackReq.setMessage("done");

        SimulationCommandEntity result = simulationService.submitFeedback("userA", 1L, feedbackReq);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("done", result.getMessage());
        assertNotNull(result.getFeedbackAt());
    }

    // 已终态的命令不可重复反馈
    @Test
    void submitFeedback_已SUCCESS_应拒绝重复反馈() {
        SimulationCommandEntity cmd = new SimulationCommandEntity(1L, "dev-01", "userA", "STOP_IRRIGATION");
        cmd.setStatus("SUCCESS");
        when(commandRepository.findById(1L)).thenReturn(Optional.of(cmd));

        CommandFeedbackRequest feedbackReq = new CommandFeedbackRequest();
        feedbackReq.setStatus("FAILED");

        assertThrows(RuntimeException.class,
                () -> simulationService.submitFeedback("userA", 1L, feedbackReq));
    }

    // ==================== 用户隔离 ====================

    // 不同用户的相同sampleId应分别处理
    @Test
    void uploadTelemetry_不同用户相同sampleId_应分别保存() {
        TelemetryRequest req = createTelemetryRequest("sample-001", "dev-01", 150.0, null, null, null, null);
        // userA不存在此sampleId
        when(telemetryRepository.findByOwnerUsernameAndSampleId("userB", "sample-001"))
                .thenReturn(Optional.empty());
        when(telemetryRepository.save(any(SimulationTelemetryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleRepository.findByEnabledTrueAndOwnerUsernameOrderByIdAsc("userB"))
                .thenReturn(Collections.emptyList());
        when(alarmRepository.findByOwnerUsernameAndDeviceIdAndStatusNot("userB", "dev-01", "RESOLVED"))
                .thenReturn(Collections.emptyList());

        SimulationTelemetryEntity result = simulationService.uploadTelemetry("userB", req);

        assertNotNull(result);
        assertEquals("userB", result.getOwnerUsername());
        assertEquals("sample-001", result.getSampleId());
    }

    // 跨用户访问资源应拒绝
    @Test
    void acknowledgeAlarm_跨用户访问_应拒绝() {
        SimulationAlarmEntity alarm = createAlarmEntity(1L, "dev-01", "userA", "waterLevelMm",
                "gt", 200.0, 150.0, 250.0, "ACTIVE");
        when(alarmRepository.findById(1L)).thenReturn(Optional.of(alarm));

        assertThrows(SecurityException.class,
                () -> simulationService.acknowledgeAlarm("userB", 1L));
    }

    // ==================== 查询 ====================

    // 查询遥测历史按limit截断
    @Test
    void getTelemetryHistory_超过limit_应截断() {
        SimulationTelemetryEntity e1 = createTelemetryEntity("userA",
                createTelemetryRequest("s-01", "dev-01", 100.0, null, null, null, null));
        SimulationTelemetryEntity e2 = createTelemetryEntity("userA",
                createTelemetryRequest("s-02", "dev-01", 200.0, null, null, null, null));
        when(telemetryRepository.findByOwnerUsernameAndDeviceIdOrderByOccurredAtDesc("userA", "dev-01"))
                .thenReturn(Arrays.asList(e1, e2));

        List<SimulationTelemetryEntity> result = simulationService.getTelemetryHistory("userA", "dev-01", 1);

        assertEquals(1, result.size());
    }

    // ==================== 测试数据工厂方法 ====================

    // 创建遥测请求测试数据
    private TelemetryRequest createTelemetryRequest(String sampleId, String deviceId,
            Double waterLevel, Double flowRate, Double ec,
            Double soilMoisture, Double rainfall) {
        TelemetryRequest req = new TelemetryRequest();
        req.setSampleId(sampleId);
        req.setDeviceId(deviceId);
        req.setOccurredAt(OffsetDateTime.now());
        req.setWaterLevelMm(waterLevel);
        req.setFlowRateLMin(flowRate);
        req.setEcMsCm(ec);
        req.setSoilMoisturePct(soilMoisture);
        req.setRainfallMm(rainfall);
        return req;
    }

    // 创建遥测实体测试数据
    private SimulationTelemetryEntity createTelemetryEntity(String owner, TelemetryRequest req) {
        return new SimulationTelemetryEntity(
                req.getSampleId(), req.getDeviceId(), owner,
                LocalDateTime.ofInstant(req.getOccurredAt().toInstant(), ZoneOffset.UTC),
                req.getGrowthStage(), req.getScenarioCode(),
                req.getWaterLevelMm(), req.getFlowRateLMin(), req.getEcMsCm(),
                req.getSoilMoisturePct(), req.getRainfallMm(),
                req.getPumpOn(), req.getIrrigationValveOpen(), req.getFertilizerPumpOn());
    }

    // 创建规则请求测试数据
    private RuleRequest createRuleRequest(String deviceId, String metric,
            String operator, Double threshold, Double recoveryThreshold) {
        RuleRequest req = new RuleRequest();
        req.setName("Test Rule");
        req.setDeviceId(deviceId);
        req.setMetric(metric);
        req.setOperator(operator);
        req.setThreshold(threshold);
        req.setRecoveryThreshold(recoveryThreshold);
        req.setDebounceCount(2);
        req.setSeverity("WARN");
        req.setAction("NOTIFY");
        req.setEnabled(true);
        return req;
    }

    // 创建规则实体测试数据
    private SimulationRuleEntity createRuleEntity(Long id, String owner, String deviceId,
            String metric, String operator, Double threshold, Double recoveryThreshold,
            int debounceCount, String severity, String action) {
        SimulationRuleEntity rule = new SimulationRuleEntity(
                "Test", deviceId, owner, metric, operator, threshold, recoveryThreshold,
                debounceCount, severity, action, true);
        rule.setId(id);
        return rule;
    }

    // 创建报警实体测试数据
    private SimulationAlarmEntity createAlarmEntity(Long id, String deviceId, String owner,
            String metric, String operator, Double threshold, Double recoveryThreshold,
            Double actualValue, String status) {
        SimulationAlarmEntity alarm = new SimulationAlarmEntity(
                id, deviceId, owner, metric, operator, threshold, recoveryThreshold,
                actualValue, "WARN", "NOTIFY", "test message");
        alarm.setId(id);
        alarm.setStatus(status);
        return alarm;
    }
}
