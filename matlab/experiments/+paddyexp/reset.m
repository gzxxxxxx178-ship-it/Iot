% reset - 初始化回合状态
% 参数:
%   cfg - 配置结构体
%   seq - 外生序列结构体
% 返回:
%   state - 初始环境状态
%   obs   - 初始观测
% 说明:
%   初始水位设为 targetWaterLevel, 动作历史预分配为 cfg.T 长度。
%   初始观测不含噪声 (或可选用 seq.sensorNoise(1))。
function [state, obs] = reset(cfg, seq) %#ok<INUSD>
    state = struct();
    state.waterLevel = cfg.targetWaterLevel;
    state.prevAction = 0;
    state.actionHistory = zeros(cfg.T, 1);  % 预分配动作历史

    obs = struct();
    obs.waterLevel = state.waterLevel;  % 初始观测不含噪声
end
