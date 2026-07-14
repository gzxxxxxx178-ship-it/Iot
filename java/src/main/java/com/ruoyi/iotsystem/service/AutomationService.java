package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.AutomationRuleRequest;
import com.ruoyi.iotsystem.entity.AutomationExecutionEntity;
import com.ruoyi.iotsystem.entity.AutomationRuleEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.AutomationExecutionRepository;
import com.ruoyi.iotsystem.repository.AutomationRuleRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AutomationService {

    private static final double EQUALITY_TOLERANCE = 0.05;

    private final AutomationRuleRepository ruleRepository;
    private final AutomationExecutionRepository executionRepository;
    private final MqttMessageService mqttMessageService;

    // 注入规则、执行记录仓库和延迟加载的MQTT动作发布服务
    public AutomationService(
            AutomationRuleRepository ruleRepository,
            AutomationExecutionRepository executionRepository,
            @Lazy MqttMessageService mqttMessageService) {
        this.ruleRepository = ruleRepository;
        this.executionRepository = executionRepository;
        this.mqttMessageService = mqttMessageService;
    }

    // 查询全部自动化规则
    public List<AutomationRuleEntity> getRules() {
        return ruleRepository.findAllByOrderByIdDesc();
    }

    // 校验并持久化新的自动化规则
    public AutomationRuleEntity createRule(AutomationRuleRequest request) {
        validateThreshold(request.getMetric(), request.getThreshold());
        AutomationRuleEntity rule = new AutomationRuleEntity(
                request.getName().trim(),
                request.getDeviceId().trim(),
                request.getMetric(),
                request.getOperator(),
                request.getThreshold(),
                request.getAction(),
                request.getEnabled() == null ? Boolean.TRUE : request.getEnabled(),
                request.getDebounceCount() == null ? 2 : request.getDebounceCount(),
                request.getCooldownSeconds() == null ? 300 : request.getCooldownSeconds());
        return ruleRepository.save(rule);
    }

    // 更新规则配置并清空旧条件累计状态
    public AutomationRuleEntity updateRule(Long id, AutomationRuleRequest request) {
        validateThreshold(request.getMetric(), request.getThreshold());
        AutomationRuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("自动化规则不存在"));
        rule.setName(request.getName().trim());
        rule.setDeviceId(request.getDeviceId().trim());
        rule.setMetric(request.getMetric());
        rule.setOperator(request.getOperator());
        rule.setThreshold(request.getThreshold());
        rule.setAction(request.getAction());
        rule.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        rule.setDebounceCount(request.getDebounceCount() == null ? 2 : request.getDebounceCount());
        rule.setCooldownSeconds(request.getCooldownSeconds() == null ? 300 : request.getCooldownSeconds());
        rule.setConsecutiveMatches(0);
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    // 删除指定自动化规则
    public void deleteRule(Long id) {
        if (!ruleRepository.existsById(id)) {
            throw new RuntimeException("自动化规则不存在");
        }
        ruleRepository.deleteById(id);
    }

    // 查询最近一百条动作执行审计记录
    public List<AutomationExecutionEntity> getExecutions() {
        return executionRepository.findTop100ByOrderByCreatedAtDesc();
    }

    // 串行评估最新读数，避免同一实例并发更新防抖计数
    @Transactional
    public synchronized void evaluate(EspEntity reading) {
        if (reading == null || reading.getDeviceId() == null) {
            return;
        }
        for (AutomationRuleEntity rule : ruleRepository.findByEnabledTrueOrderByIdAsc()) {
            evaluateRule(rule, reading);
        }
    }

    // 评估单条规则并维护连续命中、防抖、冷却和执行记录
    private void evaluateRule(AutomationRuleEntity rule, EspEntity reading) {
        if (!rule.getDeviceId().equals(reading.getDeviceId())) {
            return;
        }
        Double actualValue = extractMetricValue(rule.getMetric(), reading);
        if (actualValue == null || !Double.isFinite(actualValue)) {
            resetMatches(rule);
            return;
        }
        if (!matches(rule.getOperator(), actualValue, rule.getThreshold())) {
            resetMatches(rule);
            return;
        }

        int matchCount = (rule.getConsecutiveMatches() == null ? 0 : rule.getConsecutiveMatches()) + 1;
        rule.setConsecutiveMatches(matchCount);
        rule.setUpdatedAt(LocalDateTime.now());
        if (matchCount < rule.getDebounceCount()) {
            ruleRepository.save(rule);
            return;
        }

        rule.setConsecutiveMatches(0);
        LocalDateTime now = LocalDateTime.now();
        if (isInCooldown(rule, now)) {
            ruleRepository.save(rule);
            return;
        }
        rule.setLastTriggeredAt(now);
        rule.setUpdatedAt(now);
        ruleRepository.save(rule);
        executeAction(rule, reading.getDeviceId(), actualValue);
    }

    // 执行动作并将成功或失败结果持久化用于追溯
    private void executeAction(AutomationRuleEntity rule, String deviceId, Double actualValue) {
        try {
            if ("notify".equals(rule.getAction())) {
                saveExecution(rule, deviceId, actualValue, "SUCCESS", "通知事件已记录");
                return;
            }
            mqttMessageService.publishControl(deviceId, rule.getAction());
            saveExecution(rule, deviceId, actualValue, "SUCCESS", "设备指令已发布");
        } catch (Exception exception) {
            saveExecution(rule, deviceId, actualValue, "FAILED", safeErrorMessage(exception));
        }
    }

    // 保存自动化动作的审计快照
    private void saveExecution(AutomationRuleEntity rule, String deviceId, Double actualValue,
            String status, String message) {
        executionRepository.save(new AutomationExecutionEntity(
                rule.getId(), deviceId, rule.getAction(), status, actualValue, message));
    }

    // 条件未命中时清空已累计的连续次数
    private void resetMatches(AutomationRuleEntity rule) {
        if (rule.getConsecutiveMatches() != null && rule.getConsecutiveMatches() > 0) {
            rule.setConsecutiveMatches(0);
            rule.setUpdatedAt(LocalDateTime.now());
            ruleRepository.save(rule);
        }
    }

    // 判断规则是否仍在动作冷却期内
    private boolean isInCooldown(AutomationRuleEntity rule, LocalDateTime now) {
        return rule.getCooldownSeconds() != null
                && rule.getCooldownSeconds() > 0
                && rule.getLastTriggeredAt() != null
                && rule.getLastTriggeredAt().isAfter(now.minusSeconds(rule.getCooldownSeconds()));
    }

    // 从传感器实体提取规则指定指标
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

    // 比较实测值与阈值并为等于判断保留传感器容差
    private boolean matches(String operator, Double actualValue, Double threshold) {
        if ("gt".equals(operator)) {
            return actualValue > threshold;
        }
        if ("lt".equals(operator)) {
            return actualValue < threshold;
        }
        return "eq".equals(operator) && Math.abs(actualValue - threshold) <= EQUALITY_TOLERANCE;
    }

    // 根据指标物理范围校验自动化触发阈值
    private void validateThreshold(String metric, Double threshold) {
        if (threshold == null || !Double.isFinite(threshold)) {
            throw new RuntimeException("触发阈值必须是有限数值");
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
    }

    // 截断异常信息避免执行记录字段溢出
    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage() == null ? "动作执行失败" : exception.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
