package com.ruoyi.iotsystem.simulation.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仿真域核心服务，负责遥测上传、规则引擎、报警管理和命令管理。
 * 所有操作均按当前登录用户隔离，不调用任何真实设备链服务。
 */
@Service
public class SimulationService {

    private static final double WATER_LEVEL_MIN = 0.0;
    private static final double WATER_LEVEL_MAX = 500.0;
    private static final double FLOW_RATE_MIN = 0.0;
    private static final double FLOW_RATE_MAX = 1000.0;
    private static final double EC_MIN = 0.0;
    private static final double EC_MAX = 20.0;
    private static final double SOIL_MOISTURE_MIN = 0.0;
    private static final double SOIL_MOISTURE_MAX = 100.0;
    private static final double RAINFALL_MIN = 0.0;
    private static final double RAINFALL_MAX = 500.0;

    private static final Set<String> VALID_METRICS = new HashSet<String>(Arrays.asList(
            "waterLevelMm", "flowRateLMin", "ecMsCm", "soilMoisturePct", "rainfallMm"));

    private final SimulationTelemetryRepository telemetryRepository;
    private final SimulationRuleRepository ruleRepository;
    private final SimulationAlarmRepository alarmRepository;
    private final SimulationCommandRepository commandRepository;

    // 按 ruleId:deviceId 跟踪连续命中计数，用于防抖判断
    private final ConcurrentHashMap<String, Integer> consecutiveMatches = new ConcurrentHashMap<String, Integer>();

    // 注入仿真域四个仓库
    public SimulationService(
            SimulationTelemetryRepository telemetryRepository,
            SimulationRuleRepository ruleRepository,
            SimulationAlarmRepository alarmRepository,
            SimulationCommandRepository commandRepository) {
        this.telemetryRepository = telemetryRepository;
        this.ruleRepository = ruleRepository;
        this.alarmRepository = alarmRepository;
        this.commandRepository = commandRepository;
    }

    // ==================== 遥测 ====================

    // 上传仿真遥测，幂等处理并按规则引擎触发报警
    @Transactional
    public SimulationTelemetryEntity uploadTelemetry(String ownerUsername, TelemetryRequest request) {
        validateTelemetryRequest(request);
        // 幂等检查：同一用户同一sampleId返回已存在记录
        Optional<SimulationTelemetryEntity> existing = telemetryRepository
                .findByOwnerUsernameAndSampleId(ownerUsername, request.getSampleId());
        if (existing.isPresent()) {
            return existing.get();
        }
        SimulationTelemetryEntity telemetry = new SimulationTelemetryEntity(
                request.getSampleId(),
                request.getDeviceId(),
                ownerUsername,
                LocalDateTime.ofInstant(request.getOccurredAt().toInstant(), ZoneOffset.UTC),
                request.getGrowthStage(),
                request.getScenarioCode(),
                request.getWaterLevelMm(),
                request.getFlowRateLMin(),
                request.getEcMsCm(),
                request.getSoilMoisturePct(),
                request.getRainfallMm(),
                request.getPumpOn(),
                request.getIrrigationValveOpen(),
                request.getFertilizerPumpOn());
        telemetryRepository.save(telemetry);
        // 先检查已有报警恢复条件，再运行规则引擎
        checkRecovery(ownerUsername, request.getDeviceId(), telemetry);
        evaluateRules(ownerUsername, telemetry);
        return telemetry;
    }

    // 查询指定设备最新一条遥测
    public SimulationTelemetryEntity getLatestTelemetry(String ownerUsername, String deviceId) {
        return telemetryRepository
                .findFirstByOwnerUsernameAndDeviceIdOrderByOccurredAtDesc(ownerUsername, deviceId)
                .orElse(null);
    }

    // 查询指定设备历史遥测，按发生时间倒序
    public List<SimulationTelemetryEntity> getTelemetryHistory(String ownerUsername, String deviceId, int limit) {
        List<SimulationTelemetryEntity> all = telemetryRepository
                .findByOwnerUsernameAndDeviceIdOrderByOccurredAtDesc(ownerUsername, deviceId);
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(0, limit);
    }

    // ==================== 规则CRUD ====================

    // 查询当前用户全部仿真规则
    public List<SimulationRuleEntity> getRules(String ownerUsername) {
        return ruleRepository.findByOwnerUsernameOrderByIdDesc(ownerUsername);
    }

    // 校验并创建仿真规则
    public SimulationRuleEntity createRule(String ownerUsername, RuleRequest request) {
        validateRuleRequest(request);
        SimulationRuleEntity rule = new SimulationRuleEntity(
                request.getName().trim(),
                request.getDeviceId().trim(),
                ownerUsername,
                request.getMetric(),
                request.getOperator(),
                request.getThreshold(),
                request.getRecoveryThreshold(),
                request.getDebounceCount() == null ? Integer.valueOf(1) : request.getDebounceCount(),
                request.getSeverity(),
                request.getAction(),
                request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return ruleRepository.save(rule);
    }

    // 校验并更新指定仿真规则，校验owner归属
    public SimulationRuleEntity updateRule(String ownerUsername, Long id, RuleRequest request) {
        validateRuleRequest(request);
        SimulationRuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("仿真规则不存在"));
        assertOwner(rule.getOwnerUsername(), ownerUsername);
        rule.setName(request.getName().trim());
        rule.setDeviceId(request.getDeviceId().trim());
        rule.setMetric(request.getMetric());
        rule.setOperator(request.getOperator());
        rule.setThreshold(request.getThreshold());
        rule.setRecoveryThreshold(request.getRecoveryThreshold());
        rule.setDebounceCount(request.getDebounceCount() == null ? Integer.valueOf(1) : request.getDebounceCount());
        rule.setSeverity(request.getSeverity());
        rule.setAction(request.getAction());
        rule.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    // 删除指定仿真规则，校验owner归属
    public void deleteRule(String ownerUsername, Long id) {
        SimulationRuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("仿真规则不存在"));
        assertOwner(rule.getOwnerUsername(), ownerUsername);
        ruleRepository.delete(rule);
    }

    // ==================== 报警 ====================

    // 按设备和状态查询报警
    public List<SimulationAlarmEntity> getAlarms(String ownerUsername, String deviceId, String status) {
        if (status != null && !status.isEmpty()) {
            return alarmRepository.findByOwnerUsernameAndDeviceIdAndStatusOrderByTriggeredAtDesc(
                    ownerUsername, deviceId, status);
        }
        return alarmRepository.findByOwnerUsernameAndDeviceIdOrderByTriggeredAtDesc(ownerUsername, deviceId);
    }

    // 确认指定报警，校验owner归属和当前状态为ACTIVE
    @Transactional
    public SimulationAlarmEntity acknowledgeAlarm(String ownerUsername, Long id) {
        SimulationAlarmEntity alarm = alarmRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("仿真报警不存在"));
        assertOwner(alarm.getOwnerUsername(), ownerUsername);
        if (!"ACTIVE".equals(alarm.getStatus())) {
            throw new RuntimeException("只能确认处于ACTIVE状态的报警");
        }
        alarm.setStatus("ACKNOWLEDGED");
        alarm.setAcknowledgedAt(LocalDateTime.now());
        return alarmRepository.save(alarm);
    }

    // ==================== 命令 ====================

    // 查询指定设备待处理的仿真命令
    public List<SimulationCommandEntity> getPendingCommands(String ownerUsername, String deviceId) {
        return commandRepository.findByOwnerUsernameAndDeviceIdAndStatusOrderByCreatedAtDesc(
                ownerUsername, deviceId, "PENDING");
    }

    // 提交命令执行反馈，状态只能从PENDING进入SUCCESS或FAILED终态
    @Transactional
    public SimulationCommandEntity submitFeedback(String ownerUsername, Long id, CommandFeedbackRequest request) {
        SimulationCommandEntity command = commandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("仿真命令不存在"));
        assertOwner(command.getOwnerUsername(), ownerUsername);
        if (!"PENDING".equals(command.getStatus())) {
            throw new RuntimeException("该命令已处于终态，不能重复反馈");
        }
        command.setStatus(request.getStatus());
        command.setMessage(request.getMessage());
        command.setFeedbackAt(LocalDateTime.now());
        return commandRepository.save(command);
    }

    // ==================== 规则引擎 ====================

    // 评估所有启用规则，对满足条件的规则触发报警
    private void evaluateRules(String ownerUsername, SimulationTelemetryEntity telemetry) {
        List<SimulationRuleEntity> rules = ruleRepository
                .findByEnabledTrueAndOwnerUsernameOrderByIdAsc(ownerUsername);
        for (SimulationRuleEntity rule : rules) {
            evaluateRule(rule, telemetry);
        }
    }

    // 评估单条规则：提取指标、比较阈值、跟踪防抖、触发报警
    private void evaluateRule(SimulationRuleEntity rule, SimulationTelemetryEntity telemetry) {
        if (!appliesToDevice(rule, telemetry.getDeviceId())) {
            return;
        }
        Double actualValue = extractMetricValue(rule.getMetric(), telemetry);
        if (actualValue == null || !Double.isFinite(actualValue)) {
            return;
        }
        String concurrencyKey = rule.getId() + ":" + telemetry.getDeviceId();
        if (!matches(rule.getOperator(), actualValue, rule.getThreshold())) {
            // 条件不满足，重置连续命中计数
            resetConsecutiveMatches(concurrencyKey);
            return;
        }
        // 条件满足，累加连续命中计数
        int count = incrementConsecutiveMatches(concurrencyKey);
        if (count < rule.getDebounceCount()) {
            return;
        }
        // 达到防抖阈值后重置计数
        resetConsecutiveMatches(concurrencyKey);
        // 同一规则+设备最多一个未恢复报警
        if (hasUnresolvedAlarm(rule.getOwnerUsername(), rule.getId(), telemetry.getDeviceId())) {
            return;
        }
        // 创建报警记录
        String message = buildAlarmMessage(rule, telemetry.getDeviceId(), actualValue);
        SimulationAlarmEntity alarm = new SimulationAlarmEntity(
                rule.getId(),
                telemetry.getDeviceId(),
                rule.getOwnerUsername(),
                rule.getMetric(),
                rule.getOperator(),
                rule.getThreshold(),
                rule.getRecoveryThreshold(),
                actualValue,
                rule.getSeverity(),
                rule.getAction(),
                message);
        alarmRepository.save(alarm);
        // 非NOTIFY动作创建仿真命令
        if (!"NOTIFY".equals(rule.getAction())) {
            SimulationCommandEntity command = new SimulationCommandEntity(
                    alarm.getId(),
                    telemetry.getDeviceId(),
                    rule.getOwnerUsername(),
                    rule.getAction());
            commandRepository.save(command);
        }
    }

    // 检查当前遥测是否使已有报警满足恢复条件
    private void checkRecovery(String ownerUsername, String deviceId, SimulationTelemetryEntity telemetry) {
        List<SimulationAlarmEntity> unresolved = alarmRepository
                .findByOwnerUsernameAndDeviceIdAndStatusNot(ownerUsername, deviceId, "RESOLVED");
        for (SimulationAlarmEntity alarm : unresolved) {
            Double currentValue = extractMetricValue(alarm.getMetric(), telemetry);
            if (currentValue == null || !Double.isFinite(currentValue)) {
                continue;
            }
            if (isRecovered(alarm, currentValue)) {
                alarm.setStatus("RESOLVED");
                alarm.setResolvedAt(LocalDateTime.now());
                alarm.setResolutionValue(currentValue);
                alarmRepository.save(alarm);
            }
        }
    }

    // 判断实测值是否满足报警恢复条件
    private boolean isRecovered(SimulationAlarmEntity alarm, double currentValue) {
        if ("gt".equals(alarm.getOperator())) {
            return currentValue <= alarm.getRecoveryThreshold();
        }
        if ("lt".equals(alarm.getOperator())) {
            return currentValue >= alarm.getRecoveryThreshold();
        }
        return false;
    }

    // 判断规则适用的设备范围是否覆盖当前设备
    private boolean appliesToDevice(SimulationRuleEntity rule, String deviceId) {
        return "*".equals(rule.getDeviceId()) || rule.getDeviceId().equals(deviceId);
    }

    // 判断实测值是否满足规则比较条件
    private boolean matches(String operator, Double actualValue, Double threshold) {
        if ("gt".equals(operator)) {
            return actualValue > threshold;
        }
        if ("lt".equals(operator)) {
            return actualValue < threshold;
        }
        return false;
    }

    // 判断同一规则+设备是否已有未恢复的报警
    private boolean hasUnresolvedAlarm(String ownerUsername, Long ruleId, String deviceId) {
        return alarmRepository
                .findFirstByOwnerUsernameAndRuleIdAndDeviceIdAndStatusNotOrderByTriggeredAtDesc(
                        ownerUsername, ruleId, deviceId, "RESOLVED")
                .isPresent();
    }

    // 从遥测数据中提取指定指标值
    private Double extractMetricValue(String metric, SimulationTelemetryEntity telemetry) {
        if ("waterLevelMm".equals(metric)) {
            return telemetry.getWaterLevelMm();
        }
        if ("flowRateLMin".equals(metric)) {
            return telemetry.getFlowRateLMin();
        }
        if ("ecMsCm".equals(metric)) {
            return telemetry.getEcMsCm();
        }
        if ("soilMoisturePct".equals(metric)) {
            return telemetry.getSoilMoisturePct();
        }
        if ("rainfallMm".equals(metric)) {
            return telemetry.getRainfallMm();
        }
        return null;
    }

    // 递增并返回防抖连续命中计数
    private int incrementConsecutiveMatches(String key) {
        Integer current = consecutiveMatches.get(key);
        if (current == null) {
            consecutiveMatches.put(key, Integer.valueOf(1));
            return 1;
        }
        int next = current.intValue() + 1;
        consecutiveMatches.put(key, Integer.valueOf(next));
        return next;
    }

    // 重置防抖连续命中计数
    private void resetConsecutiveMatches(String key) {
        consecutiveMatches.remove(key);
    }

    // 生成报警描述信息
    private String buildAlarmMessage(SimulationRuleEntity rule, String deviceId, Double actualValue) {
        String operatorSymbol = "gt".equals(rule.getOperator()) ? ">" : "<";
        return "设备" + deviceId + "的" + metricLabel(rule.getMetric())
                + "实测值" + String.format("%.2f", actualValue)
                + operatorSymbol + "阈值" + String.format("%.2f", rule.getThreshold());
    }

    // 获取指标中文标签
    private String metricLabel(String metric) {
        if ("waterLevelMm".equals(metric)) {
            return "水位(mm)";
        }
        if ("flowRateLMin".equals(metric)) {
            return "流量(L/min)";
        }
        if ("ecMsCm".equals(metric)) {
            return "EC(mS/cm)";
        }
        if ("soilMoisturePct".equals(metric)) {
            return "土壤含水率(%)";
        }
        if ("rainfallMm".equals(metric)) {
            return "降雨量(mm)";
        }
        return metric;
    }

    // ==================== 校验 ====================

    // 校验遥测请求的合法范围：至少一个指标、各指标不越界、非有限值拒绝
    private void validateTelemetryRequest(TelemetryRequest request) {
        boolean hasMetric = false;
        hasMetric |= validateMetricRange("水位", request.getWaterLevelMm(), WATER_LEVEL_MIN, WATER_LEVEL_MAX);
        hasMetric |= validateMetricRange("流量", request.getFlowRateLMin(), FLOW_RATE_MIN, FLOW_RATE_MAX);
        hasMetric |= validateMetricRange("EC", request.getEcMsCm(), EC_MIN, EC_MAX);
        hasMetric |= validateMetricRange("土壤含水率", request.getSoilMoisturePct(),
                SOIL_MOISTURE_MIN, SOIL_MOISTURE_MAX);
        hasMetric |= validateMetricRange("降雨量", request.getRainfallMm(), RAINFALL_MIN, RAINFALL_MAX);
        if (!hasMetric) {
            throw new RuntimeException("至少需要提供一个有效数值指标");
        }
    }

    // 校验单个指标值：可为空，不为空时必须在物理范围内且非NaN/Infinity
    private boolean validateMetricRange(String label, Double value, double min, double max) {
        if (value == null) {
            return false;
        }
        if (!Double.isFinite(value)) {
            throw new RuntimeException(label + "不能为NaN或Infinity");
        }
        if (value < min || value > max) {
            throw new RuntimeException(label + "超出合法范围[" + min + ", " + max + "]");
        }
        return true;
    }

    // 校验仿真规则请求的合法性和阈值的逻辑一致性
    private void validateRuleRequest(RuleRequest request) {
        if (!VALID_METRICS.contains(request.getMetric())) {
            throw new RuntimeException("无效的监控指标");
        }
        if (!"gt".equals(request.getOperator()) && !"lt".equals(request.getOperator())) {
            throw new RuntimeException("比较运算符必须是gt或lt");
        }
        if (request.getThreshold() == null || !Double.isFinite(request.getThreshold())) {
            throw new RuntimeException("触发阈值必须是有限数值");
        }
        if (request.getRecoveryThreshold() == null || !Double.isFinite(request.getRecoveryThreshold())) {
            throw new RuntimeException("恢复阈值必须是有限数值");
        }
        // gt: recoveryThreshold < threshold; lt: recoveryThreshold > threshold
        if ("gt".equals(request.getOperator()) && request.getRecoveryThreshold() >= request.getThreshold()) {
            throw new RuntimeException("gt规则的恢复阈值必须小于触发阈值");
        }
        if ("lt".equals(request.getOperator()) && request.getRecoveryThreshold() <= request.getThreshold()) {
            throw new RuntimeException("lt规则的恢复阈值必须大于触发阈值");
        }
        int debounce = request.getDebounceCount() == null ? 1 : request.getDebounceCount();
        if (debounce < 1 || debounce > 100) {
            throw new RuntimeException("防抖次数必须在1到100之间");
        }
        if (!"INFO".equals(request.getSeverity()) && !"WARN".equals(request.getSeverity())
                && !"CRITICAL".equals(request.getSeverity())) {
            throw new RuntimeException("严重级别无效");
        }
        if (!"NOTIFY".equals(request.getAction()) && !"STOP_IRRIGATION".equals(request.getAction())
                && !"STOP_FERTILIZER".equals(request.getAction()) && !"STOP_ALL".equals(request.getAction())) {
            throw new RuntimeException("触发动作无效");
        }
    }

    // 校验资源归属，禁止跨用户访问
    private void assertOwner(String resourceOwner, String currentOwner) {
        if (currentOwner != null && !currentOwner.equals(resourceOwner)) {
            throw new SecurityException("无权访问该仿真资源");
        }
    }
}
