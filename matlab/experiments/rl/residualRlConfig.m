% residualRlConfig - 返回独立RL配置, 不覆写 paddyexp.config()
% 返回:
%   rlCfg - 残差SAC实验配置结构体
% 说明:
%   所有seed集与1-30互斥且两两无交集。
%   参数为合成设定, 非农艺处方。
function rlCfg = residualRlConfig()
    % 读取基础配置以获取共享参数 (不修改paddyexp.config())
    baseCfg = paddyexp.config();

    % ---- 基础仿真参数 (从baseCfg完整继承, 保持paddyexp兼容性) ----
    % 时间参数
    rlCfg.T = baseCfg.T;                         % 每回合步数
    rlCfg.dtHours = baseCfg.dtHours;             % 时间步长

    % 目标与安全区间
    rlCfg.targetWaterLevel = baseCfg.targetWaterLevel;  % 目标水深 (mm)
    rlCfg.bandLow = baseCfg.bandLow;             % 性能区间下限
    rlCfg.bandHigh = baseCfg.bandHigh;           % 性能区间上限
    rlCfg.safetyLow = baseCfg.safetyLow;         % 安全区间下限
    rlCfg.safetyHigh = baseCfg.safetyHigh;       % 安全区间上限

    % 灌溉动作限制
    rlCfg.actionMin = baseCfg.actionMin;         % 动作下限
    rlCfg.actionMax = baseCfg.actionMax;         % 动作上限

    % 排水参数
    rlCfg.maxDrainage = baseCfg.maxDrainage;     % 最大排水速率

    % 名义参数 (MPC使用)
    rlCfg.nominalInfiltration = baseCfg.nominalInfiltration;
    rlCfg.nominalActuatorGain = baseCfg.nominalActuatorGain;
    rlCfg.nominalDelay = baseCfg.nominalDelay;

    % MPC参数
    rlCfg.mpcHorizon = baseCfg.mpcHorizon;
    rlCfg.mpcStateWeight = baseCfg.mpcStateWeight;
    rlCfg.mpcInputWeight = baseCfg.mpcInputWeight;
    rlCfg.mpcDeltaWeight = baseCfg.mpcDeltaWeight;

    % 规则控制器参数
    rlCfg.ruleIrrigateOn = baseCfg.ruleIrrigateOn;
    rlCfg.ruleIrrigateOff = baseCfg.ruleIrrigateOff;
    rlCfg.ruleIrrigationRate = baseCfg.ruleIrrigationRate;
    rlCfg.ruleMinSteps = baseCfg.ruleMinSteps;

    % 场景列表
    rlCfg.scenarios = baseCfg.scenarios;         % 场景列表

    % ---- Seed分区 (三组互斥且与1-30互斥) ----
    rlCfg.trainingSeeds = 10001:10100;           % 训练: 100个seed
    rlCfg.validationSeeds = 20001:20010;         % 验证: 10个seed
    rlCfg.testSeeds = 30001:30030;               % 最终测试: 30个seed
    rlCfg.agentRandomSeed = 20260811;            % Agent随机种子

    % ---- 残差动作空间 ----
    rlCfg.residualRange = [-2, 2];               % 残差范围 (mm/步)
    rlCfg.actionChangeLimit = 2;                 % 动作变化率限制 (mm/步)

    % ---- 安全屏蔽参数 ----
    rlCfg.safetyHighMargin = 10;                 % 高水位余量 (mm)
    rlCfg.safetyLowMargin = 5;                   % 低水位余量 (mm)

    % ---- 观测归一化尺度与钳制 ----
    rlCfg.obsNorm.waterLevelError = baseCfg.targetWaterLevel;  % 除以目标水位
    rlCfg.obsNorm.action = baseCfg.actionMax;    % 除以最大动作
    rlCfg.obsNorm.rainfall = 10.0;               % 降雨参考尺度 (mm/步)
    rlCfg.obsNorm.ET = 1.0;                      % 蒸散参考尺度 (mm/步)
    rlCfg.obsNorm.residual = rlCfg.residualRange(2);  % 除以最大残差绝对值

    rlCfg.obsClamp.waterLevelError = [-2, 2];    % 归一化水位误差钳制
    rlCfg.obsClamp.action = [0, 1];              % 归一化动作钳制
    rlCfg.obsClamp.rainfall = [0, 1];            % 归一化降雨钳制
    rlCfg.obsClamp.ET = [0, 1];                  % 归一化蒸散钳制
    rlCfg.obsClamp.residual = [-1, 1];           % 归一化残差钳制

    % ---- 奖励权重 (负加权和) ----
    rlCfg.rewardWeights.tracking = 1.0;          % 水位跟踪误差
    rlCfg.rewardWeights.irrigation = 0.1;        % 灌溉量代价
    rlCfg.rewardWeights.actionChange = 0.05;     % 动作变化率代价
    rlCfg.rewardWeights.bandViolation = 2.0;     % 性能区间越界代价
    rlCfg.rewardWeights.safetyViolation = 10.0;  % 安全越界代价
    rlCfg.rewardWeights.shieldIntervention = 1.0;% 屏蔽干预代价

    % ---- SAC agent 默认超参数 ----
    rlCfg.sacOptions.DiscountFactor = 0.99;
    rlCfg.sacOptions.ExperienceBufferLength = 1e5;
    rlCfg.sacOptions.MiniBatchSize = 128;
    rlCfg.sacOptions.NumWarmStartSteps = 500;
    rlCfg.sacOptions.SampleTime = 1;

    % ---- 训练默认参数 ----
    rlCfg.smokeEpisodes = 6;
    rlCfg.confirmatoryEpisodes = 600;
    rlCfg.stepsPerEpisode = rlCfg.T;             % = 96
end
