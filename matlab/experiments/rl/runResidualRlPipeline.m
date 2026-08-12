% runResidualRlPipeline - 残差SAC训练+评估全流程入口
% 参数:
%   mode       - 运行模式: 'smoke' | 'confirmatory'
%   outputRoot - 输出根目录 (可选)
% 返回:
%   output - 含 training, validation, test 结果的结构体
% 说明:
%   smoke: 运行smoke训练 + validation少量seed子集 (不运行test)
%   confirmatory: 运行600回合训练 + 完整validation + 一次test
%   不得根据test结果反向调整参数或网络。
function output = runResidualRlPipeline(mode, outputRoot)
    if nargin < 1 || isempty(mode)
        mode = 'smoke';
    end
    if nargin < 2 || isempty(outputRoot)
        outputRoot = fullfile(tempdir, 'paddy-residual-rl');
    end

    validModes = {'smoke', 'confirmatory'};
    if ~ismember(lower(mode), validModes)
        error('runResidualRlPipeline:invalidMode', ...
            '模式必须为 ''smoke'' 或 ''confirmatory'', 收到: ''%s''', mode);
    end

    rlCfg = residualRlConfig();
    timestamp = datestr(now, 'yyyymmdd_HHMMSS');
    pipelineDir = fullfile(outputRoot, sprintf('pipeline_%s_%s', mode, timestamp));
    if ~exist(pipelineDir, 'dir')
        mkdir(pipelineDir);
    end

    fprintf('=============================================================\n');
    fprintf('  残差SAC RL Pipeline: %s\n', mode);
    fprintf('  输出根目录: %s\n', pipelineDir);
    fprintf('=============================================================\n\n');

    output = struct();
    output.mode = mode;
    output.pipelineDir = pipelineDir;

    % ====== 第1阶段: 训练 ======
    fprintf('====== 第1阶段: 训练 ======\n');
    trainDir = fullfile(pipelineDir, 'training');
    trainOut = trainResidualSac(mode, trainDir);
    output.training = trainOut;
    fprintf('\n');

    agent = trainOut.agent;
    agentPath = fullfile(trainOut.outputDir, 'trained_agent.mat');

    % ====== 第2阶段: 验证评估 ======
    fprintf('====== 第2阶段: 验证评估 ======\n');
    valDir = fullfile(pipelineDir, 'validation');

    if strcmpi(mode, 'smoke')
        % smoke模式: 只评估validation的前2个seed (加速)
        fprintf('Smoke模式: 使用validation前2个seed的子集\n');
        output.validation = evaluateResidualSac(agentPath, 'validation', valDir, 2);
    else
        fprintf('Confirmatory模式: 完整validation\n');
        output.validation = evaluateResidualSac(agentPath, 'validation', valDir);
    end
    fprintf('\n');

    % ====== 第3阶段: 最终测试 (仅confirmatory) ======
    if strcmpi(mode, 'confirmatory')
        fprintf('====== 第3阶段: 最终测试 ======\n');
        fprintf('警告: 最终测试后不得修改任何参数!\n');
        testDir = fullfile(pipelineDir, 'test');
        output.test = evaluateResidualSac(agentPath, 'test', testDir);
        fprintf('\n');
    else
        output.test = [];
        fprintf('Smoke模式: 跳过最终测试\n');
    end

    % ====== Pipeline Manifest ======
    generatePipelineManifest(pipelineDir, mode, rlCfg, output, timestamp);
    fprintf('Pipeline manifest已生成\n');

    fprintf('\n=============================================================\n');
    fprintf('Pipeline完成: %s\n', pipelineDir);
    fprintf('=============================================================\n');
end

% generatePipelineManifest - 生成管道清单
function generatePipelineManifest(pipelineDir, mode, rlCfg, output, timestamp)
    m = struct();
    m.mode = mode;
    m.timestamp = timestamp;
    m.matlabVersion = version();
    m.seedPartition.training = sprintf('%d:%d', rlCfg.trainingSeeds(1), rlCfg.trainingSeeds(end));
    m.seedPartition.validation = sprintf('%d:%d', rlCfg.validationSeeds(1), rlCfg.validationSeeds(end));
    m.seedPartition.test = sprintf('%d:%d', rlCfg.testSeeds(1), rlCfg.testSeeds(end));
    m.agentRandomSeed = rlCfg.agentRandomSeed;
    m.rewardWeights = rlCfg.rewardWeights;

    m.stages = struct();
    m.stages.training = output.training.outputDir;
    if ~isempty(output.validation)
        m.stages.validation = output.validation.outputDir;
    end
    if ~isempty(output.test)
        m.stages.test = output.test.outputDir;
    end

    m.disclaimer = ['All results are from synthetic software-in-the-loop experiments. ', ...
        'No water-saving rate, yield increase, or field validity is claimed.'];

    jsonStr = jsonencode(m, 'PrettyPrint', true);
    fid = fopen(fullfile(pipelineDir, 'pipeline_manifest.json'), 'w');
    if fid < 0
        warning('无法写入 pipeline_manifest.json');
        return;
    end
    fprintf(fid, '%s', jsonStr);
    fclose(fid);
end
