% buildResidualObservation - 构建6×1归一化RL观测向量
% 参数:
%   obsWaterLevel  - 当前含噪声观测水位 (标量, mm)
%   prevFinalAction - 上一最终实际指令 (标量, mm/步)
%   mpcBaseAction   - 当前MPC基础动作 (标量, mm/步)
%   rainfall        - 当前步降雨量 (标量, mm/步)
%   ET              - 当前步蒸散量 (标量, mm/步)
%   prevResidual    - 上一残差 (标量, mm/步)
%   rlCfg           - RL配置结构体
% 返回:
%   obsVec - 6×1归一化double列向量
% 说明:
%   所有分量经明确归一化和钳制, 不包含 actualInfiltration、actuatorGain、
%   delaySteps 或真实水位等不可观测真值。
%   观测分量定义:
%     [1] 归一化水位误差: (obs_wl - target) / target, 钳制 [-2, 2]
%     [2] 归一化上一最终动作: prevFinalAction / actionMax, 钳制 [0, 1]
%     [3] 归一化MPC基础动作: mpcBaseAction / actionMax, 钳制 [0, 1]
%     [4] 归一化降雨: rainfall / rainfallScale, 钳制 [0, 1]
%     [5] 归一化蒸散: ET / ETScale, 钳制 [0, 1]
%     [6] 归一化上一残差: prevResidual / maxResidual, 钳制 [-1, 1]
function obsVec = buildResidualObservation(obsWaterLevel, prevFinalAction, ...
        mpcBaseAction, rainfall, ET, prevResidual, rlCfg)
    % 1. 归一化水位误差
    wlError = (obsWaterLevel - rlCfg.targetWaterLevel) / rlCfg.obsNorm.waterLevelError;
    wlError = max(rlCfg.obsClamp.waterLevelError(1), ...
                  min(rlCfg.obsClamp.waterLevelError(2), wlError));

    % 2. 归一化上一最终动作
    normPrevAction = prevFinalAction / rlCfg.obsNorm.action;
    normPrevAction = max(rlCfg.obsClamp.action(1), ...
                         min(rlCfg.obsClamp.action(2), normPrevAction));

    % 3. 归一化MPC基础动作
    normMpcAction = mpcBaseAction / rlCfg.obsNorm.action;
    normMpcAction = max(rlCfg.obsClamp.action(1), ...
                        min(rlCfg.obsClamp.action(2), normMpcAction));

    % 4. 归一化降雨
    normRain = rainfall / rlCfg.obsNorm.rainfall;
    normRain = max(rlCfg.obsClamp.rainfall(1), ...
                   min(rlCfg.obsClamp.rainfall(2), normRain));

    % 5. 归一化蒸散
    normET = ET / rlCfg.obsNorm.ET;
    normET = max(rlCfg.obsClamp.ET(1), ...
                 min(rlCfg.obsClamp.ET(2), normET));

    % 6. 归一化上一残差
    normResidual = prevResidual / rlCfg.obsNorm.residual;
    normResidual = max(rlCfg.obsClamp.residual(1), ...
                       min(rlCfg.obsClamp.residual(2), normResidual));

    % 组装6×1列向量
    obsVec = [wlError; normPrevAction; normMpcAction; normRain; normET; normResidual];

    % 最终防御: 确保无非有限值
    obsVec(~isfinite(obsVec)) = 0;
end
