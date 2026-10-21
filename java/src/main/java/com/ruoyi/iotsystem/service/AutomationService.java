package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.dto.AutomationRuleRequest;
import com.ruoyi.iotsystem.dto.AutomationExecutionView;
import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import com.ruoyi.iotsystem.entity.AutomationExecutionEntity;
import com.ruoyi.iotsystem.entity.AutomationRuleEntity;
import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.exception.BusinessException;
import com.ruoyi.iotsystem.repository.AutomationExecutionRepository;
import com.ruoyi.iotsystem.repository.AutomationRuleRepository;
import com.ruoyi.iotsystem.repository.DeviceCommandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Collections;

@Service
public class AutomationService {

    private static final double EQUALITY_TOLERANCE = 0.05;
    private static final Pattern LEGACY_COMMAND = Pattern.compile(
            "，commandId=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    private final AutomationRuleRepository ruleRepository;
    private final AutomationExecutionRepository executionRepository;
    private final DeviceCommandService deviceCommandService;
    private final DeviceService deviceService;
    private final DeviceCommandRepository commandRepository;

    // 注入规则与执行记录仓库、设备命令服务和设备状态校验服务
    public AutomationService(
            AutomationRuleRepository ruleRepository,
            AutomationExecutionRepository executionRepository,
            DeviceCommandService deviceCommandService,
            DeviceService deviceService, DeviceCommandRepository commandRepository) {
        this.ruleRepository = ruleRepository;
        this.executionRepository = executionRepository;
        this.deviceCommandService = deviceCommandService;
        this.deviceService = deviceService;
        this.commandRepository = commandRepository;
    }

    // 查询全部自动化规则
    public List<AutomationRuleEntity> getRules() {
        return ruleRepository.findAllByOrderByIdDesc();
    }

    // 按当前用户查询自动化规则
    public List<AutomationRuleEntity> getRules(String ownerUsername) {
        return ruleRepository.findByOwnerUsernameOrderByIdDesc(ownerUsername);
    }

    // 校验并持久化新的自动化规则
    public AutomationRuleEntity createRule(AutomationRuleRequest request) {
        return createRule(request, null);
    }

    // 校验并创建归属于当前用户的自动化规则
    public AutomationRuleEntity createRule(AutomationRuleRequest request, String ownerUsername) {
        validateThreshold(request.getMetric(), request.getThreshold());
        validateTarget(request, ownerUsername, false);
        AutomationRuleEntity rule = new AutomationRuleEntity(
                request.getName().trim(),
                request.getDeviceId().trim(),
                request.getMetric(),
                request.getOperator(),
                request.getThreshold(),
                request.getAction(),
                request.getEnabled() == null ? Boolean.TRUE : request.getEnabled(),
                request.getDebounceCount() == null ? 2 : request.getDebounceCount(),
                request.getCooldownSeconds() == null ? 300 : request.getCooldownSeconds(), ownerUsername);
        return ruleRepository.save(rule);
    }

    // 更新规则配置并清空旧条件累计状态
    public AutomationRuleEntity updateRule(Long id, AutomationRuleRequest request) {
        return updateRule(id, request, null);
    }

    // 更新当前用户拥有的自动化规则
    public AutomationRuleEntity updateRule(Long id, AutomationRuleRequest request, String ownerUsername) {
        validateThreshold(request.getMetric(), request.getThreshold());
        AutomationRuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("自动化规则不存在"));
        assertOwner(rule.getOwnerUsername(), ownerUsername);
        validateTarget(request, ownerUsername, true);
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
        deleteRule(id, null);
    }

    // 删除当前用户拥有的自动化规则
    public void deleteRule(Long id, String ownerUsername) {
        AutomationRuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("自动化规则不存在"));
        assertOwner(rule.getOwnerUsername(), ownerUsername);
        ruleRepository.delete(rule);
    }

    // 查询最近一百条动作执行审计记录
    public List<AutomationExecutionView> getExecutions() {
        return resolveCommands(executionRepository.findTop100ByOrderByCreatedAtDesc());
    }

    // 按当前用户查询自动化执行记录
    public List<AutomationExecutionView> getExecutions(String ownerUsername) {
        return resolveCommands(executionRepository.findTop100ByOwnerUsernameOrderByCreatedAtDesc(ownerUsername));
    }

    // 批量关联命令，并同时核对设备、账号和动作以防串用其他审计结果。
    private List<AutomationExecutionView> resolveCommands(List<AutomationExecutionEntity> executions) {
        Set<String> ids = executions.stream().map(this::commandReference).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, DeviceCommandEntity> commands = ids.isEmpty() ? Collections.emptyMap()
                : commandRepository.findByCommandIdIn(ids).stream()
                        .collect(Collectors.toMap(DeviceCommandEntity::getCommandId, Function.identity()));
        return executions.stream().map(execution -> {
            String id = commandReference(execution);
            DeviceCommandEntity command = commands.get(id);
            if (command != null && !(Objects.equals(execution.getDeviceId(), command.getDeviceId())
                    && Objects.equals(execution.getOwnerUsername(), command.getOwnerUsername())
                    && Objects.equals(execution.getAction(), command.getCommand()))) {
                command = null;
            }
            return new AutomationExecutionView(execution, id, command);
        }).collect(Collectors.toList());
    }

    // 新记录使用独立字段；旧记录仅识别服务端历史格式的完整UUID后缀。
    private String commandReference(AutomationExecutionEntity execution) {
        if ("notify".equals(execution.getAction())) return null;
        if (execution.getCommandId() != null) return execution.getCommandId();
        Matcher matcher = LEGACY_COMMAND.matcher(execution.getMessage() == null ? "" : execution.getMessage());
        return matcher.find() ? matcher.group(1) : null;
    }

    // 串行评估最新读数，避免同一实例并发更新防抖计数
    @Transactional
    public synchronized void evaluate(EspEntity reading) {
        evaluate(reading, null);
    }

    // 使用当前用户的规则评估最新读数
    @Transactional
    public synchronized void evaluate(EspEntity reading, String ownerUsername) {
        if (reading == null || reading.getDeviceId() == null) {
            return;
        }
        List<AutomationRuleEntity> rules = ownerUsername == null
                ? ruleRepository.findByEnabledTrueOrderByIdAsc()
                : ruleRepository.findByEnabledTrueAndOwnerUsernameOrderByIdAsc(ownerUsername);
        for (AutomationRuleEntity rule : rules) {
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
            deviceService.assertAutomationTarget(deviceId, rule.getOwnerUsername(), true, true);
            DeviceCommandEntity command = deviceCommandService.issue(
                    deviceId, rule.getOwnerUsername(), rule.getAction());
            AutomationExecutionEntity execution = new AutomationExecutionEntity(rule.getId(), deviceId,
                    rule.getAction(), command.getStatus(), actualValue, command.getMessage(), rule.getOwnerUsername());
            execution.setCommandId(command.getCommandId());
            executionRepository.save(execution);
        } catch (Exception exception) {
            saveExecution(rule, deviceId, actualValue, "FAILED", safeErrorMessage(exception));
        }
    }

    // 保存自动化动作的审计快照
    private void saveExecution(AutomationRuleEntity rule, String deviceId, Double actualValue,
            String status, String message) {
        executionRepository.save(new AutomationExecutionEntity(
                rule.getId(), deviceId, rule.getAction(), status, actualValue, message,
                rule.getOwnerUsername()));
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
            throw new BusinessException("触发阈值必须是有限数值");
        }
        if ("temperature".equals(metric) && (threshold < -50.0 || threshold > 100.0)) {
            throw new BusinessException("温度阈值必须在-50到100℃之间");
        }
        if ("humidity".equals(metric) && (threshold < 0.0 || threshold > 100.0)) {
            throw new BusinessException("湿度阈值必须在0到100%之间");
        }
        if ("water".equals(metric) && threshold < 0.0) {
            throw new BusinessException("水位ADC阈值不能为负数");
        }
    }

    // 校验规则目标设备归属，并允许停用过期规则而不允许启用不可用设备规则
    private void validateTarget(AutomationRuleRequest request, String ownerUsername, boolean allowInactive) {
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        deviceService.assertAutomationTarget(request.getDeviceId().trim(), ownerUsername,
                enabled || !allowInactive, enabled);
    }

    // 截断异常信息避免执行记录字段溢出
    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage() == null ? "动作执行失败" : exception.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    // 校验资源归属，禁止跨用户修改或删除
    private void assertOwner(String resourceOwner, String currentOwner) {
        if (currentOwner != null && !currentOwner.equals(resourceOwner)) {
            throw new SecurityException("无权访问该自动化资源");
        }
    }
}
