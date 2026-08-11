% computeMetrics - 计算逐回合性能指标
% 参数:
%   cfg            - 配置结构体
%   timeSeries     - 时序记录结构体 (含 trueWaterLevel, action, actualIrrigation,
%                    decisionTimeMs, mpcFallback 等字段)
%   controllerType - 控制器类型 ('rule' 或 'mpc')
% 返回:
%   metrics - 指标结构体, 包含:
%     waterLevelMAE, bandViolationRate, safetyViolationCount,
%     totalIrrigationMm, switchCount, meanDecisionTimeMs, mpcFallbackCount
% 说明:
%   安全越界按连续事件计数, 而非逐采样点计数。
%   bandViolationRate 为水位在 [bandLow, bandHigh] 之外的步数占比。
function metrics = computeMetrics(cfg, timeSeries, controllerType)
    h = timeSeries.trueWaterLevel(:);
    T = length(h);

    % ---- 水位MAE ----
    metrics.waterLevelMAE = mean(abs(h - cfg.targetWaterLevel));

    % ---- 性能区间违规率 ----
    inBand = (h >= cfg.bandLow) & (h <= cfg.bandHigh);
    metrics.bandViolationRate = 1 - mean(inBand);

    % ---- 安全越界计数 (连续事件) ----
    safetyViolations = (h < cfg.safetyLow) | (h > cfg.safetyHigh);
    d = diff([0; safetyViolations; 0]);
    violationStarts = find(d == 1);
    violationEnds = find(d == -1) - 1; %#ok<NASGU>
    metrics.safetyViolationCount = length(violationStarts);

    % ---- 总灌溉量 ----
    metrics.totalIrrigationMm = sum(timeSeries.actualIrrigation(:));

    % ---- 灌溉开关次数 ----
    irrigationOn = timeSeries.action(:) > 0;
    switches = diff([0; irrigationOn]) ~= 0;
    metrics.switchCount = sum(switches);

    % ---- 平均决策时间 ----
    metrics.meanDecisionTimeMs = mean(timeSeries.decisionTimeMs(:));

    % ---- MPC回退次数 ----
    if strcmpi(controllerType, 'mpc') && isfield(timeSeries, 'mpcFallback')
        metrics.mpcFallbackCount = sum(timeSeries.mpcFallback(:));
    else
        metrics.mpcFallbackCount = 0;
    end

    metrics.controllerType = controllerType;
end
