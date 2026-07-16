package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.AlarmRuleRequest;
import com.ruoyi.iotsystem.entity.AlarmRecordEntity;
import com.ruoyi.iotsystem.entity.AlarmRuleEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.AlarmRecordRepository;
import com.ruoyi.iotsystem.repository.AlarmRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AlarmService {

    private static final double EQUALITY_TOLERANCE = 0.05;

    private final AlarmRuleRepository alarmRuleRepository;
    private final AlarmRecordRepository alarmRecordRepository;

    // 注入报警规则与报警记录仓库
    public AlarmService(
            AlarmRuleRepository alarmRuleRepository,
            AlarmRecordRepository alarmRecordRepository) {
        this.alarmRuleRepository = alarmRuleRepository;
        this.alarmRecordRepository = alarmRecordRepository;
    }

    // 查询全部报警规则
    public List<AlarmRuleEntity> getRules() {
        return alarmRuleRepository.findAllByOrderByIdDesc();
    }

    // 按当前用户查询报警规则
    public List<AlarmRuleEntity> getRules(String ownerUsername) {
        return alarmRuleRepository.findByOwnerUsernameOrderByIdDesc(ownerUsername);
    }

    // 校验并创建报警规则
    public AlarmRuleEntity createRule(AlarmRuleRequest request) {
        return createRule(request, null);
    }

    // 校验并创建归属于当前用户的报警规则
    public AlarmRuleEntity createRule(AlarmRuleRequest request, String ownerUsername) {
        validateThreshold(request.getMetric(), request.getThreshold());
        validateOperator(request.getOperator());
        String deviceId = normalizeDeviceId(request.getDeviceId());
        Boolean enabled = request.getEnabled() == null ? Boolean.TRUE : request.getEnabled();
        Integer cooldownSeconds = normalizeCooldown(request.getCooldownSeconds());
        AlarmRuleEntity rule = new AlarmRuleEntity(
                request.getMetric(),
                request.getOperator(),
                request.getThreshold(),
                enabled,
                deviceId,
                cooldownSeconds, ownerUsername);
        return alarmRuleRepository.save(rule);
    }

    // 校验并更新指定报警规则
    public AlarmRuleEntity updateRule(Long id, AlarmRuleRequest request) {
        return updateRule(id, request, null);
    }

    // 校验并更新当前用户拥有的报警规则
    public AlarmRuleEntity updateRule(Long id, AlarmRuleRequest request, String ownerUsername) {
        validateThreshold(request.getMetric(), request.getThreshold());
        validateOperator(request.getOperator());
        AlarmRuleEntity rule = alarmRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("报警规则不存在"));
        assertOwner(rule.getOwnerUsername(), ownerUsername);
        rule.setMetric(request.getMetric());
        rule.setOperator(request.getOperator());
        rule.setThreshold(request.getThreshold());
        rule.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        rule.setDeviceId(normalizeDeviceId(request.getDeviceId()));
        rule.setCooldownSeconds(normalizeCooldown(request.getCooldownSeconds()));
        rule.setUpdatedAt(LocalDateTime.now());
        return alarmRuleRepository.save(rule);
    }

    // 删除指定报警规则
    public void deleteRule(Long id) {
        deleteRule(id, null);
    }

    // 删除当前用户拥有的报警规则
    public void deleteRule(Long id, String ownerUsername) {
        AlarmRuleEntity rule = alarmRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("报警规则不存在"));
        assertOwner(rule.getOwnerUsername(), ownerUsername);
        alarmRuleRepository.delete(rule);
    }

    // 查询最近报警记录或指定时间范围内的记录
    public List<AlarmRecordEntity> getRecords(LocalDateTime start, LocalDateTime end) {
        return getRecords(start, end, null);
    }

    // 按当前用户查询报警记录
    public List<AlarmRecordEntity> getRecords(LocalDateTime start, LocalDateTime end, String ownerUsername) {
        if (start == null && end == null) {
            return ownerUsername == null
                    ? alarmRecordRepository.findTop100ByOrderByCreatedAtDesc()
                    : alarmRecordRepository.findTop100ByOwnerUsernameOrderByCreatedAtDesc(ownerUsername);
        }
        if (start == null || end == null) {
            throw new RuntimeException("开始时间和结束时间必须同时提供");
        }
        if (start.isAfter(end)) {
            throw new RuntimeException("开始时间不能晚于结束时间");
        }
        return ownerUsername == null
                ? alarmRecordRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end)
                : alarmRecordRepository.findByOwnerUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(
                        ownerUsername, start, end);
    }

    // 使用最新传感器数据评估全部启用规则并保存报警记录
    @Transactional
    public void evaluate(EspEntity reading) {
        evaluate(reading, null);
    }

    // 使用当前用户的规则评估最新传感器数据
    @Transactional
    public void evaluate(EspEntity reading, String ownerUsername) {
        if (reading == null || reading.getDeviceId() == null) {
            return;
        }
        List<AlarmRuleEntity> rules = ownerUsername == null
                ? alarmRuleRepository.findByEnabledTrueOrderByIdAsc()
                : alarmRuleRepository.findByEnabledTrueAndOwnerUsernameOrderByIdAsc(ownerUsername);
        for (AlarmRuleEntity rule : rules) {
            evaluateRule(rule, reading, ownerUsername);
        }
    }

    // 评估单条规则并在满足条件且不处于冷却期时生成报警
    private void evaluateRule(AlarmRuleEntity rule, EspEntity reading, String ownerUsername) {
        if (!appliesToDevice(rule, reading.getDeviceId())) {
            return;
        }
        Double actualValue = extractMetricValue(rule.getMetric(), reading);
        if (actualValue == null || !Double.isFinite(actualValue)) {
            return;
        }
        if (!matches(rule.getOperator(), actualValue, rule.getThreshold())) {
            return;
        }
        String recordOwner = ownerUsername == null ? rule.getOwnerUsername() : ownerUsername;
        if (isInCooldown(rule, reading.getDeviceId(), LocalDateTime.now(), recordOwner)) {
            return;
        }
        alarmRecordRepository.save(new AlarmRecordEntity(
                rule.getId(),
                reading.getDeviceId(),
                rule.getMetric(),
                rule.getOperator(),
                rule.getThreshold(),
                actualValue,
                buildMessage(rule, reading.getDeviceId(), actualValue), recordOwner));
    }

    // 判断规则是否适用于当前设备
    private boolean appliesToDevice(AlarmRuleEntity rule, String deviceId) {
        return "*".equals(rule.getDeviceId()) || rule.getDeviceId().equals(deviceId);
    }

    // 从传感器读数中提取规则指定指标
    private Double extractMetricValue(String metric, EspEntity reading) {
        if ("temperature".equals(metric)) {
            return reading.getTemperature();
        }
        if ("humidity".equals(metric)) {
            return reading.getHumidity();
        }
        if ("water".equals(metric)) {
            return reading.getWater();
        }
        return null;
    }

    // 判断实测值是否满足规则比较条件
    private boolean matches(String operator, Double actualValue, Double threshold) {
        if ("gt".equals(operator)) {
            return actualValue > threshold;
        }
        if ("lt".equals(operator)) {
            return actualValue < threshold;
        }
        if ("eq".equals(operator)) {
            return Math.abs(actualValue - threshold) <= EQUALITY_TOLERANCE;
        }
        return false;
    }

    // 判断同一规则和设备是否仍处于重复报警冷却期
    private boolean isInCooldown(AlarmRuleEntity rule, String deviceId, LocalDateTime now, String ownerUsername) {
        if (rule.getCooldownSeconds() == null || rule.getCooldownSeconds() <= 0) {
            return false;
        }
        Optional<AlarmRecordEntity> latest = ownerUsername == null
                ? alarmRecordRepository.findFirstByRuleIdAndDeviceIdOrderByCreatedAtDesc(rule.getId(), deviceId)
                : alarmRecordRepository.findFirstByOwnerUsernameAndRuleIdAndDeviceIdOrderByCreatedAtDesc(
                        ownerUsername, rule.getId(), deviceId);
        return latest.isPresent()
                && latest.get().getCreatedAt() != null
                && latest.get().getCreatedAt().isAfter(now.minusSeconds(rule.getCooldownSeconds()));
    }

    // 生成包含设备、指标、实测值和阈值的报警描述
    private String buildMessage(AlarmRuleEntity rule, String deviceId, Double actualValue) {
        return String.format(
                "设备%s的%s实测值%.2f%s阈值%.2f",
                deviceId,
                metricLabel(rule.getMetric()),
                actualValue,
                operatorSymbol(rule.getOperator()),
                rule.getThreshold());
    }

    // 获取指标中文名称和单位
    private String metricLabel(String metric) {
        if ("temperature".equals(metric)) {
            return "温度(℃)";
        }
        if ("humidity".equals(metric)) {
            return "湿度(%)";
        }
        return "水位(ADC)";
    }

    // 获取比较运算符显示符号
    private String operatorSymbol(String operator) {
        if ("gt".equals(operator)) {
            return ">";
        }
        if ("lt".equals(operator)) {
            return "<";
        }
        return "≈";
    }

    // 规范化设备范围并将空值转换为全部设备
    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return "*";
        }
        return deviceId.trim();
    }

    // 规范化冷却时间并限制在接口允许范围内
    private Integer normalizeCooldown(Integer cooldownSeconds) {
        int normalized = cooldownSeconds == null ? 300 : cooldownSeconds;
        if (normalized < 0 || normalized > 86400) {
            throw new RuntimeException("报警冷却时间必须在0到86400秒之间");
        }
        return normalized;
    }

    // 根据指标物理范围校验报警阈值
    private void validateThreshold(String metric, Double threshold) {
        if (threshold == null || !Double.isFinite(threshold)) {
            throw new RuntimeException("报警阈值必须是有限数值");
        }
        if ("temperature".equals(metric) && (threshold < -50.0 || threshold > 100.0)) {
            throw new RuntimeException("温度阈值必须在-50到100℃之间");
        }
        if ("humidity".equals(metric) && (threshold < 0.0 || threshold > 100.0)) {
            throw new RuntimeException("湿度阈值必须在0到100%之间");
        }
        if ("water".equals(metric) && threshold < 0.0) {
            throw new RuntimeException("水位ADC阈值不能为负数");
        }
        if (!"temperature".equals(metric) && !"humidity".equals(metric) && !"water".equals(metric)) {
            throw new RuntimeException("不支持的监控指标");
        }
    }

    // 校验规则比较运算符
    private void validateOperator(String operator) {
        if (!"gt".equals(operator) && !"lt".equals(operator) && !"eq".equals(operator)) {
            throw new RuntimeException("不支持的比较运算符");
        }
    }

    // 校验资源归属，禁止跨用户修改或删除
    private void assertOwner(String resourceOwner, String currentOwner) {
        if (currentOwner != null && !currentOwner.equals(resourceOwner)) {
            throw new SecurityException("无权访问该报警资源");
        }
    }
}
