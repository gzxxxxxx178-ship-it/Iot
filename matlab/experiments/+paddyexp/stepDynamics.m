% stepDynamics - 执行一步稻田水量平衡动力学
% 参数:
%   cfg    - 配置结构体 (来自 paddyexp.config)
%   state  - 环境状态结构体 (会被原地更新)
%   action - 当前步灌溉指令 (mm/步)
%   seq    - 预生成外生序列结构体
%   t      - 当前时间步 (1-indexed)
% 返回:
%   state - 更新后的环境状态
%   obs   - 含噪声观测结构体
%   info  - 辅助信息结构体 (记录各项分量)
% 说明:
%   水量平衡: h[t] = h[t-1] + P[t] - ET[t] - I[t] + u_eff[t] - D[t]
%   u_eff[t] = action[t - delaySteps] * actuatorGain[t]  (纯延迟队列)
%   当 h > safetyHigh 时发生排水 D[t]; h 物理下界为 0。
%   所有单位均为 mm/步。
function [state, obs, info] = stepDynamics(cfg, state, action, seq, t)
    % ---- 记录当前步灌溉指令 ----
    state.actionHistory(t) = action;

    % ---- 计算延迟后的有效灌溉量 ----
    d = seq.delaySteps;  % 标量, 每回合恒定的纯延迟步数
    effectiveIdx = t - d;
    if effectiveIdx >= 1
        effectiveAction = state.actionHistory(effectiveIdx);
    else
        effectiveAction = 0;  % t-d 超出历史范围, 无有效灌溉
    end

    % 应用执行器增益
    actualIrrigation = effectiveAction * seq.actuatorGain(t);

    % ---- 水量平衡 ----
    h = state.waterLevel;
    P = seq.rainfall(t);
    ET = seq.ET(t);
    I_act = seq.actualInfiltration(t);

    hNext = h + P - ET - I_act + actualIrrigation;

    % ---- 排水 (仅当超过安全上限时) ----
    drainage = 0;
    if hNext > cfg.safetyHigh
        drainage = min(cfg.maxDrainage, hNext - cfg.safetyHigh);
        hNext = hNext - drainage;
    end

    % ---- 物理下界: 水深不能为负 ----
    if hNext < 0
        hNext = 0;
    end

    % ---- 更新状态 ----
    state.waterLevel = hNext;
    state.prevAction = action;

    % ---- 生成含噪声观测 ----
    obs = struct();
    obs.waterLevel = hNext + seq.sensorNoise(t);

    % ---- 辅助信息 ----
    info = struct();
    info.rainfall = P;
    info.ET = ET;
    info.actualInfiltration = I_act;
    info.actualIrrigation = actualIrrigation;
    info.effectiveAction = effectiveAction;
    info.drainage = drainage;
    info.trueWaterLevel = hNext;
end
