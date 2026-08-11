% mpcController - 线性MPC控制器 (quadprog滚动时域QP)
% 参数:
%   cfg   - 配置结构体
%   obs   - 当前观测结构体
%   state - MPC内部状态 (含 prevAction, fallback 标志)
%   seq   - 预生成外生序列 (仅使用预测窗口内降雨和ET)
%   t     - 当前时间步
% 返回:
%   action - 灌溉量 (mm/步)
%   state  - 更新后的MPC状态
% 说明:
%   使用 cfg.mpcHorizon 步滚动时域, 对未知参数 (实际入渗、执行器增益、延迟)
%   只能使用名义值。仅读取 t:t+mpcHorizon-1 窗口内预生成的降雨和ET。
%   QP失败或输出非有限值时安全回退至规则动作, 记录 fallback 标志。
function [action, state] = mpcController(cfg, obs, state, seq, t)
    H = cfg.mpcHorizon;

    % ---- 初始化状态 ----
    if nargin < 3 || isempty(state)
        state = struct();
    end
    if ~isfield(state, 'prevAction')
        state.prevAction = 0;
    end

    % ---- 提取预测窗口内的降雨和蒸散 (仅使用预生成数据) ----
    predEnd = min(t + H - 1, cfg.T);
    predLen = predEnd - t + 1;
    if predLen < H
        rainfallPred = [seq.rainfall(t:predEnd); zeros(H - predLen, 1)];
        ETPred = [seq.ET(t:predEnd); zeros(H - predLen, 1)];
    else
        rainfallPred = seq.rainfall(t:t + H - 1);
        ETPred = seq.ET(t:t + H - 1);
    end

    % 扰动预测: 使用名义入渗, 不读取实际入渗
    dPred = rainfallPred - ETPred - cfg.nominalInfiltration;

    % ---- 当前观测 ----
    h0 = obs.waterLevel;
    uPrev = state.prevAction;

    % ---- 构建QP ----
    g = cfg.nominalActuatorGain;
    target = cfg.targetWaterLevel;

    % 下三角求和矩阵 S, 用于将控制序列累积为状态轨迹
    S = tril(ones(H));

    % 名义状态预测基线: x0 = h0*1_H + S*dPred
    x0 = h0 * ones(H, 1) + S * dPred;

    % 权重
    q = cfg.mpcStateWeight;
    r = cfg.mpcInputWeight;
    rDelta = cfg.mpcDeltaWeight;

    % 差分矩阵 D, 用于控制变化率惩罚
    % D * u = [u[0] - uPrev; u[1] - u[0]; ...; u[H-1] - u[H-2]]
    DMat = eye(H) - diag(ones(H - 1, 1), -1);

    % Hessian: 2 * (q*g^2*S'*S + r*I + rDelta*D'*D)
    H_qp = 2 * (q * g^2 * (S' * S) + r * eye(H) + rDelta * (DMat' * DMat));
    H_qp = (H_qp + H_qp') / 2;  % 确保对称性

    % 线性项: 2*q*g*S'*(x0 - target) - 2*rDelta*uPrev * DMat(1,:)'
    y = x0 - target * ones(H, 1);
    f_qp = 2 * q * g * (S' * y) - 2 * rDelta * uPrev * DMat(1, :)';

    % ---- 变量边界 ----
    lb = cfg.actionMin * ones(H, 1);
    ub = cfg.actionMax * ones(H, 1);

    % ---- 状态不等式约束: safetyLow <= x0 + g*S*u <= safetyHigh ----
    A_state = [g * S; -g * S];
    b_state = [cfg.safetyHigh * ones(H, 1) - x0; ...
               -(cfg.safetyLow * ones(H, 1) - x0)];

    % ---- 求解QP ----
    options = optimoptions('quadprog', 'Display', 'off', ...
                           'Algorithm', 'interior-point-convex');

    try
        [uOpt, ~, exitflag] = quadprog(H_qp, f_qp, A_state, b_state, ...
                                        [], [], lb, ub, [], options);

        if exitflag > 0 && all(isfinite(uOpt))
            action = uOpt(1);          % 仅应用序列第一个控制量
            state.fallback = false;
        else
            % QP数值失败, 安全回退
            action = fallbackAction(cfg, obs);
            state.fallback = true;
        end
    catch
        % QP异常, 安全回退
        action = fallbackAction(cfg, obs);
        state.fallback = true;
    end

    % 裁剪到允许范围
    action = max(cfg.actionMin, min(cfg.actionMax, action));

    % 更新状态
    state.prevAction = action;
end

% fallbackAction - QP失败时的安全回退动作
% 使用简单阈值规则, 确保不中断实验批处理
function action = fallbackAction(cfg, obs)
    if obs.waterLevel < cfg.ruleIrrigateOn
        action = cfg.ruleIrrigationRate;
    elseif obs.waterLevel > cfg.ruleIrrigateOff
        action = 0;
    else
        % 中间状态: 保守的小灌溉量
        action = cfg.ruleIrrigationRate * 0.5;
    end
end
