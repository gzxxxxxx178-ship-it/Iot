% evaluateResidualSac - 评估训练好的残差SAC agent
% 参数:
%   agentOrPath - rlSACAgent 对象或 trained_agent.mat 路径
%   split       - 数据分割: 'validation' (使用validationSeeds) | 'test' (使用testSeeds)
%   outputRoot  - 输出根目录 (可选)
% 返回:
%   output - 含 outputDir, episodeMetrics, pairedDeltas, aggregateMetrics 的结构体
% 说明:
%   评估时禁用探索策略, 每回合reset agent状态。
%   比较 rule, mpc, mpc_residual_sac_shielded, mpc_residual_sac_unshielded 四种控制器。
%   每个 scenario+seed 对只生成一份外生序列, 四种控制器共享。
%   输出 episode_metrics.csv, paired_vs_mpc.csv, aggregate_metrics.csv,
%   代表性时序CSV、对比图PNG 和 evaluation_manifest.json。
function output = evaluateResidualSac(agentOrPath, split, outputRoot, maxSeeds)
    % ---- 参数解析 ----
    if nargin < 2 || isempty(split)
        split = 'validation';
    end
    if nargin < 3 || isempty(outputRoot)
        outputRoot = fullfile(tempdir, 'paddy-residual-rl-eval');
    end
    if nargin < 4 || isempty(maxSeeds)
        maxSeeds = [];  % 使用全部seed
    end

    validSplits = {'validation', 'test'};
    if ~ismember(lower(split), validSplits)
        error('evaluateResidualSac:invalidSplit', ...
            'split 必须为 ''validation'' 或 ''test'', 收到: ''%s''', split);
    end

    % ---- 加载配置 ----
    rlCfg = residualRlConfig();

    % ---- 确定评估种子 ----
    switch lower(split)
        case 'validation'
            evalSeeds = rlCfg.validationSeeds;
        case 'test'
            evalSeeds = rlCfg.testSeeds;
    end

    % 可选: 限制评估seed数 (用于smoke快速验证)
    if ~isempty(maxSeeds) && maxSeeds < length(evalSeeds)
        evalSeeds = evalSeeds(1:maxSeeds);
    end

    % ---- 加载agent ----
    if ischar(agentOrPath) || isstring(agentOrPath)
        fprintf('从文件加载agent: %s\n', agentOrPath);
        loaded = load(agentOrPath, 'agent');
        agent = loaded.agent;
    else
        agent = agentOrPath;
    end

    % 禁用探索策略
    agent.UseExplorationPolicy = false;

    % ---- 创建时间戳输出目录 ----
    timestamp = datestr(now, 'yyyymmdd_HHMMSS');
    outputDir = fullfile(outputRoot, sprintf('evaluation_%s_%s', split, timestamp));
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end

    scenarios = rlCfg.scenarios;
    nScenarios = length(scenarios);
    nSeeds = length(evalSeeds);
    nPairs = nScenarios * nSeeds;
    nRuns = nPairs * 4;  % 四种控制器

    fprintf('=== 残差SAC评估 ===\n');
    fprintf('分割: %s | 场景数: %d | 种子数: %d → 配对: %d | 总运行: %d\n', ...
        split, nScenarios, nSeeds, nPairs, nRuns);
    fprintf('输出: %s\n\n', outputDir);

    % ---- 存储容器 ----
    evalResults = cell(nPairs, 1);
    allMetricsRows = {};

    % ---- 主循环 ----
    pairIdx = 0;
    for iSc = 1:nScenarios
        scenario = scenarios{iSc};
        for iSeed = 1:nSeeds
            seed = evalSeeds(iSeed);
            pairIdx = pairIdx + 1;

            % 生成一份外生序列 (四种控制器共享)
            seq = paddyexp.generateEpisode(rlCfg, scenario, seed);

            % --- 1. 规则控制器 ---
            fprintf('[%3d/%3d] %-20s seed=%d  规则...', pairIdx, nPairs, scenario, seed);
            [tsRule, mRule] = runRuleEpisode(rlCfg, seq);
            mRule.scenario = scenario; mRule.seed = seed;
            fprintf(' 完成\n');

            % --- 2. MPC控制器 ---
            fprintf('          %-20s seed=%d  MPC...', scenario, seed);
            [tsMpc, mMpc] = runMpcEpisode(rlCfg, seq);
            mMpc.scenario = scenario; mMpc.seed = seed;
            fprintf(' 完成\n');

            % --- 3. MPC + SAC 已屏蔽 ---
            fprintf('          %-20s seed=%d  SAC(shield)...', scenario, seed);
            [tsSacShield, mSacShield] = runSacEpisode(rlCfg, seq, agent, true);
            mSacShield.scenario = scenario; mSacShield.seed = seed;
            fprintf(' 完成\n');

            % --- 4. MPC + SAC 未屏蔽 ---
            fprintf('          %-20s seed=%d  SAC(unshield)...', scenario, seed);
            [tsSacUnshield, mSacUnshield] = runSacEpisode(rlCfg, seq, agent, false);
            mSacUnshield.scenario = scenario; mSacUnshield.seed = seed;
            fprintf(' 完成\n');

            % 存储配对结果
            evalResults{pairIdx} = struct(...
                'scenario', scenario, 'seed', seed, 'seq', seq, ...
                'tsRule', tsRule, 'tsMpc', tsMpc, ...
                'tsSacShield', tsSacShield, 'tsSacUnshield', tsSacUnshield, ...
                'mRule', mRule, 'mMpc', mMpc, ...
                'mSacShield', mSacShield, 'mSacUnshield', mSacUnshield);

            % 收集指标行
            allMetricsRows{end + 1} = sacMetricsToRow(mRule);
            allMetricsRows{end + 1} = sacMetricsToRow(mMpc);
            allMetricsRows{end + 1} = sacMetricsToRow(mSacShield);
            allMetricsRows{end + 1} = sacMetricsToRow(mSacUnshield);
        end
    end

    % ---- 写 episode_metrics.csv ----
    episodeMetrics = vertcat(allMetricsRows{:});
    writetable(episodeMetrics, fullfile(outputDir, 'episode_metrics.csv'));
    fprintf('\nepisode_metrics.csv: %d 行已写入\n', height(episodeMetrics));

    % ---- 计算 paired_vs_mpc.csv (SAC vs MPC 配对差值) ----
    pairedVsMpc = computePairedVsMpc(evalResults);
    writetable(pairedVsMpc, fullfile(outputDir, 'paired_vs_mpc.csv'));
    fprintf('paired_vs_mpc.csv: %d 行已写入\n', height(pairedVsMpc));

    % ---- 计算 aggregate_metrics.csv ----
    aggregateTable = computeSacAggregateMetrics(evalResults);
    writetable(aggregateTable, fullfile(outputDir, 'aggregate_metrics.csv'));
    fprintf('aggregate_metrics.csv: %d 行已写入\n', height(aggregateTable));

    % ---- 代表性时序 ----
    if ~isempty(evalResults)
        repTs = exportSacTimeseries(evalResults{1});
        writetable(repTs, fullfile(outputDir, 'representative_timeseries.csv'));
        fprintf('representative_timeseries.csv: %d 行已写入\n', height(repTs));
    end

    % ---- 对比图 ----
    generateSacPlots(evalResults, outputDir, rlCfg);
    fprintf('对比图PNG已生成\n');

    % ---- manifest ----
    generateEvalManifest(outputDir, split, rlCfg, nPairs, nSeeds, timestamp);
    fprintf('evaluation_manifest.json 已生成\n');

    % ---- 汇总 ----
    output.outputDir = outputDir;
    output.episodeMetrics = episodeMetrics;
    output.pairedDeltas = pairedVsMpc;
    output.aggregateMetrics = aggregateTable;
    output.summary = sprintf([ ...
        '残差SAC评估完成\n', ...
        '  分割: %s | 配对: %d | 总运行: %d\n', ...
        '  输出: %s'], split, nPairs, nRuns, outputDir);

    fprintf('\n%s\n', output.summary);
end

% ========================================================================
% 控制器Episode运行函数
% ========================================================================

% runRuleEpisode - 运行规则控制器一个episode, 返回时序和指标
function [ts, metrics] = runRuleEpisode(cfg, seq)
    T = cfg.T;
    [envState, obs] = paddyexp.reset(cfg, seq);
    ctrlState = struct();

    ts = initTimeSeries(T);
    for t = 1:T
        ticD = tic;
        [action, ctrlState] = paddyexp.ruleController(cfg, obs, ctrlState);
        dtMs = toc(ticD) * 1000;

        [envState, obs, info] = paddyexp.stepDynamics(cfg, envState, action, seq, t);
        ts = recordStep(ts, t, info, obs, action, dtMs, false, 0, false);
    end
    metrics = paddyexp.computeMetrics(cfg, ts, 'rule');
    metrics.shieldInterventionCount = 0;
    metrics.meanAbsoluteResidual = 0;
end

% runMpcEpisode - 运行MPC控制器一个episode, 返回时序和指标
function [ts, metrics] = runMpcEpisode(cfg, seq)
    T = cfg.T;
    [envState, obs] = paddyexp.reset(cfg, seq);
    mpcState = struct();

    ts = initTimeSeries(T);
    for t = 1:T
        ticD = tic;
        [action, mpcState] = paddyexp.mpcController(cfg, obs, mpcState, seq, t);
        dtMs = toc(ticD) * 1000;

        [envState, obs, info] = paddyexp.stepDynamics(cfg, envState, action, seq, t);

        isFallback = isfield(mpcState, 'fallback') && mpcState.fallback;
        ts = recordStep(ts, t, info, obs, action, dtMs, isFallback, 0, false);
    end
    metrics = paddyexp.computeMetrics(cfg, ts, 'mpc');
    metrics.shieldInterventionCount = 0;
    metrics.meanAbsoluteResidual = 0;
end

% runSacEpisode - 运行MPC+SAC残差控制器一个episode (可选屏蔽)
function [ts, metrics] = runSacEpisode(rlCfg, seq, agent, useShield)
    T = rlCfg.T;
    [envState, obs] = paddyexp.reset(rlCfg, seq);
    mpcState = struct();

    % 重置agent状态
    reset(agent);

    prevFinalAction = 0;
    prevResidual = 0;
    shieldCount = 0;
    absResidualSum = 0;

    ts = initTimeSeries(T);
    for t = 1:T
        % 1. 计算MPC基础动作
        ticD = tic;
        [mpcAction, mpcState] = paddyexp.mpcController(rlCfg, obs, mpcState, seq, t);

        % 2. 构建RL观测并获取残差
        obsVec = buildResidualObservation(obs.waterLevel, prevFinalAction, ...
            mpcAction, seq.rainfall(t), seq.ET(t), prevResidual, rlCfg);
        residual = getAction(agent, obsVec);
        if iscell(residual)
            residual = residual{1};
        end
        residual = double(residual);

        % 3. 钳制残差
        residual = max(rlCfg.residualRange(1), min(rlCfg.residualRange(2), residual));

        % 4. 提议动作 = MPC基础 + 残差
        proposedAction = mpcAction + residual;

        % 5. 安全屏蔽 (可选)
        shieldIntervened = false;
        if useShield
            [finalAction, shieldIntervened, ~] = applySafetyShield(...
                rlCfg, proposedAction, obs.waterLevel, prevFinalAction);
        else
            % 无屏蔽: 仅做法定动作边界clip, 不计入shieldInterventionCount
            finalAction = max(rlCfg.actionMin, min(rlCfg.actionMax, proposedAction));
            shieldIntervened = false;
        end

        % 6. 更新MPC prevAction 为最终实际指令
        mpcState.prevAction = finalAction;

        dtMs = toc(ticD) * 1000;

        % 7. 执行动力学
        [envState, obs, info] = paddyexp.stepDynamics(rlCfg, envState, finalAction, seq, t);

        % 8. 累计
        if shieldIntervened
            shieldCount = shieldCount + 1;
        end
        absResidualSum = absResidualSum + abs(residual);

        % 9. 记录
        ts = recordStep(ts, t, info, obs, finalAction, dtMs, ...
            isfield(mpcState, 'fallback') && mpcState.fallback, residual, shieldIntervened);

        prevFinalAction = finalAction;
        prevResidual = residual;
    end

    % 计算指标 (复用MPC阶段指标)
    metrics = paddyexp.computeMetrics(rlCfg, ts, ...
        getSacControllerType(useShield));
    % 冻结computeMetrics仅对controllerType=='mpc'计数回退,
    % SAC控制器需显式从时序覆盖
    metrics.mpcFallbackCount = sum(ts.mpcFallback);
    metrics.shieldInterventionCount = shieldCount;
    metrics.meanAbsoluteResidual = absResidualSum / T;
end

% ========================================================================
% 辅助函数
% ========================================================================

% initTimeSeries - 初始化时序结构体
function ts = initTimeSeries(T)
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
    ts.residual = zeros(T, 1);
    ts.shieldIntervened = false(T, 1);
end

% recordStep - 记录单步时序
function ts = recordStep(ts, t, info, obs, action, dtMs, isFallback, residual, shieldIntervened)
    ts.trueWaterLevel(t) = info.trueWaterLevel;
    ts.observedWaterLevel(t) = obs.waterLevel;
    ts.action(t) = action;
    ts.actualIrrigation(t) = info.actualIrrigation;
    ts.rainfall(t) = info.rainfall;
    ts.ET(t) = info.ET;
    ts.actualInfiltration(t) = info.actualInfiltration;
    ts.drainage(t) = info.drainage;
    ts.decisionTimeMs(t) = dtMs;
    ts.mpcFallback(t) = isFallback;
    ts.residual(t) = residual;
    ts.shieldIntervened(t) = shieldIntervened;
end

% getSacControllerType - 返回SAC控制器类型字符串
function ct = getSacControllerType(useShield)
    if useShield
        ct = 'mpc_residual_sac_shielded';
    else
        ct = 'mpc_residual_sac_unshielded';
    end
end

% sacMetricsToRow - 将含扩展字段的指标转为单行table
function row = sacMetricsToRow(m)
    row = table({m.controllerType}, {m.scenario}, m.seed, ...
        m.waterLevelMAE, m.bandViolationRate, m.safetyViolationCount, ...
        m.totalIrrigationMm, m.switchCount, m.meanDecisionTimeMs, ...
        m.mpcFallbackCount, m.shieldInterventionCount, m.meanAbsoluteResidual, ...
        'VariableNames', {'controller', 'scenario', 'seed', ...
        'waterLevelMAE', 'bandViolationRate', 'safetyViolationCount', ...
        'totalIrrigationMm', 'switchCount', 'meanDecisionTimeMs', ...
        'mpcFallbackCount', 'shieldInterventionCount', 'meanAbsoluteResidual'});
end

% computePairedVsMpc - 计算SAC控制器 vs MPC的配对差值
function deltas = computePairedVsMpc(evalResults)
    nPairs = length(evalResults);
    rows = cell(nPairs * 2, 1);  % shielded + unshielded for each pair
    rowIdx = 0;

    metricFields = {'waterLevelMAE', 'bandViolationRate', 'safetyViolationCount', ...
        'totalIrrigationMm', 'switchCount', 'meanDecisionTimeMs', 'mpcFallbackCount'};
    sacFields = {'shieldInterventionCount', 'meanAbsoluteResidual'};

    for i = 1:nPairs
        r = evalResults{i};
        % shielded vs MPC
        rowIdx = rowIdx + 1;
        rows{rowIdx} = buildDeltaRow(r.scenario, r.seed, 'mpc_residual_sac_shielded', ...
            r.mSacShield, r.mMpc, metricFields, sacFields);

        % unshielded vs MPC
        rowIdx = rowIdx + 1;
        rows{rowIdx} = buildDeltaRow(r.scenario, r.seed, 'mpc_residual_sac_unshielded', ...
            r.mSacUnshield, r.mMpc, metricFields, sacFields);
    end

    deltas = vertcat(rows{1:rowIdx});
end

% buildDeltaRow - 构建单行配对差值
function row = buildDeltaRow(scenario, seed, controllerType, mSac, mMpc, metricFields, sacFields)
    % 显式cell包装文本列, 防止R2025a将字符向量误解析为table参数名
    deltaVals = cell(1, length(metricFields) + length(sacFields) + 3);
    deltaVals{1} = {scenario};           % 文本标量用cell包装
    deltaVals{2} = seed;                 % 数值标量
    deltaVals{3} = {controllerType};     % 文本标量用cell包装
    colIdx = 4;
    for j = 1:length(metricFields)
        fn = metricFields{j};
        deltaVals{colIdx} = mSac.(fn) - mMpc.(fn);
        colIdx = colIdx + 1;
    end
    for j = 1:length(sacFields)
        fn = sacFields{j};
        deltaVals{colIdx} = mSac.(fn);
        colIdx = colIdx + 1;
    end
    varNames = [{'scenario', 'seed', 'controller'}, ...
        strcat('delta_', metricFields), sacFields];
    row = table(deltaVals{:}, 'VariableNames', varNames);
end

% computeSacAggregateMetrics - 聚合指标 (长表schema)
function agg = computeSacAggregateMetrics(evalResults)
    metricNames = {'waterLevelMAE', 'bandViolationRate', 'safetyViolationCount', ...
        'totalIrrigationMm', 'switchCount', 'meanDecisionTimeMs', 'mpcFallbackCount', ...
        'shieldInterventionCount', 'meanAbsoluteResidual'};
    lowerIsBetter = true(1, length(metricNames));

    % 收集所有逐episode指标
    allM = [];
    for i = 1:length(evalResults)
        r = evalResults{i};
        allM = [allM; sacMetricsToRow(r.mRule); sacMetricsToRow(r.mMpc); ...
            sacMetricsToRow(r.mSacShield); sacMetricsToRow(r.mSacUnshield)]; %#ok<AGROW>
    end
    controllers = unique(allM.controller, 'stable');
    scenarios = unique(allM.scenario, 'stable');

    rows = {};

    % controller_summary 行
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

    % paired_delta 行 (SAC shielded + unshielded vs MPC)
    pairedVsMpc = computePairedVsMpc(evalResults);
    sacControllers = unique(pairedVsMpc.controller, 'stable');
    deltaMetricNames = pairedVsMpc.Properties.VariableNames;
    deltaMetricNames = setdiff(deltaMetricNames, {'scenario', 'seed', 'controller'}, 'stable');

    for iCtrl = 1:length(sacControllers)
        ctrl = sacControllers{iCtrl};
        for iSc = 1:length(scenarios)
            sc = scenarios{iSc};
            mask = strcmp(pairedVsMpc.scenario, sc) & strcmp(pairedVsMpc.controller, ctrl);
            sub = pairedVsMpc(mask, :);
            n = height(sub);
            for j = 1:length(deltaMetricNames)
                dmn = deltaMetricNames{j};
                vals = sub.(dmn);
                posRatio = mean(vals > 0);
                impRatio = mean(vals < 0);
                rows{end + 1} = table( ...
                    {'paired_delta'}, {ctrl}, {sc}, {dmn}, n, ...
                    mean(vals), std(vals), min(vals), max(vals), ...
                    posRatio, impRatio, true, ...
                    'VariableNames', {'rowType', 'controller', 'scenario', 'metric', ...
                    'n', 'mean', 'std', 'min', 'max', ...
                    'positiveRatio', 'improvementRatio', 'lowerIsBetter'}); %#ok<AGROW>
            end
        end
    end

    agg = vertcat(rows{:});
end

% exportSacTimeseries - 导出四控制器代表时序
function tbl = exportSacTimeseries(epResult)
    T = length(epResult.tsRule.trueWaterLevel);

    tbl = table((1:T)', ...
        epResult.tsRule.trueWaterLevel, ...
        epResult.tsMpc.trueWaterLevel, ...
        epResult.tsSacShield.trueWaterLevel, ...
        epResult.tsSacUnshield.trueWaterLevel, ...
        epResult.tsRule.action, ...
        epResult.tsMpc.action, ...
        epResult.tsSacShield.action, ...
        epResult.tsSacUnshield.action, ...
        epResult.tsRule.rainfall, ...
        epResult.tsRule.ET, ...
        epResult.tsRule.actualInfiltration, ...
        epResult.tsSacShield.residual, ...
        double(epResult.tsSacShield.shieldIntervened), ...
        'VariableNames', {'timeStep', 'waterLevel_Rule', 'waterLevel_MPC', ...
        'waterLevel_SAC_Shield', 'waterLevel_SAC_Unshield', ...
        'action_Rule', 'action_MPC', 'action_SAC_Shield', 'action_SAC_Unshield', ...
        'rainfall', 'ET', 'actualInfiltration', ...
        'residual_SAC', 'shieldIntervention'});
end

% generateSacPlots - 生成评估对比图
function generateSacPlots(evalResults, outputDir, cfg)
    if isempty(evalResults)
        return;
    end
    r = evalResults{1};
    T = length(r.tsRule.trueWaterLevel);
    tVec = (1:T)';

    % ---- 图1: 四控制器水位/动作对比 ----
    figure('Position', [100, 100, 1400, 1000], 'Visible', 'off');

    subplot(3, 1, 1);
    hold on;
    plot(tVec, r.tsRule.trueWaterLevel, 'b-', 'LineWidth', 1, 'DisplayName', 'Rule');
    plot(tVec, r.tsMpc.trueWaterLevel, 'r-', 'LineWidth', 1, 'DisplayName', 'MPC');
    plot(tVec, r.tsSacShield.trueWaterLevel, 'g-', 'LineWidth', 1.5, 'DisplayName', 'SAC+Shield');
    plot(tVec, r.tsSacUnshield.trueWaterLevel, 'm--', 'LineWidth', 1, 'DisplayName', 'SAC(no shield)');
    yline(cfg.targetWaterLevel, 'k--', 'LineWidth', 1, 'DisplayName', 'Target');
    yline(cfg.bandLow, 'c:', 'LineWidth', 0.8, 'HandleVisibility', 'off');
    yline(cfg.bandHigh, 'c:', 'LineWidth', 0.8, 'DisplayName', 'Band');
    hold off;
    xlabel('Step'); ylabel('Water Level (mm)');
    title(sprintf('Water Level: %s seed=%d', r.scenario, r.seed));
    legend('Location', 'best'); grid on;

    subplot(3, 1, 2);
    hold on;
    stairs(tVec, r.tsRule.action, 'b-', 'LineWidth', 1, 'DisplayName', 'Rule');
    stairs(tVec, r.tsMpc.action, 'r-', 'LineWidth', 1, 'DisplayName', 'MPC');
    stairs(tVec, r.tsSacShield.action, 'g-', 'LineWidth', 1.5, 'DisplayName', 'SAC+Shield');
    stairs(tVec, r.tsSacUnshield.action, 'm--', 'LineWidth', 1, 'DisplayName', 'SAC(no shield)');
    hold off;
    xlabel('Step'); ylabel('Irrigation (mm/step)');
    title('Irrigation Actions'); legend('Location', 'best'); grid on;

    subplot(3, 1, 3);
    hold on;
    bar(tVec, r.tsRule.rainfall, 'FaceColor', [0.3 0.6 1.0], 'DisplayName', 'Rainfall');
    plot(tVec, r.tsRule.ET, 'k-', 'LineWidth', 1.2, 'DisplayName', 'ET');
    plot(tVec, r.tsSacShield.residual, 'r-', 'LineWidth', 1.2, 'DisplayName', 'SAC Residual');
    yyaxis right;
    plot(tVec, double(r.tsSacShield.shieldIntervened), 'mo', 'MarkerSize', 4, 'DisplayName', 'Shield Active');
    ylabel('Shield');
    hold off;
    xlabel('Step'); ylabel('mm/step');
    title('Disturbances, Residual & Shield');
    legend('Location', 'best'); grid on;

    saveas(gcf, fullfile(outputDir, 'timeseries_comparison.png'));
    close(gcf);

    % ---- 图2: 指标对比图 — 复用离线重绘函数 ----
    renderResidualRlMetricsComparison(outputDir);
end

% generateEvalManifest - 生成评估清单JSON
function generateEvalManifest(outputDir, split, rlCfg, nPairs, nSeeds, timestamp)
    m = struct();
    m.split = split;
    m.timestamp = timestamp;
    m.matlabVersion = version();
    m.nScenarios = length(rlCfg.scenarios);
    m.nSeeds = nSeeds;
    m.nPairs = nPairs;
    m.nControllers = 4;
    m.controllers = {'rule', 'mpc', 'mpc_residual_sac_shielded', 'mpc_residual_sac_unshielded'};
    m.config = rlCfg;
    m.disclaimer = ['All results are from synthetic software-in-the-loop experiments. ', ...
        'No water-saving rate, yield increase, or field validity is claimed.'];

    jsonStr = jsonencode(m, 'PrettyPrint', true);
    fid = fopen(fullfile(outputDir, 'evaluation_manifest.json'), 'w');
    if fid < 0
        warning('无法写入 evaluation_manifest.json');
        return;
    end
    fprintf(fid, '%s', jsonStr);
    fclose(fid);
end
