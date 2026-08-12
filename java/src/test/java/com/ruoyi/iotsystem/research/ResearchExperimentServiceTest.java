package com.ruoyi.iotsystem.research;

import com.ruoyi.iotsystem.research.dto.ExperimentMetricRequest;
import com.ruoyi.iotsystem.research.dto.ExperimentRunRequest;
import com.ruoyi.iotsystem.research.dto.ExperimentRunResponse;
import com.ruoyi.iotsystem.research.entity.ExperimentRunEntity;
import com.ruoyi.iotsystem.research.repository.ExperimentMetricRepository;
import com.ruoyi.iotsystem.research.repository.ExperimentRunRepository;
import com.ruoyi.iotsystem.research.service.ResearchExperimentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 研究实验服务单元测试。
 * 覆盖上传前置校验（null/NaN/Infinity/非法时间）、合法上传和幂等行为。
 */
@ExtendWith(MockitoExtension.class)
class ResearchExperimentServiceTest {

    @Mock
    private ExperimentRunRepository runRepository;

    @Mock
    private ExperimentMetricRepository metricRepository;

    @InjectMocks
    private ResearchExperimentService service;

    // ==================== 前置校验：NaN ====================

    /**
     * 验证指标值为NaN时两个repository的save均为never
     */
    @Test
    void NaN指标值应拒绝并不保存() {
        ExperimentRunRequest req = validRequest();
        req.getMetrics().get(0).setMetricValue(Double.NaN);

        assertThrows(IllegalArgumentException.class, () ->
                service.uploadExperiment("test_user", req));
        verify(runRepository, never()).save(any());
        verify(metricRepository, never()).save(any());
    }

    // ==================== 前置校验：正无穷 ====================

    /**
     * 验证指标值为正无穷时两个repository的save均为never
     */
    @Test
    void 正无穷指标值应拒绝并不保存() {
        ExperimentRunRequest req = validRequest();
        req.getMetrics().get(0).setMetricValue(Double.POSITIVE_INFINITY);

        assertThrows(IllegalArgumentException.class, () ->
                service.uploadExperiment("test_user", req));
        verify(runRepository, never()).save(any());
        verify(metricRepository, never()).save(any());
    }

    // ==================== 前置校验：负无穷 ====================

    /**
     * 验证指标值为负无穷时两个repository的save均为never
     */
    @Test
    void 负无穷指标值应拒绝并不保存() {
        ExperimentRunRequest req = validRequest();
        req.getMetrics().get(0).setMetricValue(Double.NEGATIVE_INFINITY);

        assertThrows(IllegalArgumentException.class, () ->
                service.uploadExperiment("test_user", req));
        verify(runRepository, never()).save(any());
        verify(metricRepository, never()).save(any());
    }

    // ==================== 前置校验：null指标值 ====================

    /**
     * 验证指标值为null时两个repository的save均为never
     */
    @Test
    void null指标值应拒绝并不保存() {
        ExperimentRunRequest req = validRequest();
        req.getMetrics().get(0).setMetricValue(null);

        assertThrows(IllegalArgumentException.class, () ->
                service.uploadExperiment("test_user", req));
        verify(runRepository, never()).save(any());
        verify(metricRepository, never()).save(any());
    }

    // ==================== 前置校验：非法时间 ====================

    /**
     * 验证executedAt为非法格式字符串时两个repository的save均为never
     */
    @Test
    void 非法时间格式应拒绝并不保存() {
        ExperimentRunRequest req = validRequest();
        req.setExecutedAt("not-a-date-time");

        assertThrows(IllegalArgumentException.class, () ->
                service.uploadExperiment("test_user", req));
        verify(runRepository, never()).save(any());
        verify(metricRepository, never()).save(any());
    }

    // ==================== 前置校验：空时间 ====================

    /**
     * 验证executedAt为空字符串时两个repository的save均为never
     */
    @Test
    void 空时间应拒绝并不保存() {
        ExperimentRunRequest req = validRequest();
        req.setExecutedAt("");

        assertThrows(IllegalArgumentException.class, () ->
                service.uploadExperiment("test_user", req));
        verify(runRepository, never()).save(any());
        verify(metricRepository, never()).save(any());
    }

    /**
     * 验证executedAt为null时两个repository的save均为never
     */
    @Test
    void null时间应拒绝并不保存() {
        ExperimentRunRequest req = validRequest();
        req.setExecutedAt(null);

        assertThrows(IllegalArgumentException.class, () ->
                service.uploadExperiment("test_user", req));
        verify(runRepository, never()).save(any());
        verify(metricRepository, never()).save(any());
    }

    // ==================== 前置校验：null owner ====================

    /**
     * 验证owner为null时两个repository的save均为never
     */
    @Test
    void nullOwner应拒绝并不保存() {
        ExperimentRunRequest req = validRequest();

        assertThrows(IllegalArgumentException.class, () ->
                service.uploadExperiment(null, req));
        verify(runRepository, never()).save(any());
        verify(metricRepository, never()).save(any());
    }

    // ==================== 前置校验：null request ====================

    /**
     * 验证request为null时两个repository的save均为never
     */
    @Test
    void nullRequest应拒绝并不保存() {
        assertThrows(IllegalArgumentException.class, () ->
                service.uploadExperiment("test_user", null));
        verify(runRepository, never()).save(any());
        verify(metricRepository, never()).save(any());
    }

    // ==================== 合法请求测试 ====================

    /**
     * 验证合法请求正常保存run和指标
     */
    @Test
    void 合法请求应正常保存() {
        ExperimentRunRequest req = validRequest();
        ExperimentRunEntity savedRun = new ExperimentRunEntity();
        savedRun.setId(1L);
        savedRun.setRunKey(req.getRunKey());
        savedRun.setOwnerUsername("test_user");
        savedRun.setExperimentType("MPC");
        savedRun.setTitle(req.getTitle());
        savedRun.setSourceType("SIMULATION");
        savedRun.setStatus("MIXED");
        savedRun.setExecutedAt(java.time.LocalDateTime.of(2026, 8, 11, 16, 44, 30));

        when(runRepository.findByOwnerUsernameAndRunKey(eq("test_user"), eq("mpc-test-001")))
                .thenReturn(Optional.empty());
        when(runRepository.save(any(ExperimentRunEntity.class))).thenReturn(savedRun);
        // metrics are empty list from buildResponse since no actual save

        ExperimentRunResponse resp = service.uploadExperiment("test_user", req);

        assertNotNull(resp);
        assertEquals("mpc-test-001", resp.getRunKey());

        // 验证run save被调用
        ArgumentCaptor<ExperimentRunEntity> runCaptor =
                ArgumentCaptor.forClass(ExperimentRunEntity.class);
        verify(runRepository).save(runCaptor.capture());
        assertEquals("test_user", runCaptor.getValue().getOwnerUsername());

        // 验证指标save被调用（两个指标）
        verify(metricRepository, times(2)).save(any());
    }

    // ==================== 幂等测试 ====================

    /**
     * 验证相同owner+runKey时幂等返回已有run，不重复写入指标
     */
    @Test
    void 幂等上传应不重复写指标() {
        ExperimentRunRequest req = validRequest();
        ExperimentRunEntity existing = new ExperimentRunEntity();
        existing.setId(5L);
        existing.setRunKey("mpc-test-001");
        existing.setOwnerUsername("test_user");
        existing.setExperimentType("MPC");
        existing.setTitle(req.getTitle());
        existing.setSourceType("SIMULATION");
        existing.setStatus("MIXED");
        existing.setExecutedAt(java.time.LocalDateTime.of(2026, 8, 11, 16, 44, 30));

        when(runRepository.findByOwnerUsernameAndRunKey(eq("test_user"), eq("mpc-test-001")))
                .thenReturn(Optional.of(existing));

        ExperimentRunResponse resp = service.uploadExperiment("test_user", req);

        assertNotNull(resp);
        assertEquals(5L, resp.getId());

        // 幂等：run和metric都不应被save（不重复写入）
        verify(runRepository, never()).save(any());
        verify(metricRepository, never()).save(any());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造合法的测试用ExperimentRunRequest
     */
    private ExperimentRunRequest validRequest() {
        ExperimentRunRequest req = new ExperimentRunRequest();
        req.setRunKey("mpc-test-001");
        req.setExperimentType("MPC");
        req.setTitle("MPC测试实验");
        req.setSourceType("SIMULATION");
        req.setStatus("MIXED");
        req.setResultSummary("测试摘要");
        req.setExecutedAt("2026-08-11T16:44:30");

        List<ExperimentMetricRequest> metrics = new ArrayList<>();

        ExperimentMetricRequest m1 = new ExperimentMetricRequest();
        m1.setMethodName("MPC");
        m1.setMetricName("水位MAE");
        m1.setMetricValue(6.4902);
        m1.setUnit("mm");
        metrics.add(m1);

        ExperimentMetricRequest m2 = new ExperimentMetricRequest();
        m2.setMethodName("规则");
        m2.setMetricName("水位MAE");
        m2.setMetricValue(9.1552);
        m2.setUnit("mm");
        metrics.add(m2);

        req.setMetrics(metrics);
        return req;
    }
}
