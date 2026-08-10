% runSil - MATLAB软件在环仿真主循环
% 参数:
%   config       - 仿真配置 (由createSilConfig创建)
%   scenarioName - 场景名称 (如 'normal')
% 返回:
%   results - 结果结构体, 包含:
%     sessionId      - 会话标识符
%     config         - 使用的配置
%     records        - 每步仿真记录的结构体数组
%     commandHistory - 执行的命令历史
%     summary        - 汇总统计
% 说明:
%   1. 设置固定随机种子, 确保结果可复现
%   2. 初始化稻田状态
%   3. 逐步模拟: 生成扰动 -> 调用模型 -> 构建遥测 -> 上传/记录
%   4. 联网模式下, 每步轮询Java命令并执行STOP_IRRIGATION/STOP_FERTILIZER/STOP_ALL
%   5. dryRun模式不联网, 仅在MATLAB工作区记录payload与状态
%   6. 同一命令ID在当前会话内仅反馈一次
function results = runSil(config, scenarioName)
    % ---- 固定随机种子, 确保可复现 ----
    rng(config.randomSeed);

    % ---- 生成会话标识 ----
    sessionUuid = char(java.util.UUID.randomUUID());
    sessionId = ['SIL-', sessionUuid(1:12)];
    sampleCounter = 0;

    % ---- 初始化状态与场景 ----
    state = initializePaddyState(config.deviceId);
    scenario = createScenario(scenarioName);
    state.waterLevelMm = scenario.initialWaterLevelMm;
    state.ecMsCm = scenario.initialEcMsCm;

    % ---- 命令去重记录 ----
    fedBackCommandIds = containers.Map('KeyType', 'char', 'ValueType', 'logical');

    % ---- 泵/阀周期控制计数器 ----
    cycleCounter = 0;
    cycleOnDuration = 0;
    cycleOffDuration = 0;
    cycleOn = false;
    [cycleOnDuration, cycleOffDuration] = pickCycleDuration(scenario);

    % ---- 结果存储 ----
    records = struct('stepNum', {}, 'timestamp', {}, 'waterLevelMm', {}, ...
                     'flowRateLMin', {}, 'ecMsCm', {}, 'soilMoisturePct', {}, ...
                     'rainfallMm', {}, 'pumpOn', {}, 'irrigationValveOpen', {}, ...
                     'fertilizerPumpOn', {}, 'uploaded', {});

    fprintf('=== MATLAB SIL 仿真开始 ===\n');
    fprintf('会话: %s, 设备: %s, 场景: %s, 步数: %d\n', ...
            sessionId, config.deviceId, scenarioName, config.steps);
    if config.dryRun
        fprintf('[DRY-RUN] 不发送网络请求, 仅记录payload\n');
    end

    % ---- 主仿真循环 ----
    for stepNum = 1:config.steps
        % ---- 泵/阀周期控制 (auto模式) ----
        cycleCounter = cycleCounter + 1;
        if cycleOn && cycleCounter > cycleOnDuration
            cycleOn = false;
            cycleCounter = 0;
            [cycleOnDuration, cycleOffDuration] = pickCycleDuration(scenario);
        elseif ~cycleOn && cycleCounter > cycleOffDuration
            cycleOn = true;
            cycleCounter = 0;
            [cycleOnDuration, cycleOffDuration] = pickCycleDuration(scenario);
        end

        % 根据场景控制模式设置执行器
        switch scenario.pumpControl
            case 'always_on'
                state.pumpOn = true;
            case 'always_off'
                state.pumpOn = false;
            otherwise  % 'auto'
                state.pumpOn = cycleOn;
        end
        switch scenario.valveControl
            case 'always_on'
                state.irrigationValveOpen = true;
            case 'always_off'
                state.irrigationValveOpen = false;
            otherwise  % 'auto'
                state.irrigationValveOpen = cycleOn;
        end
        switch scenario.fertilizerControl
            case 'always_on'
                state.fertilizerPumpOn = true;
            case 'always_off'
                state.fertilizerPumpOn = false;
            otherwise
                state.fertilizerPumpOn = cycleOn;
        end

        % 后端停止命令在本次会话内锁存，防止下一步被场景周期控制重新开启。
        if state.irrigationStopLatched
            state.pumpOn = false;
            state.irrigationValveOpen = false;
        end
        if state.fertilizerStopLatched
            state.fertilizerPumpOn = false;
        end

        % ---- 执行一步模型 ----
        state = stepPaddyModel(state, scenario, config.stepSeconds);

        % ---- 构建遥测payload ----
        sampleCounter = sampleCounter + 1;
        sampleId = [sessionId, '-', sprintf('%05d', sampleCounter)];
        payload = buildTelemetryPayload(sampleId, config.deviceId, stepNum, scenario, state);

        % ---- 上传遥测 ----
        uploaded = false;
        if ~config.dryRun
            [ok, ~] = postTelemetry(config, payload);
            uploaded = ok;
        end

        % ---- 记录当前步结果 ----
        records(end+1) = struct('stepNum', stepNum, ...
                                'timestamp', payload.occurredAt, ...
                                'waterLevelMm', state.waterLevelMm, ...
                                'flowRateLMin', state.flowRateLMin, ...
                                'ecMsCm', state.ecMsCm, ...
                                'soilMoisturePct', state.soilMoisturePct, ...
                                'rainfallMm', state.rainfallMm, ...
                                'pumpOn', state.pumpOn, ...
                                'irrigationValveOpen', state.irrigationValveOpen, ...
                                'fertilizerPumpOn', state.fertilizerPumpOn, ...
                                'uploaded', uploaded);

        % ---- 轮询待处理命令 ----
        cmdOk = false;
        pendingCommands = [];
        if ~config.dryRun
            [cmdOk, pendingCommands] = getPendingCommands(config, config.deviceId);
        end
        if cmdOk && ~isempty(pendingCommands)
            for ci = 1:numel(pendingCommands)
                cmd = pendingCommands(ci);
                cmdId = cmd.id;
                cmdKey = num2str(cmdId, '%.0f');
                if isfield(cmd, 'action')
                    action = cmd.action;
                else
                    action = '';
                end

                % 命令去重: 同一会话内仅反馈一次
                if isKey(fedBackCommandIds, cmdKey)
                    continue;
                end

                % 执行命令并反馈
                [state, execStatus, execMsg] = applyCommand(action, state);
                [~, ~] = postCommandFeedback(config, cmdId, execStatus, execMsg);
                fedBackCommandIds(cmdKey) = true;
            end
        end

        % ---- 实时暂停 ----
        if config.realtimePauseSeconds > 0
            pause(config.realtimePauseSeconds);
        end
    end

    fprintf('=== 仿真完成: %d步 ===\n', config.steps);
    fprintf('遥测上传: %d条, 命令反馈: %d条\n', ...
            sum([records.uploaded]), fedBackCommandIds.Count);

    % ---- 组装返回结果 ----
    results = struct();
    results.sessionId = sessionId;
    results.config = config;
    results.records = records;
    results.commandHistory = fedBackCommandIds;
    results.summary = struct(...
        'totalSteps', config.steps, ...
        'uploadsAttempted', sum([records.uploaded]), ...
        'commandsFedBack', fedBackCommandIds.Count, ...
        'finalWaterLevelMm', state.waterLevelMm, ...
        'finalEcMsCm', state.ecMsCm, ...
        'finalSoilMoisturePct', state.soilMoisturePct ...
    );
end

% applyCommand - 应用从Java获取的命令到仿真状态
% 仅支持三种动作: STOP_IRRIGATION, STOP_FERTILIZER, STOP_ALL
% 返回: [state, status ('SUCCESS'|'FAILED'), message]
function [state, status, msg] = applyCommand(action, state)
    switch action
        case 'STOP_IRRIGATION'
            state.pumpOn = false;
            state.irrigationValveOpen = false;
            state.irrigationStopLatched = true;
            status = 'SUCCESS';
            msg = '已停止灌溉 (泵与阀关闭)';
        case 'STOP_FERTILIZER'
            state.fertilizerPumpOn = false;
            state.fertilizerStopLatched = true;
            status = 'SUCCESS';
            msg = '已停止施肥';
        case 'STOP_ALL'
            state.pumpOn = false;
            state.irrigationValveOpen = false;
            state.fertilizerPumpOn = false;
            state.irrigationStopLatched = true;
            state.fertilizerStopLatched = true;
            status = 'SUCCESS';
            msg = '已停止全部执行器';
        otherwise
            status = 'FAILED';
            msg = ['未知动作: ', action];
    end
end

% pickCycleDuration - 从场景指定的范围随机选择开关持续时间
function [onDur, offDur] = pickCycleDuration(scenario)
    onDur = scenario.onSteps(1) + randi([0, max(0, scenario.onSteps(2) - scenario.onSteps(1))]);
    offDur = scenario.offSteps(1) + randi([0, max(0, scenario.offSteps(2) - scenario.offSteps(1))]);
end
