% trainResidualSac - 训练残差SAC agent
% 参数:
%   mode       - 运行模式: 'smoke' (≥6 episode) | 'confirmatory' (600 episode)
%   outputRoot - 输出根目录 (可选, 默认 tempdir 下自动生成)
% 返回:
%   output - 含 outputDir, agent, trainingStats 的结构体
% 说明:
%   训练前设置 rng(agentRandomSeed, 'twister') 确保可复现。
%   保存 trained_agent.mat, training_stats.csv, training_curve.png,
%   training_manifest.json。
function output = trainResidualSac(mode, outputRoot)
    if nargin < 1 || isempty(mode)
        mode = 'smoke';
    end
    if nargin < 2 || isempty(outputRoot)
        outputRoot = fullfile(tempdir, 'paddy-residual-rl-training');
    end

    % ---- 加载配置 ----
    rlCfg = residualRlConfig();

    % ---- 确定episode数 ----
    switch lower(mode)
        case 'smoke'
            nEpisodes = rlCfg.smokeEpisodes;
        case 'confirmatory'
            nEpisodes = rlCfg.confirmatoryEpisodes;
        otherwise
            error('trainResidualSac:unknownMode', ...
                '未知模式 ''%s''。仅支持 ''smoke'' 或 ''confirmatory''。', mode);
    end

    % ---- 创建时间戳输出目录 ----
    timestamp = datestr(now, 'yyyymmdd_HHMMSS');
    outputDir = fullfile(outputRoot, sprintf('training_%s_%s', mode, timestamp));
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end

    fprintf('=== 残差SAC训练 ===\n');
    fprintf('模式: %s | Episode数: %d | 每回合步数: %d\n', mode, nEpisodes, rlCfg.stepsPerEpisode);
    fprintf('训练seed范围: %d:%d\n', rlCfg.trainingSeeds(1), rlCfg.trainingSeeds(end));
    fprintf('输出: %s\n\n', outputDir);

    % ---- 固定随机种子 ----
    rng(rlCfg.agentRandomSeed, 'twister');
    fprintf('随机种子已设置: rng(%d, ''twister'')\n', rlCfg.agentRandomSeed);

    % ---- 验证环境 (独立env, 不消耗训练env的reset序列) ----
    fprintf('验证环境...\n');
    validationEnv = createResidualRlEnvironment(rlCfg);
    validateEnvironment(validationEnv);

    % ---- 创建训练用全新环境 ----
    fprintf('创建训练环境...\n');
    env = createResidualRlEnvironment(rlCfg);

    obsInfo = getObservationInfo(env);
    actInfo = getActionInfo(env);

    fprintf('创建SAC agent...\n');
    agent = createResidualSacAgent(obsInfo, actInfo, rlCfg);
    fprintf('Agent创建完成 (连续动作SAC)\n');

    % ---- 设置训练选项 ----
    % 显式检查点目录 (绝对路径), 避免依赖进程工作目录下的 savedAgents
    checkpointDir = fullfile(outputDir, 'checkpoints');
    if ~exist(checkpointDir, 'dir')
        mkdir(checkpointDir);
    end

    trainOpts = rlTrainingOptions(...
        'MaxEpisodes', nEpisodes, ...
        'MaxStepsPerEpisode', rlCfg.stepsPerEpisode, ...
        'StopTrainingCriteria', 'EpisodeCount', ...
        'StopTrainingValue', nEpisodes, ...
        'SaveAgentCriteria', 'EpisodeCount', ...
        'SaveAgentValue', nEpisodes, ...
        'SaveAgentDirectory', checkpointDir, ...
        'Plots', 'none', ...
        'Verbose', true, ...
        'ScoreAveragingWindowLength', max(1, min(10, floor(nEpisodes / 5))));

    % ---- 训练 ----
    fprintf('\n开始训练 (%d episodes)...\n', nEpisodes);
    tStart = tic;
    trainStats = train(agent, env, trainOpts);
    elapsed = toc(tStart);
    fprintf('训练完成 (%.1f 秒)\n', elapsed);

    % ---- 保存agent ----
    agentPath = fullfile(outputDir, 'trained_agent.mat');
    save(agentPath, 'agent');
    fprintf('Agent已保存: %s\n', agentPath);

    % ---- 导出训练统计CSV ----
    statsTable = exportTrainingStats(trainStats);
    statsPath = fullfile(outputDir, 'training_stats.csv');
    writetable(statsTable, statsPath);
    fprintf('training_stats.csv: %d 行已写入\n', height(statsTable));

    % ---- 生成训练曲线PNG ----
    generateTrainingCurve(trainStats, outputDir);
    fprintf('training_curve.png 已生成\n');

    % ---- 生成训练manifest ----
    generateTrainingManifest(outputDir, mode, rlCfg, nEpisodes, elapsed, trainStats, timestamp);
    fprintf('training_manifest.json 已生成\n');

    % ---- 汇总 ----
    output.outputDir = outputDir;
    output.agent = agent;
    output.trainingStats = trainStats;
    output.summary = sprintf([ ...
        '残差SAC训练完成\n', ...
        '  模式: %s | Episodes: %d | 总步数: %d\n', ...
        '  耗时: %.1f 秒\n', ...
        '  输出: %s'], ...
        mode, nEpisodes, rlCfg.stepsPerEpisode * nEpisodes, elapsed, outputDir);

    fprintf('\n%s\n', output.summary);
end

% exportTrainingStats - 将 trainStats 结构体转换为 table
% R2025a 的 rlTrainingResult 无 GlobalStepCount 属性, 从 EpisodeSteps 独立计算
function tbl = exportTrainingStats(trainStats)
    n = length(trainStats.EpisodeIndex);
    globalSteps = cumsum(trainStats.EpisodeSteps(:));
    tbl = table(trainStats.EpisodeIndex(:), trainStats.EpisodeReward(:), ...
        trainStats.EpisodeSteps(:), trainStats.AverageReward(:), ...
        globalSteps, ...
        'VariableNames', {'Episode', 'EpisodeReward', 'EpisodeSteps', ...
        'AverageReward', 'GlobalStepCount'});
end

% generateTrainingCurve - 生成回合奖励曲线图
function generateTrainingCurve(trainStats, outputDir)
    episodes = trainStats.EpisodeIndex(:);
    rewards = trainStats.EpisodeReward(:);
    avgRewards = trainStats.AverageReward(:);

    figure('Position', [100, 100, 1000, 500], 'Visible', 'off');
    hold on;
    plot(episodes, rewards, 'b-', 'LineWidth', 1, 'DisplayName', 'Per-Episode Reward');
    plot(episodes, avgRewards, 'r-', 'LineWidth', 1.5, 'DisplayName', 'Moving Average');
    hold off;
    xlabel('Episode');
    ylabel('Reward');
    title('Residual SAC Training Curve');
    legend('Location', 'best');
    grid on;

    saveas(gcf, fullfile(outputDir, 'training_curve.png'));
    close(gcf);
end

% generateTrainingManifest - 生成训练实验清单JSON
function generateTrainingManifest(outputDir, mode, rlCfg, nEpisodes, elapsed, trainStats, timestamp)
    m = struct();
    m.mode = mode;
    m.timestamp = timestamp;
    m.matlabVersion = version();
    m.elapsedSeconds = elapsed;
    m.nEpisodes = nEpisodes;
    m.stepsPerEpisode = rlCfg.stepsPerEpisode;
    m.totalSteps = nEpisodes * rlCfg.stepsPerEpisode;

    % seed分区
    m.seedPartition.trainingSeeds = sprintf('%d:%d', rlCfg.trainingSeeds(1), rlCfg.trainingSeeds(end));
    m.seedPartition.nTraining = length(rlCfg.trainingSeeds);
    m.seedPartition.validationSeeds = sprintf('%d:%d', rlCfg.validationSeeds(1), rlCfg.validationSeeds(end));
    m.seedPartition.nValidation = length(rlCfg.validationSeeds);
    m.seedPartition.testSeeds = sprintf('%d:%d', rlCfg.testSeeds(1), rlCfg.testSeeds(end));
    m.seedPartition.nTest = length(rlCfg.testSeeds);
    m.seedPartition.agentRandomSeed = rlCfg.agentRandomSeed;

    % 奖励参数
    m.rewardWeights = rlCfg.rewardWeights;
    m.rewardEquation = ['r = -(w1*((h-target)/target)^2 + w2*(action/actionMax) + ', ...
        'w3*|action-prevAction|/actionMax + w4*bandViolationFlag + ', ...
        'w5*safetyViolationFlag + w6*shieldInterventionFlag)'];

    % agent选项
    m.agentOptions.DiscountFactor = rlCfg.sacOptions.DiscountFactor;
    m.agentOptions.ExperienceBufferLength = rlCfg.sacOptions.ExperienceBufferLength;
    m.agentOptions.MiniBatchSize = rlCfg.sacOptions.MiniBatchSize;
    m.agentOptions.NumWarmStartSteps = rlCfg.sacOptions.NumWarmStartSteps;

    % 安全屏蔽参数
    m.safetyShield.residualRange = rlCfg.residualRange;
    m.safetyShield.actionChangeLimit = rlCfg.actionChangeLimit;
    m.safetyShield.safetyHighMargin = rlCfg.safetyHighMargin;
    m.safetyShield.safetyLowMargin = rlCfg.safetyLowMargin;

    % 最终训练奖励
    finalEpisodeReward = trainStats.EpisodeReward(end);
    finalAverageReward = trainStats.AverageReward(end);
    m.finalRewards.episodeReward = finalEpisodeReward;
    m.finalRewards.averageReward = finalAverageReward;

    m.disclaimer = ['All results are from synthetic software-in-the-loop experiments. ', ...
        'No water-saving rate, yield increase, or field validity is claimed.'];

    jsonStr = jsonencode(m, 'PrettyPrint', true);
    fid = fopen(fullfile(outputDir, 'training_manifest.json'), 'w');
    if fid < 0
        warning('无法写入 training_manifest.json');
        return;
    end
    fprintf(fid, '%s', jsonStr);
    fclose(fid);
end
