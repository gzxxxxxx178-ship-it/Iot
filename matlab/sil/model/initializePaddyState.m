% initializePaddyState - 初始化红壤丘陵稻田状态
% 参数:
%   deviceId - 设备标识字符串
% 返回:
%   state - 包含传感器值与模型参数的初始状态结构体
% 说明:
%   初始化水位、流量、EC、土壤含水率、降雨为合理初值，
%   同时设置稻田面积、入渗率、蒸散率、排水系数等模型参数。
%   所有执行器初始为关闭状态。
function state = initializePaddyState(deviceId)
    state = struct();

    % 设备标识
    state.deviceId = deviceId;

    % ---- 传感器值初值 ----
    state.waterLevelMm = 40.0;        % 水位 (mm), 范围 0-500
    state.flowRateLMin = 0.0;         % 进水流量 (L/min), 范围 0-1000
    state.ecMsCm = 0.80;              % 电导率 (mS/cm), 范围 0-20
    state.soilMoisturePct = 75.0;     % 土壤含水率 (%), 范围 0-100
    state.rainfallMm = 0.0;           % 降雨量 (mm), 范围 0-500

    % ---- 执行器状态 ----
    state.pumpOn = false;              % 水泵运行标志
    state.irrigationValveOpen = false; % 灌溉阀门开启标志
    state.fertilizerPumpOn = false;   % 施肥泵运行标志
    state.irrigationStopLatched = false; % 后端停止灌溉命令的会话内锁存
    state.fertilizerStopLatched = false; % 后端停止施肥命令的会话内锁存

    % ---- 模型参数 ----
    state.areaM2 = 100.0;              % 稻田面积 (m²)
    state.infiltrationMmHr = 2.0;      % 入渗速率 (mm/hr), 演示用可配置假设
    state.etMmHr = 3.0;                % 蒸散速率 (mm/hr), 演示用可配置假设
    state.drainageCoeff = 0.02;        % 排水系数 (hr⁻¹), 与当前水位成正比
    state.defaultFlowRateLMin = 80.0;  % 默认进水流量 (L/min)
    state.ecBaselineMsCm = 0.80;       % 基础EC值 (mS/cm)
    state.ecInflowMsCm = 1.20;         % 进水EC值 (mS/cm), 用于混合计算
end
