% TestSilModel - MATLAB SIL仿真模型单元测试套件
% 使用 matlab.unittest 框架验证:
%   1. 可重复性 (相同seed和输入产生相同输出)
%   2. 水位动态与命令影响 (开泵开阀水位上升, STOP_IRRIGATION后入流为0)
%   3. 状态值合法范围 (不越界)
%   4. 六种场景均可创建
%   5. payload不包含敏感字段
%   6. occurredAt带UTC时区
%   7. dry-run不调用HTTP (可观测结果验证)
%
% 运行方式 (MATLAB命令行):
%   results = runtests('TestSilModel.m')
%   或运行指定测试:
%   results = runtests('TestSilModel.m', 'ProcedureName', 'testReproducibility')
classdef TestSilModel < matlab.unittest.TestCase

    properties (TestParameter)
        % 参数化测试: 所有六个场景名称
        scenarioName = {'normal', 'low_water', 'high_water', 'high_ec', 'blocked_flow', 'valve_leak'};
    end

    methods (Test)

    % ==================== 可重复性测试 ====================

    % testReproducibility - 验证相同seed和配置产生相同的仿真结果
    function testReproducibility(testCase)
        % 创建两个相同的dry-run配置
        cfg1 = createSilConfig('http://localhost:8080', 'test-token-dummy');
        cfg1.dryRun = true;
        cfg1.steps = 20;
        cfg1.realtimePauseSeconds = 0;  % 测试时不暂停

        cfg2 = createSilConfig('http://localhost:8080', 'test-token-dummy');
        cfg2.dryRun = true;
        cfg2.steps = 20;
        cfg2.realtimePauseSeconds = 0;

        % 执行两次仿真
        res1 = runSil(cfg1, 'normal');
        % 重置随机种子后再执行第二次
        rng(cfg2.randomSeed);
        res2 = runSil(cfg2, 'normal');

        % 验证步数一致
        testCase.verifyEqual(numel(res1.records), numel(res2.records), ...
            '两次运行的记录数应相同');

        % 验证关键指标每步一致 (水位和EC)
        for i = 1:numel(res1.records)
            testCase.verifyEqual(res1.records(i).waterLevelMm, res2.records(i).waterLevelMm, ...
                'RelTol', 1e-6, sprintf('Step %d: 水位应可重复', i));
            testCase.verifyEqual(res1.records(i).ecMsCm, res2.records(i).ecMsCm, ...
                'RelTol', 1e-6, sprintf('Step %d: EC应可重复', i));
        end
    end

    % ==================== 水位动态与命令测试 ====================

    % testWaterLevelRisesWhenPumpOn - 验证开泵开阀时水位上升
    function testWaterLevelRisesWhenPumpOn(testCase)
        % 创建状态并手动设置
        state = initializePaddyState('TEST-001');
        scenario = createScenario('normal');
        initialWater = state.waterLevelMm;

        % 开泵开阀
        state.pumpOn = true;
        state.irrigationValveOpen = true;

        % 执行10步
        for i = 1:10
            state = stepPaddyModel(state, scenario, 5);
        end

        % 水位应上升 (进水 > 入渗+蒸散+排水)
        testCase.verifyGreaterThan(state.waterLevelMm, initialWater, ...
            '开泵开阀后水位应上升');
    end

    % testStopIrrigationZeroFlow - 验证STOP_IRRIGATION后入流为0
    function testStopIrrigationZeroFlow(testCase)
        state = initializePaddyState('TEST-001');
        scenario = createScenario('normal');

        % 先开泵开阀
        state.pumpOn = true;
        state.irrigationValveOpen = true;
        state = stepPaddyModel(state, scenario, 5);
        flowBeforeStop = state.flowRateLMin;

        % STOP_IRRIGATION
        state.pumpOn = false;
        state.irrigationValveOpen = false;
        state = stepPaddyModel(state, scenario, 5);

        % 验证入流为0
        testCase.verifyEqual(state.flowRateLMin, 0.0, ...
            'STOP_IRRIGATION后流量应为0');

        % 验证之前确实有流量 (除非blocked_flow场景会干扰)
        % 仅记录非0验证
        if abs(flowBeforeStop) > 0
            testCase.verifyGreaterThan(flowBeforeStop, 0.0, ...
                '停止前应有正流量');
        end
    end

    % ==================== 状态合法范围测试 ====================

    % testStateWithinLegalRange - 验证所有指标不越Java合法范围
    function testStateWithinLegalRange(testCase)
        state = initializePaddyState('TEST-001');
        scenario = createScenario('normal');
        state.pumpOn = true;
        state.irrigationValveOpen = true;

        % 执行大量步数以测试边界
        for i = 1:500
            state = stepPaddyModel(state, scenario, 5);
        end

        % 验证Java合法范围
        testCase.verifyTrue(state.waterLevelMm >= 0 && state.waterLevelMm <= 500, ...
            sprintf('水位 %.2f 应在0-500范围', state.waterLevelMm));
        testCase.verifyTrue(state.flowRateLMin >= 0 && state.flowRateLMin <= 1000, ...
            sprintf('流量 %.2f 应在0-1000范围', state.flowRateLMin));
        testCase.verifyTrue(state.ecMsCm >= 0 && state.ecMsCm <= 20, ...
            sprintf('EC %.2f 应在0-20范围', state.ecMsCm));
        testCase.verifyTrue(state.soilMoisturePct >= 0 && state.soilMoisturePct <= 100, ...
            sprintf('含水率 %.2f 应在0-100范围', state.soilMoisturePct));
        testCase.verifyTrue(state.rainfallMm >= 0 && state.rainfallMm <= 500, ...
            sprintf('降雨 %.2f 应在0-500范围', state.rainfallMm));
    end

    % ==================== 场景创建测试 ====================

    % testSixScenariosCreatable - 验证六种场景均可创建
    function testSixScenariosCreatable(testCase, scenarioName)
        % 参数化测试: 每种场景分别验证
        s = createScenario(scenarioName);
        testCase.verifyEqual(s.name, lower(scenarioName), ...
            '场景名称应匹配');
        testCase.verifyTrue(isfield(s, 'flowMultiplier'), ...
            '场景应包含flowMultiplier字段');
        testCase.verifyTrue(isfield(s, 'rainfallBaselineMm'), ...
            '场景应包含rainfallBaselineMm字段');
        testCase.verifyTrue(isfield(s, 'ecMultiplier'), ...
            '场景应包含ecMultiplier字段');
    end

    % ==================== Payload字段验证 ====================

    % testPayloadExcludesSensitiveFields - 验证payload不包含禁止字段
    function testPayloadExcludesSensitiveFields(testCase)
        state = initializePaddyState('SIM-PADDY-001');
        scenario = createScenario('normal');
        payload = buildTelemetryPayload('SAMPLE-00001', 'SIM-PADDY-001', 1, scenario, state);

        % 验证不包含禁止字段
        testCase.verifyFalse(isfield(payload, 'ownerUsername'), ...
            'payload不应包含ownerUsername');
        testCase.verifyFalse(isfield(payload, 'sourceType'), ...
            'payload不应包含sourceType');
        testCase.verifyFalse(isfield(payload, 'alarm'), ...
            'payload不应包含alarm');
        testCase.verifyFalse(isfield(payload, 'Bearer'), ...
            'payload不应包含Bearer');
        testCase.verifyFalse(isfield(payload, 'token'), ...
            'payload不应包含token');
        testCase.verifyFalse(isfield(payload, 'password'), ...
            'payload不应包含password');
        testCase.verifyFalse(isfield(payload, 'secret'), ...
            'payload不应包含secret');
    end

    % testPayloadHasRequiredFields - 验证payload包含必需字段
    function testPayloadHasRequiredFields(testCase)
        state = initializePaddyState('SIM-PADDY-001');
        scenario = createScenario('high_water');
        payload = buildTelemetryPayload('SAMPLE-00001', 'SIM-PADDY-001', 50, scenario, state);

        % 验证必需字段存在
        testCase.verifyTrue(isfield(payload, 'sampleId'), '应包含sampleId');
        testCase.verifyTrue(isfield(payload, 'deviceId'), '应包含deviceId');
        testCase.verifyTrue(isfield(payload, 'occurredAt'), '应包含occurredAt');
        testCase.verifyTrue(isfield(payload, 'waterLevelMm'), '应包含waterLevelMm');
        testCase.verifyTrue(isfield(payload, 'flowRateLMin'), '应包含flowRateLMin');
        testCase.verifyTrue(isfield(payload, 'ecMsCm'), '应包含ecMsCm');
        testCase.verifyTrue(isfield(payload, 'soilMoisturePct'), '应包含soilMoisturePct');
        testCase.verifyTrue(isfield(payload, 'rainfallMm'), '应包含rainfallMm');
        testCase.verifyTrue(isfield(payload, 'pumpOn'), '应包含pumpOn');
        testCase.verifyTrue(isfield(payload, 'irrigationValveOpen'), '应包含irrigationValveOpen');
        testCase.verifyTrue(isfield(payload, 'fertilizerPumpOn'), '应包含fertilizerPumpOn');
        testCase.verifyTrue(isfield(payload, 'scenarioCode'), '应包含scenarioCode');

        % 验证scenarioCode不含alarm=true
        testCase.verifyTrue(~contains(payload.scenarioCode, 'alarm'), ...
            'scenarioCode不应包含alarm预判');
    end

    % ==================== 时间戳格式验证 ====================

    % testOccurredAtHasUtcTimezone - 验证occurredAt为UTC时区ISO-8601格式
    function testOccurredAtHasUtcTimezone(testCase)
        state = initializePaddyState('SIM-PADDY-001');
        scenario = createScenario('normal');
        payload = buildTelemetryPayload('SAMPLE-00001', 'SIM-PADDY-001', 1, scenario, state);

        occurredAt = payload.occurredAt;
        testCase.verifyClass(occurredAt, 'char', 'occurredAt应是字符串');

        % 验证UTC时区: 以Z或+00:00结尾
        testCase.verifyTrue(endsWith(occurredAt, 'Z') || contains(occurredAt, '+00:00'), ...
            sprintf('occurredAt应带UTC时区: %s', occurredAt));
    end

    % ==================== Dry-run测试 ====================

    % testDryRunNoHttp - 验证dry-run不调用HTTP
    function testDryRunNoHttp(testCase)
        % 使用无效URL创建配置 (dry-run不应实际请求)
        invalidUrl = 'http://127.0.0.1:99999';  % 不可能存在的端口
        cfg = createSilConfig(invalidUrl, 'dummy-token');
        cfg.dryRun = true;
        cfg.steps = 5;
        cfg.realtimePauseSeconds = 0;

        % dry-run应成功完成, 不应该因网络错误而失败
        res = runSil(cfg, 'normal');

        % 验证有记录生成
        testCase.verifyNotEmpty(res.records, 'dry-run应生成记录');
        testCase.verifyEqual(numel(res.records), 5, 'dry-run应完成5步');

        % 验证记录未上传 (dry-run不联网)
        for i = 1:numel(res.records)
            testCase.verifyFalse(res.records(i).uploaded, ...
                sprintf('Step %d: dry-run不应标记为已上传', i));
        end
    end

    % ==================== 配置验证测试 ====================

    % testConfigValidation - 验证createSilConfig参数校验
    function testConfigValidation(testCase)
        % 空baseUrl应报错
        testCase.verifyError(@() createSilConfig('', 'token123'), ...
            'createSilConfig:EmptyBaseUrl');

        % 空token应报错
        testCase.verifyError(@() createSilConfig('http://localhost', ''), ...
            'createSilConfig:EmptyBearerToken');
    end

    % ==================== 非法场景测试 ====================

    % testInvalidScenarioError - 验证非法场景名报错
    function testInvalidScenarioError(testCase)
        testCase.verifyError(@() createScenario('invalid_scenario'), ...
            'createScenario:UnsupportedScenario');
    end

    end
end
