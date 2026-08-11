% runMpcBaselineExperiment - MPC基线对比实验入口
% 参数:
%   mode       - 运行模式: 'smoke'(6场景×2种子=12对) | 'confirmatory'(6场景×30种子=180对)
%   outputRoot - 输出根目录 (可选, 默认 matlab/experiments/results/)
% 返回:
%   output - 含 outputDir 和 summary 的结构体
% 说明:
%   每个 scenario+seed 对预生成一份外生序列, 规则控制器和MPC控制器
%   在完全相同序列上运行, 记录完整时序和指标。
%   输出目录包含: manifest.json, episode_metrics.csv, paired_deltas.csv,
%   aggregate_metrics.csv, 代表性时序CSV, 对比图PNG。
%   所有结果标记为合成软件在环试验, 不声称节水率、增产率或田间有效性。
function output = runMpcBaselineExperiment(mode, outputRoot)
    % ---- 解析参数 ----
    if nargin < 1 || isempty(mode)
        mode = 'smoke';
    end
    if nargin < 2 || isempty(outputRoot)
        outputRoot = fullfile(fileparts(mfilename('fullpath')), 'results');
    end

    % ---- 确定种子数 ----
    switch lower(mode)
        case 'smoke'
            nSeeds = 2;
        case 'confirmatory'
            nSeeds = 30;
        otherwise
            error('runMpcBaselineExperiment:unknownMode', ...
                  '未知模式: ''%s''。支持 ''smoke'' 或 ''confirmatory''。', mode);
    end

    % ---- 创建时间戳输出目录 ----
    timestamp = datestr(now, 'yyyymmdd_HHMMSS');
    outputDir = fullfile(outputRoot, sprintf('mpc_baseline_%s_%s', mode, timestamp));
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end

    % ---- 加载配置并展开场景与种子 ----
    cfg = paddyexp.config();
    scenarios = cfg.scenarios;
    seeds = 1:nSeeds;

    nPairs = length(scenarios) * length(seeds);
    nRuns = nPairs * 2;  % 每对两个控制器

    fprintf('=== MPC基线对比实验 ===\n');
    fprintf('模式: %s\n', mode);
    fprintf('场景数: %d, 种子数: %d → 配对: %d, 总运行: %d\n', ...
            length(scenarios), nSeeds, nPairs, nRuns);
    fprintf('输出: %s\n\n', outputDir);

    % ---- MPC工具箱预热: 避免JIT/加载时间计入首个回合decisionTime ----
    fprintf('预热 quadprog / MPC (不进入结果)...\n');
    warmupSeq = paddyexp.generateEpisode(cfg, 'dry', 9999);
    simulateEpisode(cfg, warmupSeq, 'mpc');
    fprintf('预热完成\n\n');

    % ---- 存储容器 ----
    episodeResults = cell(nPairs, 1);  % 逐配对结果
    allMetricsRows = {};               % 逐控制器指标行

    % ---- 主循环: 遍历场景×种子 ----
    pairIdx = 0;
    for iScenario = 1:length(scenarios)
        scenario = scenarios{iScenario};
        for iSeed = 1:length(seeds)
            seed = seeds(iSeed);
            pairIdx = pairIdx + 1;

            % 生成外生序列 (同一 scenario+seed 对只生成一次, 两个控制器共享)
            seq = paddyexp.generateEpisode(cfg, scenario, seed);

            % --- 运行规则控制器 ---
            fprintf('[%3d/%3d] %-20s seed=%2d  规则...', pairIdx, nPairs, scenario, seed);
            tStart = tic;
            tsRule = simulateEpisode(cfg, seq, 'rule');
            metricsRule = paddyexp.computeMetrics(cfg, tsRule, 'rule');
            metricsRule.scenario = scenario;
            metricsRule.seed = seed;
            fprintf(' 完成 (%.1fs)\n', toc(tStart));

            % --- 运行MPC控制器 (相同外生序列) ---
            fprintf('          %-20s seed=%2d  MPC...', scenario, seed);
            tStart = tic;
            tsMpc = simulateEpisode(cfg, seq, 'mpc');
            metricsMpc = paddyexp.computeMetrics(cfg, tsMpc, 'mpc');
            metricsMpc.scenario = scenario;
            metricsMpc.seed = seed;
            fprintf(' 完成 (%.1fs)\n', toc(tStart));

            % --- 存储配对结果 ---
            episodeResults{pairIdx} = struct(...
                'scenario', scenario, 'seed', seed, ...
                'seq', seq, ...
                'timeSeriesRule', tsRule, 'timeSeriesMpc', tsMpc, ...
                'metricsRule', metricsRule, 'metricsMpc', metricsMpc);

            % --- 收集指标行 ---
            allMetricsRows{end + 1} = metricsToRow(metricsRule); %#ok<AGROW>
            allMetricsRows{end + 1} = metricsToRow(metricsMpc); %#ok<AGROW>
        end
    end

    % ---- 生成 episode_metrics.csv ----
    episodeMetricsTable = vertcat(allMetricsRows{:});
    writetable(episodeMetricsTable, fullfile(outputDir, 'episode_metrics.csv'));
    fprintf('\nepisode_metrics.csv: %d 行已写入\n', height(episodeMetricsTable));

    % ---- 计算配对差值 ----
    pairedDeltasTable = computePairedDeltas(episodeResults);
    writetable(pairedDeltasTable, fullfile(outputDir, 'paired_deltas.csv'));
    fprintf('paired_deltas.csv: %d 行已写入\n', height(pairedDeltasTable));

    % ---- 计算聚合指标 ----
    aggregateTable = computeAggregateMetrics(episodeResults);
    writetable(aggregateTable, fullfile(outputDir, 'aggregate_metrics.csv'));
    fprintf('aggregate_metrics.csv: %d 行已写入\n', height(aggregateTable));

    % ---- 保存代表性时序 (第一个配对) ----
    repTsTable = exportRepresentativeTimeseries(episodeResults{1});
    writetable(repTsTable, fullfile(outputDir, 'representative_timeseries.csv'));
    fprintf('representative_timeseries.csv: %d 行已写入\n', height(repTsTable));

    % ---- 生成对比图 ----
    generateComparisonPlots(episodeResults, outputDir, cfg);
    fprintf('对比图PNG已生成\n');

    % ---- 生成 manifest.json ----
    generateManifest(outputDir, mode, cfg, nPairs, nSeeds, timestamp);
    fprintf('manifest.json 已生成\n');

    % ---- 汇总 ----
    output.outputDir = outputDir;
    output.summary = sprintf([ ...
        'MPC基线实验完成\n', ...
        '  模式: %s | 配对: %d | 总运行: %d\n', ...
        '  输出: %s\n', ...
        '  文件: episode_metrics.csv, paired_deltas.csv, aggregate_metrics.csv,\n', ...
        '        representative_timeseries.csv, timeseries_comparison.png,\n', ...
        '        metrics_comparison.png, manifest.json'], ...
        mode, nPairs, nRuns, outputDir);

    fprintf('\n%s\n', output.summary);
end

% ========================================================================
% 局部辅助函数
% ========================================================================

% simulateEpisode - 模拟一个回合, 返回完整时序结构体
% 参数:
%   cfg          - 配置结构体
%   seq          - 外生序列
%   controllerId - 'rule' 或 'mpc'
% 返回:
%   ts - 时序结构体, 字段均为 cfg.T×1
function ts = simulateEpisode(cfg, seq, controllerId)
    T = cfg.T;

    % 初始化环境和控制器
    [envState, obs] = paddyexp.reset(cfg, seq);
    ctrlState = struct();

    % 预分配时序数组
    ts = struct();
    ts.trueWaterLevel = zeros(T, 1);
    ts.observedWaterLevel = zeros(T, 1);
    ts.action = zeros(T, 1);
    ts.actualIrrigation = zeros(T, 1);
    ts.rainfall = zeros(T, 1);
    ts.ET = zeros(T, 1);
    ts.actualInfiltration = zeros(T, 1);
    ts.drainage = zeros(T, 1);
    ts.decisionTimeMs = zeros(T, 1);
    ts.mpcFallback = false(T, 1);

    for t = 1:T
        % 决策计时
        ticDecision = tic;

        % 根据控制器类型调用
        if strcmpi(controllerId, 'rule')
            [action, ctrlState] = paddyexp.ruleController(cfg, obs, ctrlState);
        else
            [action, ctrlState] = paddyexp.mpcController(cfg, obs, ctrlState, seq, t);
        end

        dtMs = toc(ticDecision) * 1000;

        % 执行一步动力学
        [envState, obs, info] = paddyexp.stepDynamics(cfg, envState, action, seq, t);

        % 记录时序
        ts.trueWaterLevel(t) = info.trueWaterLevel;
        ts.observedWaterLevel(t) = obs.waterLevel;
        ts.action(t) = action;
        ts.actualIrrigation(t) = info.actualIrrigation;
        ts.rainfall(t) = info.rainfall;
        ts.ET(t) = info.ET;
        ts.actualInfiltration(t) = info.actualInfiltration;
        ts.drainage(t) = info.drainage;
        ts.decisionTimeMs(t) = dtMs;

        if isfield(ctrlState, 'fallback') && ctrlState.fallback
            ts.mpcFallback(t) = true;
        end
    end
end

% metricsToRow - 将指标结构体转换为单行 table
function row = metricsToRow(m)
    row = table({m.controllerType}, {m.scenario}, m.seed, ...
                m.waterLevelMAE, m.bandViolationRate, m.safetyViolationCount, ...
                m.totalIrrigationMm, m.switchCount, m.meanDecisionTimeMs, ...
                m.mpcFallbackCount, ...
                'VariableNames', {'controller', 'scenario', 'seed', ...
                'waterLevelMAE', 'bandViolationRate', 'safetyViolationCount', ...
                'totalIrrigationMm', 'switchCount', 'meanDecisionTimeMs', ...
                'mpcFallbackCount'});
end

% computePairedDeltas - 计算每个 scenario+seed 对的 MPC-规则 指标差值
function deltas = computePairedDeltas(episodeResults)
    nPairs = length(episodeResults);
    rows = cell(nPairs, 1);

    for i = 1:nPairs
        r = episodeResults{i};
        mR = r.metricsRule;
        mM = r.metricsMpc;

        rows{i} = table({r.scenario}, r.seed, ...
            mM.waterLevelMAE - mR.waterLevelMAE, ...
            mM.bandViolationRate - mR.bandViolationRate, ...
            mM.safetyViolationCount - mR.safetyViolationCount, ...
            mM.totalIrrigationMm - mR.totalIrrigationMm, ...
            mM.switchCount - mR.switchCount, ...
            mM.meanDecisionTimeMs - mR.meanDecisionTimeMs, ...
            mM.mpcFallbackCount - mR.mpcFallbackCount, ...
            'VariableNames', {'scenario', 'seed', ...
            'delta_waterLevelMAE', 'delta_bandViolationRate', ...
            'delta_safetyViolationCount', 'delta_totalIrrigationMm', ...
            'delta_switchCount', 'delta_meanDecisionTimeMs', ...
            'delta_mpcFallbackCount'});
    end

    deltas = vertcat(rows{:});
end

% computeAggregateMetrics - 统一长表schema: controller_summary 和 paired_delta 行可 vertcat
% 固定列: rowType, controller, scenario, metric, n, mean, std, min, max,
%          positiveRatio, improvementRatio, lowerIsBetter
% controller_summary 行的 positiveRatio/improvementRatio 为 NaN;
% paired_delta 行 delta=MPC-Rule, positiveRatio=mean(delta>0),
% improvementRatio=mean(delta<0) (所有指标 lowerIsBetter=true).
function agg = computeAggregateMetrics(episodeResults)
    metricNames = {'waterLevelMAE', 'bandViolationRate', 'safetyViolationCount', ...
                   'totalIrrigationMm', 'switchCount', 'meanDecisionTimeMs', 'mpcFallbackCount'};
    lowerIsBetter = true(1, length(metricNames));  % 所有指标越低越好

    % 收集所有逐episode指标
    allM = [];
    for i = 1:length(episodeResults)
        r = episodeResults{i};
        allM = [allM; metricsToRow(r.metricsRule); metricsToRow(r.metricsMpc)]; %#ok<AGROW>
    end
    controllers = unique(allM.controller, 'stable');
    scenarios = unique(allM.scenario, 'stable');

    rows = {};

    % ---- controller_summary 行 ----
    for iCtrl = 1:length(controllers)
        ctrl = controllers{iCtrl};
        for iSc = 1:length(scenarios)
            sc = scenarios{iSc};
            mask = strcmp(allM.controller, ctrl) & strcmp(allM.scenario, sc);
            sub = allM(mask, :);
            n = height(sub);
            for j = 1:length(metricNames)
                mn = metricNames{j};
                vals = sub.(mn);
                rows{end + 1} = table( ...
                    {'controller_summary'}, {ctrl}, {sc}, {mn}, n, ...
                    mean(vals), std(vals), min(vals), max(vals), ...
                    NaN, NaN, lowerIsBetter(j), ...
                    'VariableNames', {'rowType', 'controller', 'scenario', 'metric', ...
                    'n', 'mean', 'std', 'min', 'max', ...
                    'positiveRatio', 'improvementRatio', 'lowerIsBetter'}); %#ok<AGROW>
            end
        end
    end

    % ---- paired_delta 行 ----
    deltas = computePairedDeltas(episodeResults);
    deltaMetricNames = setdiff(deltas.Properties.VariableNames, {'scenario', 'seed'}, 'stable');
    for iSc = 1:length(scenarios)
        sc = scenarios{iSc};
        mask = strcmp(deltas.scenario, sc);
        sub = deltas(mask, :);
        n = height(sub);
        for j = 1:length(deltaMetricNames)
            dmn = deltaMetricNames{j};
            vals = sub.(dmn);
            posRatio = mean(vals > 0);
            impRatio = mean(vals < 0);  % lowerIsBetter → delta<0 为改善
            rows{end + 1} = table( ...
                {'paired_delta'}, {'mpc_vs_rule'}, {sc}, {dmn}, n, ...
                mean(vals), std(vals), min(vals), max(vals), ...
                posRatio, impRatio, true, ...
                'VariableNames', {'rowType', 'controller', 'scenario', 'metric', ...
                'n', 'mean', 'std', 'min', 'max', ...
                'positiveRatio', 'improvementRatio', 'lowerIsBetter'}); %#ok<AGROW>
        end
    end

    agg = vertcat(rows{:});
end

% exportRepresentativeTimeseries - 导出代表性时序
function tbl = exportRepresentativeTimeseries(epResult)
    T = length(epResult.timeSeriesRule.trueWaterLevel);

    tbl = table((1:T)', ...
        epResult.timeSeriesRule.trueWaterLevel, ...
        epResult.timeSeriesMpc.trueWaterLevel, ...
        epResult.timeSeriesRule.action, ...
        epResult.timeSeriesMpc.action, ...
        epResult.timeSeriesRule.rainfall, ...
        epResult.timeSeriesRule.ET, ...
        epResult.timeSeriesRule.actualInfiltration, ...
        'VariableNames', {'timeStep', 'waterLevel_Rule', 'waterLevel_MPC', ...
        'action_Rule', 'action_MPC', 'rainfall', 'ET', 'actualInfiltration'});
end

% generateComparisonPlots - 生成水位/动作对比图和指标对比图
function generateComparisonPlots(episodeResults, outputDir, cfg) %#ok<INUSL>
    % ---- 图1: 代表性时序对比 (第一个配对) ----
    r = episodeResults{1};
    T = length(r.timeSeriesRule.trueWaterLevel);
    tVec = (1:T)';

    figure('Position', [100, 100, 1200, 800], 'Visible', 'off');

    % 子图1: 水位对比
    subplot(3, 1, 1);
    hold on;
    plot(tVec, r.timeSeriesRule.trueWaterLevel, 'b-', 'LineWidth', 1.5, 'DisplayName', 'Rule');
    plot(tVec, r.timeSeriesMpc.trueWaterLevel, 'r-', 'LineWidth', 1.5, 'DisplayName', 'MPC');
    yline(cfg.targetWaterLevel, 'k--', 'LineWidth', 1, 'DisplayName', 'Target');
    yline(cfg.bandLow, 'g:', 'LineWidth', 0.8, 'HandleVisibility', 'off');
    yline(cfg.bandHigh, 'g:', 'LineWidth', 0.8, 'DisplayName', 'Band');
    yline(cfg.safetyLow, 'm:', 'LineWidth', 0.8, 'HandleVisibility', 'off');
    yline(cfg.safetyHigh, 'm:', 'LineWidth', 0.8, 'DisplayName', 'Safety');
    hold off;
    xlabel('Step');
    ylabel('Water Level (mm)');
    title(sprintf('Water Level: %s seed=%d', r.scenario, r.seed));
    legend('Location', 'best');
    grid on;

    % 子图2: 灌溉动作对比
    subplot(3, 1, 2);
    hold on;
    stairs(tVec, r.timeSeriesRule.action, 'b-', 'LineWidth', 1.5, 'DisplayName', 'Rule');
    stairs(tVec, r.timeSeriesMpc.action, 'r-', 'LineWidth', 1.5, 'DisplayName', 'MPC');
    hold off;
    xlabel('Step');
    ylabel('Irrigation (mm/step)');
    title('Irrigation Actions');
    legend('Location', 'best');
    grid on;

    % 子图3: 降雨与蒸散
    subplot(3, 1, 3);
    hold on;
    bar(tVec, r.timeSeriesRule.rainfall, 'FaceColor', [0.3 0.6 1.0], 'DisplayName', 'Rainfall');
    plot(tVec, r.timeSeriesRule.ET, 'k-', 'LineWidth', 1.2, 'DisplayName', 'ET');
    plot(tVec, r.timeSeriesRule.actualInfiltration, 'm-', 'LineWidth', 1.2, 'DisplayName', 'Infiltration');
    hold off;
    xlabel('Step');
    ylabel('mm/step');
    title('Disturbances (Rainfall, ET, Infiltration)');
    legend('Location', 'best');
    grid on;

    saveas(gcf, fullfile(outputDir, 'timeseries_comparison.png'));
    close(gcf);

    % ---- 图2: 指标对比柱状图 (按场景) ----
    deltas = computePairedDeltas(episodeResults);
    scenarios = unique(deltas.scenario, 'stable');
    nSc = length(scenarios);

    deltaFields = {'delta_waterLevelMAE', 'delta_bandViolationRate', ...
                   'delta_safetyViolationCount', 'delta_totalIrrigationMm', ...
                   'delta_switchCount', 'delta_meanDecisionTimeMs', 'delta_mpcFallbackCount'};
    displayLabels = {'WaterLevelMAE', 'BandViolationRate', 'SafetyViolationCount', ...
                     'TotalIrrigation(mm)', 'SwitchCount', 'DecisionTime(ms)', 'MPCFallbackCount'};

    figure('Position', [100, 100, 1400, 900], 'Visible', 'off');
    nRows = 2;
    nCols = 4;
    for i = 1:length(deltaFields)
        subplot(nRows, nCols, i);
        means = zeros(nSc, 1);
        for j = 1:nSc
            mask = strcmp(deltas.scenario, scenarios{j});
            means(j) = mean(deltas{mask, deltaFields{i}});
        end
        bar(categorical(scenarios), means);
        title(displayLabels{i}, 'Interpreter', 'none');
        ylabel('\Delta (MPC - Rule)');
        grid on;
        xtickangle(45);
    end
    sgtitle('MPC vs Rule: Per-Scenario Mean Deltas (\Delta positive = MPC larger)', ...
            'FontSize', 14);

    saveas(gcf, fullfile(outputDir, 'metrics_comparison.png'));
    close(gcf);
end

% generateManifest - 生成实验清单JSON
function generateManifest(outputDir, mode, cfg, nPairs, nSeeds, timestamp)
    m = struct();
    m.mode = mode;
    m.timestamp = timestamp;
    m.matlabVersion = version();
    m.config.T = cfg.T;
    m.config.dtHours = cfg.dtHours;
    m.config.targetWaterLevel = cfg.targetWaterLevel;
    m.config.performanceBand = [cfg.bandLow, cfg.bandHigh];
    m.config.safetyBand = [cfg.safetyLow, cfg.safetyHigh];
    m.config.actionRange = [cfg.actionMin, cfg.actionMax];
    m.config.mpcHorizon = cfg.mpcHorizon;
    m.config.ruleHysteresisBand = [cfg.ruleIrrigateOn, cfg.ruleIrrigateOff];
    m.config.nominalInfiltration = cfg.nominalInfiltration;
    m.config.nominalActuatorGain = cfg.nominalActuatorGain;
    m.scenarios = cfg.scenarios;
    m.nSeedsPerScenario = nSeeds;
    m.nPairs = nPairs;
    m.nTotalRuns = nPairs * 2;
    m.aggregateSchema = ['Fixed columns: rowType, controller, scenario, metric, ', ...
        'n, mean, std, min, max, positiveRatio, improvementRatio, lowerIsBetter. ', ...
        'rowType=controller_summary|paired_delta. ', ...
        'For paired_delta: delta=MPC-Rule, positiveRatio=mean(delta>0), ', ...
        'improvementRatio=mean(delta<0) since all metrics are lower-is-better.'];
    m.forecastAssumption = ['MPC receives the exact synthetic rainfall and ET values only ', ...
        'inside its finite prediction window. This is a perfect short-horizon forecast ', ...
        'assumption and may overestimate performance relative to forecast-error conditions.'];
    m.disclaimer = ['All results are from synthetic software-in-the-loop experiments. ', ...
                    'No water-saving rate, yield increase, or field validity is claimed.'];

    jsonStr = jsonencode(m, 'PrettyPrint', true);
    fid = fopen(fullfile(outputDir, 'manifest.json'), 'w');
    if fid < 0
        warning('无法写入 manifest.json');
        return;
    end
    fprintf(fid, '%s', jsonStr);
    fclose(fid);
end
