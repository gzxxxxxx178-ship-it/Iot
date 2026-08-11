% config - 返回默认实验配置结构体
% 返回:
%   cfg - 包含所有仿真参数的配置结构体
% 说明:
%   所有参数为合成软件在环设定, 非农艺处方。
%   后续RL可通过修改此配置接入。
function cfg = config()
    cfg = struct();

    % ---- 时间参数 ----
    cfg.dtHours = 0.5;           % 时间步长 (小时), 默认30分钟
    cfg.T = 96;                  % 每回合步数 (48小时)

    % ---- 目标与安全区间 (合成仿真设定, 非农艺处方) ----
    cfg.targetWaterLevel = 40;   % 目标水深 (mm)
    cfg.bandLow = 20;            % 性能区间下限 (mm)
    cfg.bandHigh = 60;           % 性能区间上限 (mm)
    cfg.safetyLow = 0;           % 安全区间下限 (mm)
    cfg.safetyHigh = 100;        % 安全区间上限 (mm)

    % ---- 灌溉动作限制 ----
    cfg.actionMin = 0;           % 最小灌溉量 (mm/步)
    cfg.actionMax = 8;           % 最大灌溉量 (mm/步)

    % ---- 排水参数 ----
    cfg.maxDrainage = 50;        % 最大排水速率 (mm/步)

    % ---- 名义参数 (MPC使用, 与实际值可能不同) ----
    cfg.nominalInfiltration = 0.5;   % 名义入渗 (mm/步)
    cfg.nominalActuatorGain = 1.0;   % 名义执行器增益
    cfg.nominalDelay = 0;            % 名义延迟 (步)

    % ---- MPC参数 ----
    cfg.mpcHorizon = 12;          % 预测时域 (步)
    cfg.mpcStateWeight = 1.0;     % 状态跟踪权重
    cfg.mpcInputWeight = 0.1;     % 控制输入正则化权重
    cfg.mpcDeltaWeight = 0.05;    % 控制变化率正则化权重

    % ---- 规则控制器参数 ----
    cfg.ruleIrrigateOn = 25;      % 低于此观测值开始灌溉 (mm)
    cfg.ruleIrrigateOff = 45;     % 高于此观测值停止灌溉 (mm)
    cfg.ruleIrrigationRate = 4;   % 灌溉速率 (mm/步)
    cfg.ruleMinSteps = 3;         % 最小连续灌溉步数 (防抖振)

    % ---- 支持场景列表 ----
    cfg.scenarios = {'dry', 'intermittent_rain', 'heavy_rain', ...
                     'infiltration_shift', 'sensor_noise', 'actuator_delay'};
end
