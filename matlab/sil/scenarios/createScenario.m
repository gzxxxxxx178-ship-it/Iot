% createScenario - 创建指定的仿真场景
% 参数:
%   scenarioName - 场景名称字符串, 支持:
%     'normal'       - 正常灌溉, 泵阀按正常周期运行, 无极端天气
%     'low_water'    - 低水位, 进水流量减少, 高蒸散, 无降雨
%     'high_water'   - 高水位, 高进水流量 + 持续降雨
%     'high_ec'      - 高电导率, 进水EC异常升高
%     'blocked_flow' - 堵塞水流, 进水流量降为零
%     'valve_leak'   - 阀门泄漏, 关闭状态下仍有小流量进水
% 返回:
%   scenario - 场景结构体, 包含扰动乘子与随机噪声参数
% 说明:
%   场景仅改变扰动和模拟传感值, Java规则不读取场景名判断报警。
%   所有场景参数仅为仿真扰动因子，不代表田间实测数据。
function scenario = createScenario(scenarioName)
    % 场景基础参数模板
    scenario = struct();
    scenario.name = scenarioName;
    scenario.flowMultiplier = 1.0;
    scenario.rainfallBaselineMm = 0.0;
    scenario.rainfallNoise = 0.5;
    scenario.ecMultiplier = 1.0;
    scenario.pumpControl = 'auto';       % 'auto'=周期启停, 'always_on', 'always_off'
    scenario.valveControl = 'auto';
    scenario.fertilizerControl = 'auto';
    scenario.initialWaterLevelMm = 40.0;
    scenario.initialEcMsCm = 0.80;
    scenario.onSteps = [1, 5];           % auto模式: 每周期中泵/阀开启的步数范围
    scenario.offSteps = [5, 10];         % auto模式: 关闭步数范围

    switch lower(scenarioName)
        case 'normal'
            % 正常灌溉: 泵阀正常周期, 偶发小雨, 标准EC
            scenario.flowMultiplier = 1.0;
            scenario.rainfallBaselineMm = 0.0;
            scenario.rainfallNoise = 1.0;
            scenario.ecMultiplier = 1.0;

        case 'low_water'
            % 低水位: 流量减半, 高蒸散, 无降雨
            scenario.flowMultiplier = 0.5;
            scenario.rainfallBaselineMm = 0.0;
            scenario.rainfallNoise = 0.0;
            scenario.ecMultiplier = 1.0;
            % 减少开启步数, 增加关闭步数
            scenario.onSteps = [1, 2];
            scenario.offSteps = [10, 15];
            scenario.initialWaterLevelMm = 8.0;

        case 'high_water'
            % 高水位故障注入: 从越限水位启动并叠加合成强降雨脉冲
            scenario.flowMultiplier = 1.8;
            scenario.rainfallBaselineMm = 8.0;
            scenario.rainfallNoise = 3.0;
            scenario.ecMultiplier = 1.0;
            scenario.initialWaterLevelMm = 260.0;

        case 'high_ec'
            % 高EC故障注入: 从越限EC启动并保持进水
            scenario.flowMultiplier = 1.0;
            scenario.rainfallBaselineMm = 0.0;
            scenario.rainfallNoise = 0.5;
            scenario.ecMultiplier = 5.0;  % 仅放大进水EC，不逐步倍增田面水EC
            scenario.initialEcMsCm = 3.5;
            scenario.pumpControl = 'always_on';
            scenario.valveControl = 'always_on';
            scenario.fertilizerControl = 'always_on';

        case 'blocked_flow'
            % 堵塞水流: 泵阀开启但流量降为接近零
            scenario.flowMultiplier = 0.02;
            scenario.rainfallBaselineMm = 0.0;
            scenario.rainfallNoise = 0.2;
            scenario.ecMultiplier = 1.0;

        case 'valve_leak'
            % 阀门泄漏: 关闭时仍有微小的残余流量
            scenario.flowMultiplier = 1.0;
            scenario.rainfallBaselineMm = 0.0;
            scenario.rainfallNoise = 0.5;
            scenario.ecMultiplier = 1.0;
            scenario.leakFlowLMin = 5.0;  % 泄漏流量 (L/min)

        otherwise
            error('createScenario:UnsupportedScenario', ...
                  '不支持场景: %s。支持: normal, low_water, high_water, high_ec, blocked_flow, valve_leak', ...
                  scenarioName);
    end
end
