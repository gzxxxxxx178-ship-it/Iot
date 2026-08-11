% ruleController - 滞回规则灌溉控制器
% 参数:
%   cfg   - 配置结构体
%   obs   - 当前观测结构体 (含 waterLevel 字段)
%   state - 控制器内部状态 (或被初始化为空)
% 返回:
%   action - 灌溉量 (mm/步)
%   state  - 更新后的控制器状态
% 说明:
%   滞回逻辑: 观测水位 < cfg.ruleIrrigateOn → 开始灌溉;
%            观测水位 > cfg.ruleIrrigateOff → 停止灌溉。
%   包含最小灌溉步数 cfg.ruleMinSteps 以防止抖振。
%   不读取任何未来扰动数据, 仅使用当前观测。
function [action, state] = ruleController(cfg, obs, state)
    % 初始化控制器状态
    if nargin < 3 || isempty(state) || ~isfield(state, 'irrigating')
        state.irrigating = false;
        state.irrigationCounter = 0;
    end

    wl = obs.waterLevel;

    if state.irrigating
        state.irrigationCounter = state.irrigationCounter + 1;
        % 满足最小步数且水位高于关闭阈值时停止
        if state.irrigationCounter >= cfg.ruleMinSteps && wl > cfg.ruleIrrigateOff
            state.irrigating = false;
            state.irrigationCounter = 0;
        end
    else
        % 水位低于开启阈值时开始灌溉
        if wl < cfg.ruleIrrigateOn
            state.irrigating = true;
            state.irrigationCounter = 0;
        end
    end

    if state.irrigating
        action = cfg.ruleIrrigationRate;
    else
        action = 0;
    end
end
