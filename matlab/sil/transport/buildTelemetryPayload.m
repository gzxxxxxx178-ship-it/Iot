% buildTelemetryPayload - 从当前状态构建遥测上传payload结构体
% 参数:
%   sampleId    - 样本唯一ID (本次仿真会话内自增)
%   deviceId    - 设备标识
%   stepNum     - 当前步数序号
%   scenario    - 当前场景结构体
%   state       - 当前稻田状态结构体
% 返回:
%   payload - 与Java TelemetryRequest匹配的结构体,
%             包含: sampleId, deviceId, occurredAt, growthStage,
%                   scenarioCode, waterLevelMm, flowRateLMin, ecMsCm,
%                   soilMoisturePct, rainfallMm, pumpOn,
%                   irrigationValveOpen, fertilizerPumpOn
% 说明:
%   occurredAt使用UTC ISO-8601带'Z'后缀。
%   不包含ownerUsername、sourceType、alarm字段，
%   这些字段由Java后端服务端侧注入。
function payload = buildTelemetryPayload(sampleId, deviceId, stepNum, scenario, state)
    % 生成UTC ISO-8601时间戳
    utcNow = datetime('now', 'TimeZone', 'UTC');
    occurredAtStr = [datestr(utcNow, 'yyyy-mm-ddTHH:MM:SS'), 'Z'];

    % 根据步数确定生长阶段 (简化阶段性划分)
    growthStage = determineGrowthStage(stepNum);

    % 组装payload结构体
    payload = struct();
    payload.sampleId = sampleId;
    payload.deviceId = deviceId;
    payload.occurredAt = occurredAtStr;
    payload.growthStage = growthStage;
    payload.scenarioCode = scenario.name;
    payload.waterLevelMm = state.waterLevelMm;
    payload.flowRateLMin = state.flowRateLMin;
    payload.ecMsCm = state.ecMsCm;
    payload.soilMoisturePct = state.soilMoisturePct;
    payload.rainfallMm = state.rainfallMm;
    payload.pumpOn = state.pumpOn;
    payload.irrigationValveOpen = state.irrigationValveOpen;
    payload.fertilizerPumpOn = state.fertilizerPumpOn;
end

% determineGrowthStage - 根据仿真步数判定生长阶段 (辅助函数)
function stage = determineGrowthStage(stepNum)
    % 简化120步对应约10分钟仿真, 划分4个阶段用于展示
    if stepNum <= 30
        stage = 'tillering';       % 分蘖期
    elseif stepNum <= 60
        stage = 'booting';         % 孕穗期
    elseif stepNum <= 90
        stage = 'heading';         % 抽穗期
    else
        stage = 'grain_filling';   % 灌浆期
    end
end
