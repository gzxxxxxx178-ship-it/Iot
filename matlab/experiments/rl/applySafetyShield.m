% applySafetyShield - 确定性安全屏蔽函数
% 参数:
%   rlCfg          - RL配置结构体 (含 safetyHighMargin, safetyLowMargin, actionChangeLimit 等)
%   proposedAction - 提议动作 (MPC基础 + SAC残差) (mm/步)
%   obsWaterLevel  - 当前含噪声观测水位 (mm)
%   prevAction     - 上一最终指令 (mm/步)
% 返回:
%   finalAction    - 屏蔽后最终灌溉量 (mm/步)
%   intervened     - 是否发生干预 (logical)
%   reason         - 干预原因字符串
% 说明:
%   屏蔽规则 (按优先级):
%     1. 观测水位 >= safetyHigh - safetyHighMargin → 强制灌溉量 = 0 (防溢流)
%     2. 观测水位 <= safetyLow  + safetyLowMargin  → 强制不低于 min(ruleIrrigationRate, actionMax) (防干涸)
%     3. 其余: 施加每步最大 actionChangeLimit mm/步的变化率限制
%     4. 最终钳制到 [actionMin, actionMax]
%   所有阈值基于合成仿真设定, 非农艺处方。
function [finalAction, intervened, reason] = applySafetyShield(rlCfg, proposedAction, ...
        obsWaterLevel, prevAction)
    intervened = false;
    reason = 'none';
    finalAction = proposedAction;

    highShield = rlCfg.safetyHigh - rlCfg.safetyHighMargin;  % 高水位触发线
    lowShield = rlCfg.safetyLow + rlCfg.safetyLowMargin;     % 低水位触发线

    % ---- 规则1: 高水位强制零灌溉 ----
    if obsWaterLevel >= highShield
        finalAction = 0;
        intervened = true;
        reason = 'high_water_forced_zero';

    % ---- 规则2: 低水位强制最小灌溉 ----
    elseif obsWaterLevel <= lowShield
        minSafe = min(rlCfg.ruleIrrigationRate, rlCfg.actionMax);
        finalAction = max(finalAction, minSafe);
        intervened = true;
        reason = 'low_water_forced_min';

    % ---- 规则3: 变化率限制 ----
    else
        maxChange = rlCfg.actionChangeLimit;
        upperBound = prevAction + maxChange;
        lowerBound = prevAction - maxChange;
        if finalAction > upperBound
            finalAction = upperBound;
            intervened = true;
            reason = 'rate_limit_upper';
        elseif finalAction < lowerBound
            finalAction = lowerBound;
            intervened = true;
            reason = 'rate_limit_lower';
        end
    end

    % ---- 规则4: 最终钳制到允许范围 ----
    if finalAction > rlCfg.actionMax
        finalAction = rlCfg.actionMax;
        if ~intervened
            intervened = true;
            reason = 'clamp_max';
        end
    elseif finalAction < rlCfg.actionMin
        finalAction = rlCfg.actionMin;
        if ~intervened
            intervened = true;
            reason = 'clamp_min';
        end
    end
end
