% generateEpisode - 为指定场景和种子预生成外生序列
% 参数:
%   cfg      - 由 paddyexp.config() 返回的配置结构体
%   scenario - 场景名称 (dry|intermittent_rain|heavy_rain|infiltration_shift|sensor_noise|actuator_delay)
%   seed     - 随机种子 (整数)
% 返回:
%   seq - 包含以下字段的结构体:
%     rainfall, ET, actualInfiltration, sensorNoise, actuatorGain (均为 cfg.T×1 列向量)
%     delaySteps (标量, 每回合恒定纯延迟步数)
% 说明:
%   所有序列在生成后固定, 控制器不得改变随机数消耗次序。
%   相同 (scenario, seed) 多次调用返回完全一致的序列。
%   ET、actualInfiltration、actuatorGain 均经物理非负钳制。
%   delaySteps 为每回合恒定的纯延迟步数 (标量), 非逐步变化。
%   所有场景参数为合成设定, 不代表实际天气频率。
function seq = generateEpisode(cfg, scenario, seed)
    % 固定随机数生成器以确保可复现
    rng(seed, 'twister');
    T = cfg.T;

    % 初始化输出结构体
    seq = struct();
    seq.scenario = scenario;
    seq.seed = seed;
    seq.T = T;
    seq.rainfall = zeros(T, 1);
    seq.ET = zeros(T, 1);
    seq.actualInfiltration = zeros(T, 1);
    seq.sensorNoise = zeros(T, 1);
    seq.actuatorGain = zeros(T, 1);
    seq.delaySteps = 0;  % 标量, 每回合恒定

    % 根据场景生成扰动序列 (合成压力测试, 非实际天气频率)
    switch lower(scenario)
        case 'dry'
            % 干旱场景: 极少降雨, 高蒸散, 考验持续灌溉能力
            seq.rainfall = max(0, 0.1 * randn(T, 1));
            seq.ET = 0.55 + 0.05 * randn(T, 1);
            seq.actualInfiltration = 0.3 + 0.02 * randn(T, 1);
            seq.sensorNoise = 0.5 * randn(T, 1);
            seq.actuatorGain = 0.95 + 0.1 * rand(T, 1);
            seq.delaySteps = 1;

        case 'intermittent_rain'
            % 间歇降雨场景: 随机降雨脉冲, 考验响应能力
            rainMask = rand(T, 1) < 0.35;
            seq.rainfall = rainMask .* max(0, 3 + 2 * randn(T, 1));
            seq.ET = 0.4 + 0.1 * randn(T, 1);
            seq.actualInfiltration = 0.5 + 0.05 * randn(T, 1);
            seq.sensorNoise = 1.0 * randn(T, 1);
            seq.actuatorGain = 0.98 + 0.04 * randn(T, 1);
            seq.delaySteps = 0;

        case 'heavy_rain'
            % 暴雨场景: 偶发强降雨, 考验溢流/安全处理
            rainMask = rand(T, 1) < 0.15;
            seq.rainfall = rainMask .* max(0, 8 + 4 * randn(T, 1));
            seq.ET = 0.3 + 0.1 * randn(T, 1);
            seq.actualInfiltration = 0.5 + 0.03 * randn(T, 1);
            seq.sensorNoise = 0.5 * randn(T, 1);
            seq.actuatorGain = 0.98 + 0.04 * randn(T, 1);
            seq.delaySteps = 0;

        case 'infiltration_shift'
            % 入渗突变场景: 中段入渗率阶跃变化, 考验参数适应能力
            seq.rainfall = max(0, 0.5 + 0.3 * randn(T, 1));
            seq.ET = 0.4 + 0.1 * randn(T, 1);
            half = floor(T / 2);
            seq.actualInfiltration = [0.2 * ones(half, 1) + 0.02 * randn(half, 1);
                                      0.8 * ones(T - half, 1) + 0.02 * randn(T - half, 1)];
            seq.sensorNoise = 0.3 * randn(T, 1);
            seq.actuatorGain = ones(T, 1);
            seq.delaySteps = 0;

        case 'sensor_noise'
            % 传感器噪声场景: 高幅度测量噪声, 考验观测鲁棒性
            seq.rainfall = max(0, 0.5 + 0.3 * randn(T, 1));
            seq.ET = 0.4 + 0.1 * randn(T, 1);
            seq.actualInfiltration = 0.5 + 0.05 * randn(T, 1);
            seq.sensorNoise = 5.0 * randn(T, 1);
            seq.actuatorGain = ones(T, 1);
            seq.delaySteps = 0;

        case 'actuator_delay'
            % 执行器延迟场景: 长延迟和增益变化, 考验延迟补偿能力
            seq.rainfall = max(0, 0.5 + 0.3 * randn(T, 1));
            seq.ET = 0.4 + 0.1 * randn(T, 1);
            seq.actualInfiltration = 0.5 + 0.05 * randn(T, 1);
            seq.sensorNoise = 0.5 * randn(T, 1);
            seq.actuatorGain = 0.85 + 0.15 * randn(T, 1);
            seq.delaySteps = 3 + randi([0, 1]);  % 每回合固定为3或4步

        otherwise
            error('paddyexp:unknownScenario', ...
                  '未知场景: ''%s''。支持: %s', scenario, strjoin(cfg.scenarios, ', '));
    end

    % ---- 物理非负钳制 ----
    seq.ET = max(0, seq.ET(:));
    seq.actualInfiltration = max(0, seq.actualInfiltration(:));
    seq.actuatorGain = max(0, seq.actuatorGain(:));

    % 确保其余序列为列向量
    seq.rainfall = seq.rainfall(:);
    seq.sensorNoise = seq.sensorNoise(:);
end
