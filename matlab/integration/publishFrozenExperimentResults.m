function results = publishFrozenExperimentResults(options)
% PUBLISHFROZENEXPERIMENTRESULTS 发布冻结实验运行到Java研究验证接口
%
%   读取三个冻结实验目录的CSV/JSON结果，构造实验run并
%   通过POST /api/research/experiments上传到后端。不重跑或篡改实验。
%   所有指标数字来自实际读取的文件，不含硬编码关键指标。
%
%   参数:
%       options - struct，包含以下字段:
%           baseUrl   (默认 'http://localhost:8080')
%           authToken (默认 '')
%           dryRun    (默认 true) — true时只返回/打印payload，不联网
%
%   返回:
%       results - struct数组，每条run的发布结果，含payload字段

    arguments
        options.baseUrl (1,:) char {mustBeNonempty} = 'http://localhost:8080'
        options.authToken (1,:) char = ''
        options.dryRun (1,1) logical = true
    end

    % 确定实验目录（相对于本函数文件位置）
    funcPath = mfilename('fullpath');
    [funcDir, ~, ~] = fileparts(funcPath);
    projectRoot = fullfile(funcDir, '..', '..');
    mpcDir = fullfile(projectRoot, 'matlab', 'experiments', 'results', ...
        'mpc_baseline_confirmatory_20260811_164430');
    rlDir = fullfile(projectRoot, 'matlab', 'experiments', 'rl', 'results', ...
        'pipeline_confirmatory_20260812_083550');
    ragDir = fullfile(projectRoot, 'rag', 'results', ...
        'rag_experiment_20260812_101005');

    results = struct('runKey', {}, 'success', {}, 'dryRun', {}, ...
        'message', {}, 'payload', {});

    %% ==================== 读取 MPC 冻结实验 ====================
    fprintf('=== 读取 MPC 冻结实验数据 ===\n');

    % Fail-fast: 检查必需文件
    mpcAggFile = fullfile(mpcDir, 'aggregate_metrics.csv');
    mpcDeltaFile = fullfile(mpcDir, 'paired_deltas.csv');
    mpcManifestFile = fullfile(mpcDir, 'manifest.json');
    assert(exist(mpcAggFile, 'file') == 2, ...
        sprintf('MPC aggregate_metrics.csv 缺失: %s', mpcAggFile));
    assert(exist(mpcDeltaFile, 'file') == 2, ...
        sprintf('MPC paired_deltas.csv 缺失: %s', mpcDeltaFile));
    assert(exist(mpcManifestFile, 'file') == 2, ...
        sprintf('MPC manifest.json 缺失: %s', mpcManifestFile));

    % 读取aggregate_metrics.csv（含controller_summary和paired_delta）
    mpcAgg = readtable(mpcAggFile, 'Delimiter', ',', 'VariableNamingRule', 'preserve');
    assert(~isempty(mpcAgg), 'MPC aggregate_metrics.csv 为空');

    % 读取manifest.json
    fid = fopen(mpcManifestFile, 'r');
    raw = fread(fid, '*char')';
    fclose(fid);
    mpcManifest = jsondecode(raw);

    % 筛选controller_summary行
    isCs = strcmp(mpcAgg.rowType, 'controller_summary');

    % MPC控制器：6场景水位MAE平均值
    mpcRows = isCs & strcmp(mpcAgg.controller, 'mpc');
    mpcWlMae = mean(mpcAgg.mean(mpcRows & strcmp(mpcAgg.metric, 'waterLevelMAE')));
    mpcIrrigation = mean(mpcAgg.mean(mpcRows & strcmp(mpcAgg.metric, 'totalIrrigationMm')));

    % Rule控制器：6场景水位MAE平均值
    ruleRows = isCs & strcmp(mpcAgg.controller, 'rule');
    ruleWlMae = mean(mpcAgg.mean(ruleRows & strcmp(mpcAgg.metric, 'waterLevelMAE')));
    ruleIrrigation = mean(mpcAgg.mean(ruleRows & strcmp(mpcAgg.metric, 'totalIrrigationMm')));

    % Paired delta：配对差场景均值
    isPd = strcmp(mpcAgg.rowType, 'paired_delta');
    deltaWlMae = mean(mpcAgg.mean(isPd & strcmp(mpcAgg.metric, 'delta_waterLevelMAE')));
    deltaIrrigation = mean(mpcAgg.mean(isPd & strcmp(mpcAgg.metric, 'delta_totalIrrigationMm')));

    % MPC QP回退：仅heavy_rain场景（所有种子累计）
    mpcFallbackHeavyRain = mpcAgg.mean( ...
        mpcRows & strcmp(mpcAgg.scenario, 'heavy_rain') ...
        & strcmp(mpcAgg.metric, 'mpcFallbackCount'));
    nSeeds = mpcManifest.nSeedsPerScenario;  % 30
    mpcFallbackTotal = round(mpcFallbackHeavyRain * nSeeds);

    % 安全越界次数（全部为0）
    mpcSafetyViolations = sum(mpcAgg.mean(mpcRows & strcmp(mpcAgg.metric, 'safetyViolationCount')));

    % Fail-fast: 所有读取值必须为有限值
    mpcValues = [mpcWlMae, ruleWlMae, deltaWlMae, mpcIrrigation, ruleIrrigation, ...
        deltaIrrigation, mpcFallbackHeavyRain, double(mpcFallbackTotal), mpcSafetyViolations];
    assert(all(isfinite(mpcValues)), 'MPC指标含非有限值，拒绝继续');

    % 从读取值构造摘要字符串
    mpcSummaryStr = sprintf(['6场景x%d种子x2控制器合成软件在环对照实验。' ...
        'MPC水位MAE均值%.4f mm优于规则控制%.4f mm（配对平均差%.4f mm）；' ...
        '但MPC平均多使用%.4f mm灌溉水，不支持节水结论；' ...
        'heavy_rain场景累计%d次QP安全回退；' ...
        '安全越界均为%d；MPC使用%d步完美短时预报假设。'], ...
        nSeeds, ...
        mpcWlMae, ruleWlMae, deltaWlMae, ...
        deltaIrrigation, ...
        mpcFallbackTotal, ...
        mpcSafetyViolations, ...
        mpcManifest.config.mpcHorizon);

    mpcRun = struct();
    mpcRun.runKey = 'mpc-baseline-confirmatory-20260811-164430';
    mpcRun.experimentType = 'MPC';
    mpcRun.title = 'MPC基线确认性实验验证';
    mpcRun.sourceType = 'SIMULATION';
    mpcRun.status = 'MIXED';
    mpcRun.resultSummary = mpcSummaryStr;
    mpcRun.limitations = sprintf(['合成仿真结果，无田间标定；' ...
        '不能声称节水或增产；MPC完美预报假设可能高估实际性能。']);
    mpcRun.manifestPath = ...
        'matlab/experiments/results/mpc_baseline_confirmatory_20260811_164430/VALIDATION.md';
    mpcRun.executedAt = '2026-08-11T16:44:30';
    mpcRun.metrics = {
        createMetric('MPC', '水位MAE', mpcWlMae, 'mm', false, ...
            sprintf('%d场景x%d种子均值', length(mpcManifest.scenarios), nSeeds))
        createMetric('规则控制', '水位MAE', ruleWlMae, 'mm', false, ...
            sprintf('%d场景x%d种子均值', length(mpcManifest.scenarios), nSeeds))
        createMetric('MPC', '灌溉水量', mpcIrrigation, 'mm', false, ...
            sprintf('%d场景x%d种子均值', length(mpcManifest.scenarios), nSeeds))
        createMetric('规则控制', '灌溉水量', ruleIrrigation, 'mm', false, ...
            sprintf('%d场景x%d种子均值', length(mpcManifest.scenarios), nSeeds))
        createMetric('MPC-规则', '配对水位MAE差', deltaWlMae, 'mm', true, '负值=MPC更优')
        createMetric('MPC-规则', '灌溉水量差', deltaIrrigation, 'mm', false, '正值=MPC更多')
        createMetric('MPC', '安全越界次数', mpcSafetyViolations, '次', false, '')
        createMetric('MPC', 'QP回退次数', mpcFallbackTotal, '次', false, '仅heavy_rain场景')
    };

    %% ==================== 读取 RL 冻结实验（test目录） ====================
    fprintf('=== 读取 RL 冻结实验数据（test目录） ===\n');

    rlTestDir = fullfile(rlDir, 'test', 'evaluation_test_20260812_091440');
    rlAggFile = fullfile(rlTestDir, 'aggregate_metrics.csv');
    rlPairedFile = fullfile(rlTestDir, 'paired_vs_mpc.csv');

    assert(exist(rlAggFile, 'file') == 2, ...
        sprintf('RL aggregate_metrics.csv 缺失: %s', rlAggFile));
    assert(exist(rlPairedFile, 'file') == 2, ...
        sprintf('RL paired_vs_mpc.csv 缺失: %s', rlPairedFile));

    rlAgg = readtable(rlAggFile, 'Delimiter', ',', 'VariableNamingRule', 'preserve');
    assert(~isempty(rlAgg), 'RL aggregate_metrics.csv 为空');

    rlPaired = readtable(rlPairedFile, 'Delimiter', ',', 'VariableNamingRule', 'preserve');
    assert(~isempty(rlPaired), 'RL paired_vs_mpc.csv 为空');

    % 筛选SAC屏蔽控制器的controller_summary行
    isRlCs = strcmp(rlAgg.rowType, 'controller_summary');
    sacRows = isRlCs & strcmp(rlAgg.controller, 'mpc_residual_sac_shielded');
    mpcRowsRl = isRlCs & strcmp(rlAgg.controller, 'mpc');

    sacWlMae = mean(rlAgg.mean(sacRows & strcmp(rlAgg.metric, 'waterLevelMAE')));
    mpcWlMaeRl = mean(rlAgg.mean(mpcRowsRl & strcmp(rlAgg.metric, 'waterLevelMAE')));
    sacIrrigation = mean(rlAgg.mean(sacRows & strcmp(rlAgg.metric, 'totalIrrigationMm')));
    mpcIrrigationRl = mean(rlAgg.mean(mpcRowsRl & strcmp(rlAgg.metric, 'totalIrrigationMm')));

    % 安全屏蔽干预数（6场景均值 * 种子数）
    nSeedsRl = unique(rlAgg.n(isRlCs));
    assert(isscalar(nSeedsRl), 'RL种子数不一致');
    sacShieldMean = sum(rlAgg.mean(sacRows & strcmp(rlAgg.metric, 'shieldInterventionCount')));
    sacShieldTotal = round(sacShieldMean * nSeedsRl);

    % 从paired_vs_mpc计算配对差和更差回合率
    sacPairedRows = strcmp(rlPaired.controller, 'mpc_residual_sac_shielded');
    deltaWlMaeRl = mean(rlPaired.delta_waterLevelMAE(sacPairedRows));
    worseRatio = mean(rlPaired.delta_waterLevelMAE(sacPairedRows) > 0);

    % 安全越界
    sacSafetyViolations = sum(rlAgg.mean(sacRows & strcmp(rlAgg.metric, 'safetyViolationCount')));

    % Fail-fast: 所有读取值必须为有限值
    rlValues = [sacWlMae, mpcWlMaeRl, deltaWlMaeRl, worseRatio, sacIrrigation, ...
        mpcIrrigationRl, double(sacShieldTotal), sacSafetyViolations, ...
        sacShieldMean, double(nSeedsRl)];
    assert(all(isfinite(rlValues)), 'RL指标含非有限值，拒绝继续');

    rlSummaryStr = sprintf(['残差SAC+安全屏蔽在独立测试集（6场景x%d种子）上明显劣于MPC。' ...
        'SAC+安全屏蔽水位MAE均值%.3f mm vs MPC %.3f mm（配对差+%.3f mm，%.2f%%回合更差）；' ...
        '总灌溉量%.3f mm vs MPC %.3f mm；' ...
        '安全屏蔽累计干预%d步；退化主要集中降雨扰动场景。' ...
        '该结果证明平台具备真实RL训练和独立评估能力，但当前残差SAC不适合声称优于MPC。'], ...
        nSeedsRl, sacWlMae, mpcWlMaeRl, deltaWlMaeRl, worseRatio * 100, ...
        sacIrrigation, mpcIrrigationRl, sacShieldTotal);

    rlRun = struct();
    rlRun.runKey = 'residual-sac-confirmatory-20260812-083550';
    rlRun.experimentType = 'RL';
    rlRun.title = '残差SAC确认性实验验证';
    rlRun.sourceType = 'SIMULATION';
    rlRun.status = 'FAILED';
    rlRun.resultSummary = rlSummaryStr;
    rlRun.limitations = sprintf(['合成仿真结果；残差SAC不适合声称优于MPC；' ...
        '安全越界为0仅对当前合成边界成立；不构成田间安全认证。']);
    rlRun.manifestPath = ...
        'matlab/experiments/rl/results/pipeline_confirmatory_20260812_083550/VALIDATION.md';
    rlRun.executedAt = '2026-08-12T08:35:50';
    rlRun.metrics = {
        createMetric('SAC+安全屏蔽', '水位MAE', sacWlMae, 'mm', false, '6场景x30种子')
        createMetric('MPC', '水位MAE', mpcWlMaeRl, 'mm', false, '匹配场景x种子')
        createMetric('SAC+安全屏蔽', '灌溉水量', sacIrrigation, 'mm', false, '')
        createMetric('MPC', '灌溉水量', mpcIrrigationRl, 'mm', false, '')
        createMetric('SAC-MPC', '配对水位MAE差', deltaWlMaeRl, 'mm', false, '正值=SAC更差')
        createMetric('SAC', '更差回合率', worseRatio, '', false, '配对差>0的回合占比')
        createMetric('SAC', '安全屏蔽干预', sacShieldTotal, '次', false, '累计干预步数')
        createMetric('SAC+安全屏蔽', '安全越界', sacSafetyViolations, '次', false, '')
    };

    %% ==================== 读取 RAG 冻结实验 ====================
    fprintf('=== 读取 RAG 冻结实验数据 ===\n');

    ragMetricsFile = fullfile(ragDir, 'retrieval_metrics.csv');
    ragManifestFileR = fullfile(ragDir, 'rag_manifest.json');

    assert(exist(ragMetricsFile, 'file') == 2, ...
        sprintf('RAG retrieval_metrics.csv 缺失: %s', ragMetricsFile));
    assert(exist(ragManifestFileR, 'file') == 2, ...
        sprintf('RAG rag_manifest.json 缺失: %s', ragManifestFileR));

    ragMetricsTbl = readtable(ragMetricsFile, 'Delimiter', ',', 'VariableNamingRule', 'preserve');
    assert(~isempty(ragMetricsTbl), 'RAG retrieval_metrics.csv 为空');

    fid = fopen(ragManifestFileR, 'r');
    raw = fread(fid, '*char')';
    fclose(fid);
    ragManifest = jsondecode(raw);

    % 按retriever筛选行
    bm25Row = strcmp(ragMetricsTbl.retriever, 'bm25');
    hybridRow = strcmp(ragMetricsTbl.retriever, 'hybrid');

    assert(any(bm25Row), 'RAG CSV中缺失BM25行');
    assert(any(hybridRow), 'RAG CSV中缺失hybrid行');

    % 读取BM25指标
    bm25AbsAcc = ragMetricsTbl.abstention_accuracy(bm25Row);
    bm25FalseAns = ragMetricsTbl.false_answer_rate(bm25Row);
    bm25CitePrec = ragMetricsTbl.citation_precision(bm25Row);
    bm25Recall1 = ragMetricsTbl.('recall@1')(bm25Row);
    bm25Recall3 = ragMetricsTbl.('recall@3')(bm25Row);
    bm25Mrr1 = ragMetricsTbl.('mrr@1')(bm25Row);
    bm25InDomRecall = ragMetricsTbl.in_domain_answer_recall(bm25Row);
    bm25OodRecall = ragMetricsTbl.ood_recall(bm25Row);

    % 读取hybrid指标
    hybridAbsAcc = ragMetricsTbl.abstention_accuracy(hybridRow);
    hybridFalseAns = ragMetricsTbl.false_answer_rate(hybridRow);
    hybridCitePrec = ragMetricsTbl.citation_precision(hybridRow);
    hybridRecall1 = ragMetricsTbl.('recall@1')(hybridRow);
    hybridOodRecall = ragMetricsTbl.ood_recall(hybridRow);

    % 从manifest获取冻结阈值
    bm25Threshold = ragManifest.retrievers.bm25.threshold;
    hybridThreshold = ragManifest.retrievers.hybrid.threshold;

    % Fail-fast: 所有读取值必须为有限值
    ragValues = [bm25AbsAcc, bm25FalseAns, bm25CitePrec, bm25Recall1, bm25Recall3, ...
        bm25Mrr1, bm25InDomRecall, bm25OodRecall, ...
        hybridAbsAcc, hybridFalseAns, hybridCitePrec, hybridRecall1, hybridOodRecall, ...
        bm25Threshold, hybridThreshold];
    assert(all(isfinite(ragValues)), 'RAG指标含非有限值，拒绝继续');

    ragSummaryStr = sprintf(['BM25 test拒答准确率%.1f、误答率%.1f、引用精确率%.3f；' ...
        'hybrid误答率%.3f、引用精确率%.3f。集成默认仅使用BM25；' ...
        'hybrid只作为实验失败/对照展示，不作为生产默认。'], ...
        bm25AbsAcc, bm25FalseAns, bm25CitePrec, ...
        hybridFalseAns, hybridCitePrec);

    ragRun = struct();
    ragRun.runKey = 'rag-experiment-20260812-101005';
    ragRun.experimentType = 'RAG';
    ragRun.title = 'RAG检索实验（BM25和hybrid对照）';
    ragRun.sourceType = 'INTERNAL_BENCHMARK';
    ragRun.status = 'MIXED';
    ragRun.resultSummary = ragSummaryStr;
    ragRun.limitations = sprintf(['人工构造小样本基准，非严格盲测；' ...
        '自动指标不代表LLM生成质量或事实正确率；' ...
        'hybrid对照组仅作内部参考，不部署生产。']);
    ragRun.manifestPath = 'rag/results/rag_experiment_20260812_101005/rag_manifest.json';
    ragRun.executedAt = '2026-08-12T10:10:05';
    ragRun.metrics = {
        createMetric('BM25', '拒答准确率', bm25AbsAcc, '', true, 'abstention_accuracy')
        createMetric('BM25', '误答率', bm25FalseAns, '', false, 'false_answer_rate')
        createMetric('BM25', '引用精确率', bm25CitePrec, '', true, 'citation_precision')
        createMetric('BM25', '召回率@1', bm25Recall1, '', true, '')
        createMetric('BM25', '召回率@3', bm25Recall3, '', true, '')
        createMetric('BM25', 'MRR@1', bm25Mrr1, '', true, '')
        createMetric('BM25', '阈值', bm25Threshold, '', false, 'BM25冻结阈值')
        createMetric('BM25', '域内回答召回率', bm25InDomRecall, '', true, 'in_domain_answer_recall')
        createMetric('BM25', 'OOD召回率', bm25OodRecall, '', true, 'ood_recall')
        createMetric('hybrid', '拒答准确率', hybridAbsAcc, '', true, '')
        createMetric('hybrid', '误答率', hybridFalseAns, '', false, '明显劣于BM25')
        createMetric('hybrid', '引用精确率', hybridCitePrec, '', true, '明显劣于BM25')
        createMetric('hybrid', '召回率@1', hybridRecall1, '', true, '')
        createMetric('hybrid', '阈值', hybridThreshold, '', false, 'hybrid冻结阈值')
        createMetric('hybrid', 'OOD召回率', hybridOodRecall, '', true, '')
    };

    %% ==================== 发布 ====================
    runs = {mpcRun, rlRun, ragRun};
    for i = 1:numel(runs)
        run = runs{i};
        payload = buildPayload(run);

        if options.dryRun
            fprintf('\n--- DRY RUN: %s ---\n', run.runKey);
            fprintf('Type: %s | Status: %s\n', run.experimentType, run.status);
            fprintf('Title: %s\n', run.title);
            fprintf('Metrics: %d 条\n', size(run.metrics, 1));
            results(end+1) = struct('runKey', run.runKey, ...
                'success', true, 'dryRun', true, ...
                'message', 'Dry run — payload已打印，未联网发送', ...
                'payload', payload);  %#ok<AGROW>
        else
            try
                response = webwrite( ...
                    [options.baseUrl '/api/research/experiments'], ...
                    payload, ...
                    weboptions('MediaType', 'application/json', ...
                    'HeaderFields', {'Authorization', ['Bearer ' options.authToken]}, ...
                    'Timeout', 30));
                fprintf('✓ %s 发布成功\n', run.runKey);
                results(end+1) = struct('runKey', run.runKey, ...
                    'success', true, 'dryRun', false, ...
                    'message', '发布成功', ...
                    'payload', payload);  %#ok<AGROW>
            catch ME
                fprintf('✗ %s 发布失败: %s\n', run.runKey, ME.message);
                results(end+1) = struct('runKey', run.runKey, ...
                    'success', false, 'dryRun', false, ...
                    'message', ME.message, ...
                    'payload', payload);  %#ok<AGROW>
            end
        end
    end

    % 打印汇总
    fprintf('\n=== 发布汇总 ===\n');
    for i = 1:numel(results)
        r = results(i);
        statusStr = '成功';
        if ~r.success, statusStr = '失败'; end
        if r.dryRun, statusStr = 'Dry-run'; end
        fprintf('  %s: %s — %s\n', r.runKey, statusStr, r.message);
    end
end

% 构造单条指标结构体
function metric = createMetric(methodName, metricName, metricValue, unit, higherIsBetter, notes)
    metric = struct();
    metric.methodName = methodName;
    metric.metricName = metricName;
    metric.metricValue = metricValue;
    metric.unit = unit;
    metric.higherIsBetter = higherIsBetter;
    if nargin >= 6
        metric.notes = notes;
    end
end

% 构建JSON payload结构体
function payload = buildPayload(run)
    nMetrics = size(run.metrics, 1);
    metricsArray = cell(1, nMetrics);
    for j = 1:nMetrics
        m = run.metrics{j};
        metricsArray{j} = m;
    end

    payload.runKey = run.runKey;
    payload.experimentType = run.experimentType;
    payload.title = run.title;
    payload.sourceType = run.sourceType;
    payload.status = run.status;
    payload.resultSummary = run.resultSummary;
    payload.limitations = run.limitations;
    payload.manifestPath = run.manifestPath;
    payload.executedAt = run.executedAt;
    payload.metrics = metricsArray;
end
