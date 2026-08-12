% createResidualSacAgent - 创建SAC agent用于残差学习
% 参数:
%   obsInfo  - 观测规格 (rlNumericSpec, [6 1])
%   actInfo  - 动作规格 (rlNumericSpec, [1 1], 范围[-2,2])
%   rlCfg    - RL配置结构体 (含 sacOptions 字段)
% 返回:
%   agent - rlSACAgent 对象
% 说明:
%   使用MATLAB Reinforcement Learning Toolbox 内置 rlSACAgent,
%   6维观测输入, 1维连续动作输出。
%   Agent选项包括显式经验缓冲区、小批量大小、预热步数和折扣因子。
function agent = createResidualSacAgent(obsInfo, actInfo, rlCfg)
    % 创建默认SAC agent (自动构建actor/critic网络)
    agent = rlSACAgent(obsInfo, actInfo);

    % 设置agent选项
    opts = agent.AgentOptions;
    opts.DiscountFactor = rlCfg.sacOptions.DiscountFactor;
    opts.ExperienceBufferLength = rlCfg.sacOptions.ExperienceBufferLength;
    opts.MiniBatchSize = rlCfg.sacOptions.MiniBatchSize;
    opts.NumWarmStartSteps = rlCfg.sacOptions.NumWarmStartSteps;
    opts.SampleTime = rlCfg.sacOptions.SampleTime;

    % 禁用探索仅在三处使用: 训练时SAC自带探索, 评估时手动关闭
    % 此处保留默认探索策略供训练使用

    agent.AgentOptions = opts;
end
