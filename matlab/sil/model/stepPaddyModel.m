% stepPaddyModel - 执行一步稻田水量平衡与EC混合模型
% 参数:
%   state     - 当前状态结构体 (会被原地修改)
%   scenario  - 场景结构体, 包含扰动参数 multipliers
%   secsPerStep - 每步时长 (秒)
% 说明:
%   水量平衡公式: 水位(next) = 水位(current) + 入流/面积 + 降雨 - 入渗 - 蒸散 - 排水
%   1 L/m² = 1 mm (面积校正)
%   EC采用简化的混合/稀释动态，仅供演示性使用，不可用于施肥处方。
%   所有值钳制到Java合法范围: 水位0-500, 流量0-1000, EC 0-20, 含水率0-100, 降雨0-500
function state = stepPaddyModel(state, scenario, secsPerStep)
    hrsPerStep = secsPerStep / 3600.0;
    oldWaterLevelMm = state.waterLevelMm;
    oldPondingVolumeM3 = oldWaterLevelMm * 0.001 * state.areaM2;

    % ---- 降雨: 场景扰动乘子 + 随机扰动 ----
    baseRainMm = scenario.rainfallBaselineMm;
    randRain = randn() * scenario.rainfallNoise;
    state.rainfallMm = max(0.0, min(500.0, baseRainMm + randRain));

    % ---- 进水: 场景乘子影响流量 ----
    % 泵和阀门同时开启时产生进水, 否则为0
    % 阀门泄漏场景: 即使阀门"关闭", 仍有小流量泄漏
    if state.pumpOn && state.irrigationValveOpen
        state.flowRateLMin = state.defaultFlowRateLMin * scenario.flowMultiplier;
        % 加入扰动噪声
        state.flowRateLMin = state.flowRateLMin * (1.0 + 0.05 * randn());
    elseif isfield(scenario, 'leakFlowLMin') && scenario.leakFlowLMin > 0
        % 阀门泄漏: 关闭状态仍有残余流量
        state.flowRateLMin = scenario.leakFlowLMin * (1.0 + 0.1 * randn());
    else
        state.flowRateLMin = 0.0;
    end
    state.flowRateLMin = max(0.0, min(1000.0, state.flowRateLMin));

    % ---- 水量平衡计算 ----
    % 入流换算为水深: 1 L/m² = 1 mm => 入流(mm) = 流量(L/min) * 步长(min) / 面积(m²)
    inflowMm = state.flowRateLMin * (secsPerStep / 60.0) / state.areaM2;

    % 入渗损失 (mm/步)
    infiltrationMm = state.infiltrationMmHr * hrsPerStep;

    % 蒸散损失 (mm/步)
    etMm = state.etMmHr * hrsPerStep;

    % 排水损失 (与水位成正比)
    drainageMm = state.drainageCoeff * state.waterLevelMm * hrsPerStep;

    % 计算水位变化
    deltaMm = inflowMm + state.rainfallMm - infiltrationMm - etMm - drainageMm;
    state.waterLevelMm = state.waterLevelMm + deltaMm;

    % 钳制水位到合法范围
    state.waterLevelMm = max(0.0, min(500.0, state.waterLevelMm));

    % ---- EC混合/稀释动态 (简化演示模型) ----
    % 假设进水带有一定EC, 与田面水混合; 入渗和排水带走EC
    inflowVolumeM3 = state.flowRateLMin * (secsPerStep / 60.0) * 0.001;  % 进水体积 (m³)
    outflowVolumeM3 = (infiltrationMm + drainageMm) * 0.001 * state.areaM2;  % 出水体积 (m³)

    % EC质量平衡: 旧EC * 旧水量 + 新EC * 进水量 - 旧EC * 出水量
    oldEcMass = state.ecMsCm * oldPondingVolumeM3;
    ecInflowMass = state.ecInflowMsCm * scenario.ecMultiplier * inflowVolumeM3;
    ecOutflowMass = state.ecMsCm * outflowVolumeM3;

    % 最终水位已包含入流、降雨和各项损失，直接作为混合后的水量，避免重复计入进水。
    newVolume = state.waterLevelMm * 0.001 * state.areaM2;
    if newVolume > 1e-6
        state.ecMsCm = (oldEcMass + ecInflowMass - ecOutflowMass) / newVolume;
    else
        state.ecMsCm = state.ecBaselineMsCm;  % 接近干涸时取基线值
    end
    state.ecMsCm = max(0.0, min(20.0, state.ecMsCm));

    % ---- 土壤含水率: 受降雨、入渗和蒸散影响 ----
    % 简化模型: 降雨和入渗增加含水率, 蒸散降低含水率
    soilDelta = (state.rainfallMm + infiltrationMm) * 0.01 - etMm * 0.015;
    state.soilMoisturePct = state.soilMoisturePct + soilDelta;
    state.soilMoisturePct = max(0.0, min(100.0, state.soilMoisturePct));

    % ---- 执行器状态钳制 ----
    % 确保布尔状态合法; 场景可以覆盖执行器状态
end
