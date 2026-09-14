package com.ruoyi.iotsystem.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.iotsystem.config.GlobalExceptionHandler;
import com.ruoyi.iotsystem.exception.BusinessException;
import com.ruoyi.iotsystem.simulation.controller.SimulationController;
import com.ruoyi.iotsystem.simulation.dto.CommandFeedbackRequest;
import com.ruoyi.iotsystem.simulation.dto.RuleRequest;
import com.ruoyi.iotsystem.simulation.dto.TelemetryRequest;
import com.ruoyi.iotsystem.simulation.entity.SimulationAlarmEntity;
import com.ruoyi.iotsystem.simulation.entity.SimulationCommandEntity;
import com.ruoyi.iotsystem.simulation.entity.SimulationRuleEntity;
import com.ruoyi.iotsystem.simulation.entity.SimulationTelemetryEntity;
import com.ruoyi.iotsystem.simulation.service.SimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SimulationControllerTest {

    @Mock
    private SimulationService simulationService;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // 搭建独立MockMvc环境，绕过Security过滤器链直接测试Controller
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SimulationController(simulationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // 每个测试前清空SecurityContext
        SecurityContextHolder.clearContext();
    }

    // 每个测试结束后清理线程级认证上下文，避免污染其他控制器测试
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // 设置当前认证用户
    private void setAuthenticatedUser(String username) {
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null,
                Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ==================== 鉴权 ====================

    // 未认证用户访问仿真接口应返回403
    @Test
    void telemetry_未认证_应返回403() throws Exception {
        TelemetryRequest req = createValidTelemetryRequest();
        mockMvc.perform(post("/api/simulation/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ==================== 遥测上传 ====================

    // 合法遥测上传应返回200并持久化
    @Test
    void uploadTelemetry_合法请求_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        TelemetryRequest req = createValidTelemetryRequest();
        req.setOccurredAt(OffsetDateTime.parse("2026-08-10T08:00:00Z"));
        SimulationTelemetryEntity saved = createTelemetryEntity("userA", req);
        when(simulationService.uploadTelemetry(eq("userA"), any(TelemetryRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/simulation/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sampleId").value("sample-001"))
                .andExpect(jsonPath("$.data.sourceType").value("SIMULATION"))
                .andExpect(jsonPath("$.data.occurredAt").value("2026-08-10T08:00:00Z"));
    }

    // 空sampleId应返回400校验错误
    @Test
    void uploadTelemetry_sampleId为空_应返回400() throws Exception {
        setAuthenticatedUser("userA");
        TelemetryRequest req = createValidTelemetryRequest();
        req.setSampleId("");
        mockMvc.perform(post("/api/simulation/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // 无任何指标数据应返回400（业务异常由全局处理器收口）
    @Test
    void uploadTelemetry_无任何指标_应返回400() throws Exception {
        setAuthenticatedUser("userA");
        TelemetryRequest req = createValidTelemetryRequest();
        req.setWaterLevelMm(null);
        when(simulationService.uploadTelemetry(eq("userA"), any(TelemetryRequest.class)))
                .thenThrow(new BusinessException("至少需要提供一个有效数值指标"));

        mockMvc.perform(post("/api/simulation/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // 遥测越界值应返回400
    @Test
    void uploadTelemetry_水位越界_应返回400() throws Exception {
        setAuthenticatedUser("userA");
        TelemetryRequest req = createValidTelemetryRequest();
        req.setWaterLevelMm(600.0);
        when(simulationService.uploadTelemetry(eq("userA"), any(TelemetryRequest.class)))
                .thenThrow(new BusinessException("水位超出合法范围"));

        mockMvc.perform(post("/api/simulation/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 遥测查询 ====================

    // 查询最新遥测应返回200
    @Test
    void getLatestTelemetry_有数据_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        SimulationTelemetryEntity entity = createTelemetryEntity("userA", createValidTelemetryRequest());
        when(simulationService.getLatestTelemetry("userA", "device-001")).thenReturn(entity);

        mockMvc.perform(get("/api/simulation/telemetry/latest")
                        .param("deviceId", "device-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // 查询历史遥测应返回200
    @Test
    void getTelemetryHistory_合法参数_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        when(simulationService.getTelemetryHistory(eq("userA"), eq("device-001"), anyInt()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/simulation/telemetry/history")
                        .param("deviceId", "device-001")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // 超限limit应返回400
    @Test
    void getTelemetryHistory_limit超限_应返回400() throws Exception {
        setAuthenticatedUser("userA");
        mockMvc.perform(get("/api/simulation/telemetry/history")
                        .param("deviceId", "device-001")
                        .param("limit", "600"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 规则CRUD ====================

    // 查询规则应返回200
    @Test
    void getRules_有数据_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        when(simulationService.getRules("userA")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/simulation/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // 创建合法规则应返回200
    @Test
    void createRule_合法请求_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        RuleRequest req = createValidRuleRequest();
        SimulationRuleEntity entity = createRuleEntity("userA", req);
        when(simulationService.createRule(eq("userA"), any(RuleRequest.class))).thenReturn(entity);

        mockMvc.perform(post("/api/simulation/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Test Rule"));
    }

    // 更新规则应返回200
    @Test
    void updateRule_合法请求_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        RuleRequest req = createValidRuleRequest();
        SimulationRuleEntity entity = createRuleEntity("userA", req);
        entity.setId(1L);
        when(simulationService.updateRule(eq("userA"), eq(1L), any(RuleRequest.class))).thenReturn(entity);

        mockMvc.perform(put("/api/simulation/rules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // 删除规则应返回200
    @Test
    void deleteRule_合法请求_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        doNothing().when(simulationService).deleteRule("userA", 1L);

        mockMvc.perform(delete("/api/simulation/rules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 报警 ====================

    // 查询报警应返回200
    @Test
    void getAlarms_合法参数_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        when(simulationService.getAlarms("userA", "device-001", null))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/simulation/alarms")
                        .param("deviceId", "device-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // 确认报警应返回200
    @Test
    void acknowledgeAlarm_合法请求_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        SimulationAlarmEntity alarm = new SimulationAlarmEntity(
                1L, "device-001", "userA", "waterLevelMm", "gt",
                200.0, 150.0, 250.0, "WARN", "NOTIFY", "test");
        alarm.setStatus("ACKNOWLEDGED");
        when(simulationService.acknowledgeAlarm("userA", 1L)).thenReturn(alarm);

        mockMvc.perform(post("/api/simulation/alarms/1/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));
    }

    // ==================== 命令 ====================

    // 查询待处理命令应返回200
    @Test
    void getPendingCommands_合法请求_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        when(simulationService.getPendingCommands("userA", "device-001"))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/simulation/commands/pending")
                        .param("deviceId", "device-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // 提交命令反馈应返回200
    @Test
    void submitFeedback_合法请求_应返回200() throws Exception {
        setAuthenticatedUser("userA");
        CommandFeedbackRequest feedbackReq = new CommandFeedbackRequest();
        feedbackReq.setStatus("SUCCESS");
        feedbackReq.setMessage("执行成功");
        SimulationCommandEntity cmd = new SimulationCommandEntity(
                1L, "device-001", "userA", "STOP_IRRIGATION");
        cmd.setStatus("SUCCESS");
        when(simulationService.submitFeedback(eq("userA"), eq(1L), any(CommandFeedbackRequest.class)))
                .thenReturn(cmd);

        mockMvc.perform(post("/api/simulation/commands/1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    // ==================== 测试数据工厂方法 ====================

    // 创建合法的遥测请求
    private TelemetryRequest createValidTelemetryRequest() {
        TelemetryRequest req = new TelemetryRequest();
        req.setSampleId("sample-001");
        req.setDeviceId("device-001");
        req.setOccurredAt(OffsetDateTime.now());
        req.setWaterLevelMm(150.0);
        req.setFlowRateLMin(5.0);
        req.setSoilMoisturePct(60.0);
        req.setPumpOn(false);
        req.setIrrigationValveOpen(true);
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

    // 创建合法的规则请求
    private RuleRequest createValidRuleRequest() {
        RuleRequest req = new RuleRequest();
        req.setName("Test Rule");
        req.setDeviceId("device-001");
        req.setMetric("waterLevelMm");
        req.setOperator("gt");
        req.setThreshold(200.0);
        req.setRecoveryThreshold(150.0);
        req.setDebounceCount(2);
        req.setSeverity("WARN");
        req.setAction("NOTIFY");
        req.setEnabled(true);
        return req;
    }

    // 创建规则实体测试数据
    private SimulationRuleEntity createRuleEntity(String owner, RuleRequest req) {
        return new SimulationRuleEntity(
                req.getName(), req.getDeviceId(), owner,
                req.getMetric(), req.getOperator(),
                req.getThreshold(), req.getRecoveryThreshold(),
                req.getDebounceCount() == null ? 1 : req.getDebounceCount(),
                req.getSeverity(), req.getAction(),
                req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
    }
}
