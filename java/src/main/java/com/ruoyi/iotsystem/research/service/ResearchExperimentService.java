package com.ruoyi.iotsystem.research.service;

import com.ruoyi.iotsystem.exception.BusinessException;
import com.ruoyi.iotsystem.research.dto.*;
import com.ruoyi.iotsystem.research.entity.ExperimentMetricEntity;
import com.ruoyi.iotsystem.research.entity.ExperimentRunEntity;
import com.ruoyi.iotsystem.research.repository.ExperimentMetricRepository;
import com.ruoyi.iotsystem.research.repository.ExperimentRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 研究实验运行管理服务。
 * 负责实验run的创建（幂等）、查询、总览生成。
 * 所有数据按owner_username隔离。
 */
@Service
public class ResearchExperimentService {

    @Autowired
    private ExperimentRunRepository runRepository;

    @Autowired
    private ExperimentMetricRepository metricRepository;

    private static final DateTimeFormatter ISO_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ==================== 实验上传 ====================

    /**
     * 上传实验运行及其指标。
     * 相同owner+runKey时幂等返回已有run，不重复写入指标。
     * 在任何repository调用前校验owner/request/metricValue/executedAt合法性，
     * 非法值抛出IllegalArgumentException，不执行任何持久化操作。
     *
     * @param owner   当前用户
     * @param request 实验请求
     * @return 完整的实验运行响应（含指标）
     */
    @Transactional
    public ExperimentRunResponse uploadExperiment(String owner, ExperimentRunRequest request) {
        // ---- 前置校验（在任何repository调用前） ----

        // 校验owner和request非空
        if (owner == null) {
            throw new IllegalArgumentException("owner不能为null");
        }
        if (request == null) {
            throw new IllegalArgumentException("request不能为null");
        }

        // 校验executedAt非空且可解析为ISO本地时间（解析一次，后续复用）
        LocalDateTime parsedExecutedAt = parseDateTime(request.getExecutedAt());

        // 校验所有metricValue非null且非NaN/非Infinity
        if (request.getMetrics() != null) {
            for (ExperimentMetricRequest mReq : request.getMetrics()) {
                if (mReq.getMetricValue() == null) {
                    throw new IllegalArgumentException(
                            "指标值不能为null，指标：" + mReq.getMetricName());
                }
                if (Double.isNaN(mReq.getMetricValue())) {
                    throw new IllegalArgumentException(
                            "指标值不能为NaN，指标：" + mReq.getMetricName());
                }
                if (Double.isInfinite(mReq.getMetricValue())) {
                    throw new IllegalArgumentException(
                            "指标值不能为无穷大，指标：" + mReq.getMetricName());
                }
            }
        }

        // 幂等检查
        Optional<ExperimentRunEntity> existing =
                runRepository.findByOwnerUsernameAndRunKey(owner, request.getRunKey());
        if (existing.isPresent()) {
            return buildResponse(existing.get());
        }

        // 创建run（使用已解析的executedAt）
        ExperimentRunEntity run = new ExperimentRunEntity();
        run.setRunKey(request.getRunKey());
        run.setOwnerUsername(owner);
        run.setExperimentType(request.getExperimentType());
        run.setTitle(request.getTitle());
        run.setSourceType(request.getSourceType());
        run.setStatus(request.getStatus());
        run.setResultSummary(request.getResultSummary());
        run.setLimitations(request.getLimitations());
        run.setManifestPath(request.getManifestPath());
        run.setExecutedAt(parsedExecutedAt);
        run = runRepository.save(run);

        // 批量写入指标
        if (request.getMetrics() != null) {
            for (ExperimentMetricRequest mReq : request.getMetrics()) {
                ExperimentMetricEntity metric = new ExperimentMetricEntity();
                metric.setRunId(run.getId());
                metric.setMetricGroup(mReq.getMetricGroup());
                metric.setMethodName(mReq.getMethodName());
                metric.setMetricName(mReq.getMetricName());
                metric.setMetricValue(mReq.getMetricValue());
                metric.setUnit(mReq.getUnit());
                metric.setHigherIsBetter(mReq.getHigherIsBetter());
                metric.setNotes(mReq.getNotes());
                metricRepository.save(metric);
            }
        }

        return buildResponse(run);
    }

    // ==================== 实验列表 ====================

    /**
     * 获取当前用户最近的实验运行摘要列表。
     *
     * @param owner 当前用户
     * @param type  可选类型筛选（MPC/RL/RAG）
     * @param limit 返回条数限制（1-200）
     * @return 实验运行摘要列表（含指标）
     */
    public List<ExperimentRunResponse> listExperiments(String owner, String type, int limit) {
        List<ExperimentRunEntity> runs;
        if (type != null && !type.isEmpty()) {
            runs = runRepository.findByOwnerUsernameAndExperimentTypeOrderByExecutedAtDesc(owner, type);
        } else {
            runs = runRepository.findByOwnerUsernameOrderByExecutedAtDesc(owner);
        }

        List<ExperimentRunResponse> responses = new ArrayList<>();
        int count = 0;
        for (ExperimentRunEntity run : runs) {
            if (count >= limit) break;
            responses.add(buildResponse(run));
            count++;
        }
        return responses;
    }

    // ==================== 实验详情 ====================

    /**
     * 获取指定实验的详情（含指标）。
     * 仅owner可访问，非owner抛出SecurityException。
     *
     * @param owner 当前用户
     * @param id    实验ID
     * @return 完整实验响应
     */
    public ExperimentRunResponse getExperimentDetail(String owner, Long id) {
        ExperimentRunEntity run = runRepository.findById(id)
                .orElseThrow(() -> new BusinessException("实验运行不存在"));
        if (!owner.equals(run.getOwnerUsername())) {
            throw new SecurityException("无权访问该实验记录");
        }
        return buildResponse(run);
    }

    // ==================== 研究总览 ====================

    /**
     * 生成研究总览，返回最新MPC、RL、RAG实验的关键摘要。
     * 不伪造不存在的数据——未上传过的类型在响应中为空map。
     *
     * @param owner 当前用户
     * @return 研究总览响应
     */
    public ResearchOverviewResponse getOverview(String owner) {
        ResearchOverviewResponse overview = new ResearchOverviewResponse();

        for (String type : new String[]{"MPC", "RL", "RAG"}) {
            Optional<ExperimentRunEntity> latest =
                    runRepository.findFirstByOwnerUsernameAndExperimentTypeOrderByExecutedAtDesc(
                            owner, type);
            if (latest.isPresent()) {
                ExperimentRunEntity run = latest.get();
                overview.putRun(type, run.getId(), run.getRunKey(),
                        run.getStatus(), run.getResultSummary());
            }
        }

        return overview;
    }

    // ==================== 内部方法 ====================

    /**
     * 将实体转换为包含指标的响应对象
     */
    private ExperimentRunResponse buildResponse(ExperimentRunEntity run) {
        ExperimentRunResponse resp = new ExperimentRunResponse();
        resp.setId(run.getId());
        resp.setRunKey(run.getRunKey());
        resp.setOwnerUsername(run.getOwnerUsername());
        resp.setExperimentType(run.getExperimentType());
        resp.setTitle(run.getTitle());
        resp.setSourceType(run.getSourceType());
        resp.setStatus(run.getStatus());
        resp.setResultSummary(run.getResultSummary());
        resp.setLimitations(run.getLimitations());
        resp.setManifestPath(run.getManifestPath());
        if (run.getExecutedAt() != null) {
            resp.setExecutedAt(run.getExecutedAt().format(ISO_FORMAT));
        }
        if (run.getCreatedAt() != null) {
            resp.setCreatedAt(run.getCreatedAt().format(ISO_FORMAT));
        }

        // 加载关联指标
        List<ExperimentMetricEntity> metrics = metricRepository.findByRunId(run.getId());
        resp.setMetrics(metrics);

        return resp;
    }

    /**
     * 将ISO格式时间字符串解析为LocalDateTime。
     * 非法输入（null/空字符串/不可解析格式）抛出IllegalArgumentException，不回退为now。
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new IllegalArgumentException("executedAt不能为空");
        }
        try {
            return LocalDateTime.parse(dateStr, ISO_FORMAT);
        } catch (Exception e) {
            // 尝试常见变体，均失败则抛出
            try {
                return LocalDateTime.parse(dateStr,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e2) {
                try {
                    return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (Exception e3) {
                    throw new IllegalArgumentException(
                            "executedAt无法解析为ISO本地时间: " + dateStr, e3);
                }
            }
        }
    }
}
