% createResidualRlEnvironment - 创建残差SAC强化学习环境 (rlFunctionEnv)
% 参数:
%   rlCfg - RL配置结构体 (来自 residualRlConfig)
% 返回:
%   env   - rlFunctionEnv 对象
% 说明:
%   环境包装 paddyexp 动力学: RL输出残差, 与MPC基础动作相加,
%   经安全屏蔽后执行。MPC的 prevAction 始终更新为最终实际指令。
%   reset函数按确定性轮转在训练seed和6个场景中取样。
%
%   观测一致性: 安全屏蔽使用的 observedWaterLevel 与 agent 当前6维观测
%   中的水位来源一致。reset时取自 paddyexp.reset 返回的 obs.waterLevel,
%   每步执行后更新为 stepDynamics 返回的 obs.waterLevel (用于下一步)。
%   时序: agent obs -- 屏蔽 -- stepDynamics -- 更新 obs -- 下一步。
function env = createResidualRlEnvironment(rlCfg)
    % 观测: 6×1 double 向量
    obsInfo = rlNumericSpec([6 1]);
    obsInfo.Name = 'residualSAC_Observation';

    % 动作: 1×1 连续残差, 范围 [-2, 2]
    actInfo = rlNumericSpec([1 1], ...
        'LowerLimit', rlCfg.residualRange(1), ...
        'UpperLimit', rlCfg.residualRange(2));
    actInfo.Name = 'residualSAC_Action';

    % 构建组合列表 (用于reset轮转)
    scenarios = rlCfg.scenarios;
    nScenarios = length(scenarios);
    seeds = rlCfg.trainingSeeds;
    nSeeds = length(seeds);
    nCombos = nScenarios * nSeeds;
    comboList = zeros(nCombos, 3);  % [scenarioIdx, seed, 占位]
    idx = 1;
    for iSeed = 1:nSeeds
        for iSc = 1:nScenarios
            comboList(idx, :) = [iSc, seeds(iSeed), 0];
            idx = idx + 1;
        end
    end

    % 预分配空动作记录模板 (用于 actionHistory 预分配)
    emptyActionRecord = struct('t', 0, 'action', 0, 'proposedAction', 0, ...
        'residual', 0, 'mpcBaseAction', 0, 'shieldIntervened', false, ...
        'shieldReason', '', 'decisionTimeMs', 0);

    % 回合计数器 (外层作用域, 嵌套resetFcn捕获并更新;
    % 每次调用 createResidualRlEnvironment 创建新作用域, 从0开始)
    episodeCounter = 0;

    % ---- Reset函数 ----
    function [initialObs, loggedSig] = resetFcn()
        episodeCounter = episodeCounter + 1;
        if episodeCounter > nCombos
            episodeCounter = 1;  % 循环
        end

        % 确定本轮 scenario 和 seed
        scIdx = comboList(episodeCounter, 1);
        seed = comboList(episodeCounter, 2);
        scenario = scenarios{scIdx};

        % 生成外生序列
        seq = paddyexp.generateEpisode(rlCfg, scenario, seed);

        % 初始化环境和MPC状态
        [envState, obs] = paddyexp.reset(rlCfg, seq);
        mpcState = struct();

        % 计算初始MPC基础动作 (t=1)
        [mpcBaseAction, mpcState] = paddyexp.mpcController(rlCfg, obs, mpcState, seq, 1);
        % 初始时 prevAction 尚无意义, 但强制为0以保持一致
        mpcState.prevAction = 0;

        % 构建初始RL观测 (使用paddyexp.reset返回的obs.waterLevel, 无噪声)
        initialObs = buildResidualObservation(obs.waterLevel, 0, ...
            mpcBaseAction, seq.rainfall(1), seq.ET(1), 0, rlCfg);

        % 打包 LoggedSignals (在step间传递的状态)
        loggedSig = struct();
        loggedSig.seq = seq;
        loggedSig.scenario = scenario;
        loggedSig.seed = seed;
        loggedSig.envState = envState;
        loggedSig.mpcState = mpcState;
        loggedSig.t = 0;               % 当前时间步 (step内递增)
        loggedSig.prevFinalAction = 0; % 上一最终指令
        loggedSig.prevResidual = 0;    % 上一残差
        loggedSig.mpcBaseAction = mpcBaseAction;  % 当前步MPC基础动作
        loggedSig.shieldCount = 0;     % 屏蔽干预累计
        loggedSig.absResidualSum = 0;  % |残差|累计
        % 当前观测水位: 与agent当前obs同源, reset时为paddyexp.reset返回值
        loggedSig.observedWaterLevel = obs.waterLevel;
        % 预分配记录结构体 (避免空double[]与struct串联类型错误)
        loggedSig.actionHistory = repmat(emptyActionRecord, rlCfg.T, 1);
        loggedSig.decisionTimeMs = zeros(rlCfg.T, 1);
    end

    % ---- Step函数 ----
    function [nextObs, reward, isDone, loggedSig] = stepFcn(residual, loggedSig)
        % 解包状态
        seq = loggedSig.seq;
        envState = loggedSig.envState;
        mpcState = loggedSig.mpcState;
        t = loggedSig.t + 1;  % 进入当前步
        prevFinalAction = loggedSig.prevFinalAction;
        mpcBaseAction = loggedSig.mpcBaseAction;
        prevResidual = loggedSig.prevResidual;
        % 当前观测水位: 与agent当前6维观测中水位误差同源
        % (reset时为paddyexp.reset返回值,
        %  后续step为上一stepDynamics返回的obs.waterLevel)
        obsWaterLevel = loggedSig.observedWaterLevel;

        % ---- 1. 钳制残差到允许范围 ----
        residual = double(residual);
        residual = max(rlCfg.residualRange(1), ...
                       min(rlCfg.residualRange(2), residual));

        % ---- 2. 提议动作 = MPC基础 + 残差 ----
        proposedAction = mpcBaseAction + residual;

        % ---- 3. 安全屏蔽 (使用与agent观测同源的obsWaterLevel) ----
        ticDecision = tic;
        [finalAction, shieldIntervened, shieldReason] = applySafetyShield(...
            rlCfg, proposedAction, obsWaterLevel, prevFinalAction);
        dtMs = toc(ticDecision) * 1000;

        % ---- 4. 更新MPC prevAction 为最终实际指令 ----
        mpcState.prevAction = finalAction;

        % ---- 5. 执行动力学 ----
        [envState, obs, info] = paddyexp.stepDynamics(rlCfg, envState, finalAction, seq, t);

        % ---- 6. 累计指标 ----
        shieldCount = loggedSig.shieldCount;
        if shieldIntervened
            shieldCount = shieldCount + 1;
        end
        absResidualSum = loggedSig.absResidualSum + abs(residual);

        % ---- 7. 计算奖励 ----
        reward = computeReward(rlCfg, info.trueWaterLevel, finalAction, ...
            prevFinalAction, shieldIntervened);

        % ---- 8. 判断终止 ----
        isDone = (t >= rlCfg.T);

        % ---- 9. 记录时序 (预分配索引赋值, 避免类型串联错误) ----
        stepRecord = struct();
        stepRecord.t = t;
        stepRecord.action = finalAction;
        stepRecord.proposedAction = proposedAction;
        stepRecord.residual = residual;
        stepRecord.mpcBaseAction = mpcBaseAction;
        stepRecord.shieldIntervened = shieldIntervened;
        stepRecord.shieldReason = shieldReason;
        stepRecord.decisionTimeMs = dtMs;

        actionHistory = loggedSig.actionHistory;
        actionHistory(t) = stepRecord;
        decisionTimeMs = loggedSig.decisionTimeMs;
        decisionTimeMs(t) = dtMs;

        % ---- 10. 构建下一步观测 ----
        % 下一步观测使用 stepDynamics 返回的 obs.waterLevel (含噪声)
        % 与安全屏蔽的时序: agent obs → 屏蔽 → stepDynamics → 更新obs → 下一步
        if ~isDone
            % 计算下一步MPC基础动作
            [nextMpcAction, mpcState] = paddyexp.mpcController(rlCfg, obs, mpcState, seq, t + 1);
            % 再次确保 MPC prevAction 指向最终实际动作
            mpcState.prevAction = finalAction;

            nextObs = buildResidualObservation(obs.waterLevel, finalAction, ...
                nextMpcAction, seq.rainfall(min(t + 1, end)), ...
                seq.ET(min(t + 1, end)), residual, rlCfg);
        else
            % 终止步: 观测无意义但必须为非空有限向量
            nextObs = zeros(6, 1);
            nextMpcAction = 0; %#ok<NASGU>
        end

        % ---- 11. 更新 LoggedSignals ----
        loggedSig.seq = seq;
        loggedSig.envState = envState;
        loggedSig.mpcState = mpcState;
        loggedSig.t = t;
        loggedSig.prevFinalAction = finalAction;
        loggedSig.prevResidual = residual;
        loggedSig.mpcBaseAction = nextMpcAction;
        loggedSig.shieldCount = shieldCount;
        loggedSig.absResidualSum = absResidualSum;
        % 更新当前观测水位: 使用stepDynamics返回的obs.waterLevel,
        % 作为下一步agent观测和屏蔽判断的水位来源
        loggedSig.observedWaterLevel = obs.waterLevel;
        loggedSig.actionHistory = actionHistory;
        loggedSig.decisionTimeMs = decisionTimeMs;
    end

    % 创建 rlFunctionEnv (R2025a API: 位置参数, 不使用命名参数)
    env = rlFunctionEnv(obsInfo, actInfo, @stepFcn, @resetFcn);
end

% computeReward - 计算单步奖励 (负加权和)
% 参数:
%   rlCfg            - RL配置
%   trueWaterLevel   - 真实水位 (mm)
%   action           - 最终灌溉量 (mm/步)
%   prevAction       - 上一动作 (mm/步)
%   shieldIntervened - 安全屏蔽是否干预
% 返回:
%   reward - 标量奖励值
% 说明:
%   奖励方程 (负加权和, 最大化奖励 = 最小化代价):
%     r = -( w1 * ((h - target)/target)^2           [跟踪误差]
%          + w2 * (action / actionMax)               [灌溉代价]
%          + w3 * |action - prevAction| / actionMax  [动作平滑]
%          + w4 * bandViolationFlag                  [性能越界]
%          + w5 * safetyViolationFlag                [安全越界]
%          + w6 * shieldInterventionFlag )           [屏蔽依赖]
function r = computeReward(rlCfg, trueWaterLevel, action, prevAction, shieldIntervened)
    w = rlCfg.rewardWeights;

    % 水位跟踪误差 (归一化)
    trackingErr = ((trueWaterLevel - rlCfg.targetWaterLevel) / rlCfg.targetWaterLevel)^2;

    % 灌溉代价 (归一化)
    irrigationCost = action / rlCfg.actionMax;

    % 动作平滑代价 (归一化)
    actionChangeCost = abs(action - prevAction) / rlCfg.actionMax;

    % 性能区间越界
    bandViolation = (trueWaterLevel < rlCfg.bandLow) || (trueWaterLevel > rlCfg.bandHigh);

    % 安全区间越界
    safetyViolation = (trueWaterLevel < rlCfg.safetyLow) || (trueWaterLevel > rlCfg.safetyHigh);

    % 屏蔽干预
    shieldFlag = double(shieldIntervened);

    % 负加权和
    r = -(w.tracking * trackingErr ...
       + w.irrigation * irrigationCost ...
       + w.actionChange * actionChangeCost ...
       + w.bandViolation * bandViolation ...
       + w.safetyViolation * safetyViolation ...
       + w.shieldIntervention * shieldFlag);
end
