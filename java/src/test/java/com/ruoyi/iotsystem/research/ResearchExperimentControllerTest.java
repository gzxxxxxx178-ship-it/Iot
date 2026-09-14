package com.ruoyi.iotsystem.research;

import com.ruoyi.iotsystem.config.GlobalExceptionHandler;
import com.ruoyi.iotsystem.research.controller.ResearchExperimentController;
import com.ruoyi.iotsystem.research.dto.ExperimentRunRequest;
import com.ruoyi.iotsystem.research.dto.ExperimentRunResponse;
import com.ruoyi.iotsystem.research.dto.ResearchOverviewResponse;
import com.ruoyi.iotsystem.research.entity.ExperimentMetricEntity;
import com.ruoyi.iotsystem.research.service.ResearchExperimentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 研究实验控制器单元测试。
 * 覆盖幂等上传、owner隔离、枚举校验、详情越权和总览不伪造。
 */
@ExtendWith(MockitoExtension.class)
class ResearchExperimentControllerTest {

    @Mock
    private ResearchExperimentService experimentService;

    private MockMvc mockMvc;

    // 创建仅包含研究实验控制器的MockMvc环境，并设置认证上下文
    @BeforeEach
    void setUp() {
        ResearchExperimentController controller =
                new ResearchExperimentController();
        // 通过反射注入service（因为没有setter，使用@Autowired field injection）
        try {
            java.lang.reflect.Field field = ResearchExperimentController.class
                    .getDeclaredField("experimentService");
            field.setAccessible(true);
            field.set(controller, experimentService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(jsr303Validator())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 设置认证用户上下文
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testuser", null, Collections.emptyList()));
    }

    // ==================== 幂等测试 ====================

    // 验证相同owner+runKey的重复上传返回已有run
    @Test
    void 相同runKey幂等返回() throws Exception {
        ExperimentRunResponse existing = createMockResponse(1L, "mpc-run-001", "MPC");
        when(experimentService.uploadExperiment(eq("testuser"), any(ExperimentRunRequest.class)))
                .thenReturn(existing);

        mockMvc.perform(post("/api/research/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mpcExperimentJson("mpc-run-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.runKey").value("mpc-run-001"));
    }

    // ==================== 枚举校验测试 ====================

    // 验证非法实验类型被拒绝并返回 HTTP 400
    @Test
    void 非法实验类型应被拒绝() throws Exception {
        mockMvc.perform(post("/api/research/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidTypeJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // 验证非法状态被拒绝并返回 HTTP 400
    @Test
    void 非法状态应被拒绝() throws Exception {
        mockMvc.perform(post("/api/research/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidStatusJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== Owner隔离测试 ====================

    // 验证列表查询返回当前用户的数据
    @Test
    void 列表查询应按owner隔离() throws Exception {
        List<ExperimentRunResponse> list = Arrays.asList(
                createMockResponse(1L, "run-001", "MPC"),
                createMockResponse(2L, "run-002", "RL"));
        when(experimentService.listExperiments(eq("testuser"), isNull(), eq(50)))
                .thenReturn(list);

        mockMvc.perform(get("/api/research/experiments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ==================== 详情越权测试 ====================

    // 验证非owner访问详情被拒绝
    @Test
    void 非owner访问详情应被拒绝() throws Exception {
        when(experimentService.getExperimentDetail(eq("testuser"), eq(99L)))
                .thenThrow(new SecurityException("无权访问该实验记录"));

        mockMvc.perform(get("/api/research/experiments/99"))
                .andExpect(status().is4xxClientError());
    }

    // 验证owner正常访问详情
    @Test
    void owner正常访问详情() throws Exception {
        ExperimentRunResponse detail = createMockResponse(1L, "run-001", "MPC");
        List<ExperimentMetricEntity> metrics = new ArrayList<>();
        ExperimentMetricEntity m = new ExperimentMetricEntity();
        m.setId(1L); m.setMetricName("水位MAE");
        m.setMetricValue(6.4902); m.setUnit("mm");
        metrics.add(m);
        detail.setMetrics(metrics);

        when(experimentService.getExperimentDetail(eq("testuser"), eq(1L)))
                .thenReturn(detail);

        mockMvc.perform(get("/api/research/experiments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.metrics.length()").value(1));
    }

    // ==================== 总览不伪造 ====================

    // 验证总览接口正常返回（不伪造不存在的实验）
    @Test
    void 总览返回实际数据不伪造() throws Exception {
        ResearchOverviewResponse overview = new ResearchOverviewResponse();
        overview.putRun("MPC", 1L, "mpc-run", "MIXED", "MPC实验摘要");

        when(experimentService.getOverview(eq("testuser"))).thenReturn(overview);

        mockMvc.perform(get("/api/research/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.latestMpc.runId").value(1));
    }

    // ==================== 工具方法 ====================

    // 创建模拟的ExperimentRunResponse
    private ExperimentRunResponse createMockResponse(Long id, String runKey, String type) {
        ExperimentRunResponse resp = new ExperimentRunResponse();
        resp.setId(id);
        resp.setRunKey(runKey);
        resp.setOwnerUsername("testuser");
        resp.setExperimentType(type);
        resp.setTitle("测试实验");
        resp.setSourceType("SIMULATION");
        resp.setStatus("PASSED");
        resp.setResultSummary("测试结果");
        resp.setExecutedAt("2026-08-12T10:00:00");
        resp.setCreatedAt("2026-08-12T10:00:00");
        resp.setMetrics(new ArrayList<>());
        return resp;
    }

    // 返回合法MPC实验JSON
    private String mpcExperimentJson(String runKey) {
        return "{"
                + "\"runKey\":\"" + runKey + "\","
                + "\"experimentType\":\"MPC\","
                + "\"title\":\"MPC基线确认性实验\","
                + "\"sourceType\":\"SIMULATION\","
                + "\"status\":\"MIXED\","
                + "\"resultSummary\":\"MPC实验摘要\","
                + "\"executedAt\":\"2026-08-11T16:44:30\","
                + "\"metrics\":[{"
                + "\"metricName\":\"水位MAE\","
                + "\"methodName\":\"MPC\","
                + "\"metricValue\":6.4902,"
                + "\"unit\":\"mm\""
                + "}]"
                + "}";
    }

    // 返回非法类型JSON
    private String invalidTypeJson() {
        return mpcExperimentJson("run-001").replace("\"MPC\"", "\"INVALID\"");
    }

    // 返回非法状态JSON
    private String invalidStatusJson() {
        return mpcExperimentJson("run-002").replace("\"MIXED\"", "\"UNKNOWN\"");
    }

    // 创建JSR-303验证器，使MockMvc standalone模式支持@Valid注解
    private static org.springframework.validation.Validator jsr303Validator() {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.afterPropertiesSet();
        return factoryBean;
    }
}
