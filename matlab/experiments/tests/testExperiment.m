% testExperiment - 实验底座单元测试
% 继承 matlab.unittest.TestCase, 覆盖配置、序列生成、确定性、
% 控制器公平性、动力学、非负性、smoke输出完整性。
classdef testExperiment < matlab.unittest.TestCase

    methods (Test)

        % testConfigValid - 验证配置结构体各字段类型和取值范围
        function testConfigValid(testCase)
            cfg = paddyexp.config();
            testCase.verifyEqual(cfg.T, 96);
            testCase.verifyEqual(cfg.targetWaterLevel, 40);
            testCase.verifyEqual(cfg.mpcHorizon, 12);
            testCase.verifyGreaterThan(cfg.bandHigh, cfg.bandLow);
            testCase.verifyGreaterThan(cfg.safetyHigh, cfg.safetyLow);
            testCase.verifyGreaterThan(cfg.actionMax, cfg.actionMin);
            testCase.verifyGreaterThan(cfg.ruleIrrigateOff, cfg.ruleIrrigateOn);
            testCase.verifyTrue(iscell(cfg.scenarios));
            testCase.verifyEqual(length(cfg.scenarios), 6);
        end

        % testGenerateEpisodeDeterminism - 相同seed生成完全一致的序列
        function testGenerateEpisodeDeterminism(testCase)
            cfg = paddyexp.config();
            seq1 = paddyexp.generateEpisode(cfg, 'dry', 42);
            seq2 = paddyexp.generateEpisode(cfg, 'dry', 42);

            testCase.verifyEqual(seq1.rainfall, seq2.rainfall);
            testCase.verifyEqual(seq1.ET, seq2.ET);
            testCase.verifyEqual(seq1.actualInfiltration, seq2.actualInfiltration);
            testCase.verifyEqual(seq1.sensorNoise, seq2.sensorNoise);
            testCase.verifyEqual(seq1.actuatorGain, seq2.actuatorGain);
            testCase.verifyEqual(seq1.delaySteps, seq2.delaySteps);
        end

        % testDifferentSeedsProduceDifferentSequences - 不同seed至少一个序列不同
        function testDifferentSeedsProduceDifferentSequences(testCase)
            cfg = paddyexp.config();
            seq1 = paddyexp.generateEpisode(cfg, 'dry', 1);
            seq2 = paddyexp.generateEpisode(cfg, 'dry', 2);

            diffRain = any(seq1.rainfall ~= seq2.rainfall);
            diffET = any(seq1.ET ~= seq2.ET);
            diffInf = any(seq1.actualInfiltration ~= seq2.actualInfiltration);
            diffNoise = any(seq1.sensorNoise ~= seq2.sensorNoise);
            diffGain = any(seq1.actuatorGain ~= seq2.actuatorGain);

            testCase.verifyTrue(diffRain || diffET || diffInf || diffNoise || diffGain, ...
                '不同seed应至少在一个外生序列字段上不同');
        end

        % testAllScenariosSupported - 所有六个场景可正常生成且序列非负
        function testAllScenariosSupported(testCase)
            cfg = paddyexp.config();
            for i = 1:length(cfg.scenarios)
                sc = cfg.scenarios{i};
                seq = paddyexp.generateEpisode(cfg, sc, 1);
                testCase.verifyEqual(length(seq.rainfall), cfg.T, ...
                    sprintf('场景 %s rainfall 长度错误', sc));
                testCase.verifyEqual(length(seq.ET), cfg.T, ...
                    sprintf('场景 %s ET 长度错误', sc));
                testCase.verifyTrue(all(isfinite(seq.rainfall)), ...
                    sprintf('场景 %s rainfall 含非有限值', sc));
                testCase.verifyTrue(all(isfinite(seq.ET)), ...
                    sprintf('场景 %s ET 含非有限值', sc));
                testCase.verifyTrue(all(seq.rainfall >= 0), ...
                    sprintf('场景 %s rainfall 不应为负', sc));
                % 物理非负性
                testCase.verifyTrue(all(seq.ET >= 0), ...
                    sprintf('场景 %s ET 不应为负', sc));
                testCase.verifyTrue(all(seq.actualInfiltration >= 0), ...
                    sprintf('场景 %s actualInfiltration 不应为负', sc));
                testCase.verifyTrue(all(seq.actuatorGain >= 0), ...
                    sprintf('场景 %s actuatorGain 不应为负', sc));
                % delaySteps 为标量
                testCase.verifyTrue(isscalar(seq.delaySteps), ...
                    sprintf('场景 %s delaySteps 应为标量', sc));
                testCase.verifyGreaterThanOrEqual(seq.delaySteps, 0, ...
                    sprintf('场景 %s delaySteps 不应为负', sc));
            end
        end

        % testUnknownScenarioThrowsError - 未知场景应抛出异常
        function testUnknownScenarioThrowsError(testCase)
            cfg = paddyexp.config();
            testCase.verifyError(@() paddyexp.generateEpisode(cfg, 'invalid_scene', 1), ...
                'paddyexp:unknownScenario');
        end

        % testWaterBalance - 验证水量平衡方程在仿真中成立
        function testWaterBalance(testCase)
            cfg = paddyexp.config();
            seq = paddyexp.generateEpisode(cfg, 'intermittent_rain', 99);
            [envState, obs] = paddyexp.reset(cfg, seq); %#ok<ASGLU>

            hPrev = envState.waterLevel;
            for t = 1:cfg.T
                action = 3.0;  % 固定灌溉量用于测试
                [envState, ~, info] = paddyexp.stepDynamics(cfg, envState, action, seq, t);

                % 验证: h[t] = h[t-1] + P[t] - ET[t] - I[t] + u_eff[t] - D[t]
                expected = hPrev + info.rainfall - info.ET ...
                           - info.actualInfiltration + info.actualIrrigation ...
                           - info.drainage;
                % 钳制到 >=0 (物理下界)
                expected = max(0, expected);
                testCase.verifyEqual(info.trueWaterLevel, expected, 'AbsTol', 1e-10, ...
                    sprintf('水量不平衡 at t=%d', t));
                hPrev = info.trueWaterLevel;
            end
        end

        % testStepDynamicsFiniteOutputs - 动力学输出始终为有限值
        function testStepDynamicsFiniteOutputs(testCase)
            cfg = paddyexp.config();
            seq = paddyexp.generateEpisode(cfg, 'heavy_rain', 7);
            [envState, obs] = paddyexp.reset(cfg, seq); %#ok<ASGLU>

            for t = 1:cfg.T
                action = 5.0;
                [envState, obs, info] = paddyexp.stepDynamics(cfg, envState, action, seq, t);

                testCase.verifyTrue(isfinite(envState.waterLevel), ...
                    sprintf('水位非有限 at t=%d', t));
                testCase.verifyTrue(isfinite(obs.waterLevel), ...
                    sprintf('观测非有限 at t=%d', t));
                testCase.verifyTrue(isfinite(info.trueWaterLevel), ...
                    sprintf('真实水位非有限 at t=%d', t));
                testCase.verifyTrue(isfinite(info.actualIrrigation), ...
                    sprintf('灌溉量非有限 at t=%d', t));
                testCase.verifyGreaterThanOrEqual(envState.waterLevel, 0, ...
                    sprintf('水位为负 at t=%d', t));
            end
        end

        % testRuleControllerHysteresis - 规则控制器滞回行为
        function testRuleControllerHysteresis(testCase)
            cfg = paddyexp.config();

            % 低水位应触发灌溉
            obsLow = struct('waterLevel', 20);
            [action, ~] = paddyexp.ruleController(cfg, obsLow, []);
            testCase.verifyGreaterThan(action, 0, '低水位应开始灌溉');

            % 高水位应停止
            obsHigh = struct('waterLevel', 50);
            ctrlState = struct('irrigating', true, 'irrigationCounter', cfg.ruleMinSteps);
            [action, ~] = paddyexp.ruleController(cfg, obsHigh, ctrlState);
            testCase.verifyEqual(action, 0, '高水位应停止灌溉');
        end

        % testRuleControllerMinSteps - 最小灌溉步数防止抖振
        function testRuleControllerMinSteps(testCase)
            cfg = paddyexp.config();

            % 刚开始灌溉, 即使水位高于关闭阈值也不应停止 (未达到最小步数)
            ctrlState = struct('irrigating', true, 'irrigationCounter', 1);
            obs = struct('waterLevel', 50);  % 高于关闭阈值
            [action, newState] = paddyexp.ruleController(cfg, obs, ctrlState);
            testCase.verifyGreaterThan(action, 0, '未达最小步数时应继续灌溉');
            testCase.verifyTrue(newState.irrigating);
        end

        % testMpcControllerFiniteAction - MPC输出有限动作
        function testMpcControllerFiniteAction(testCase)
            cfg = paddyexp.config();
            seq = paddyexp.generateEpisode(cfg, 'dry', 3);

            obs = struct('waterLevel', cfg.targetWaterLevel);
            ctrlState = struct('prevAction', 0);
            [action, ~] = paddyexp.mpcController(cfg, obs, ctrlState, seq, 1);

            testCase.verifyTrue(isfinite(action), 'MPC动作应为有限值');
            testCase.verifyGreaterThanOrEqual(action, cfg.actionMin, 'MPC动作不低于下限');
            testCase.verifyLessThanOrEqual(action, cfg.actionMax, 'MPC动作不高于上限');
        end

        % testMpcFallbackOnConstrainedCase - MPC在极端条件下的回退
        function testMpcFallbackOnConstrainedCase(testCase)
            cfg = paddyexp.config();
            % 极端场景: 极高水位, 可能触发约束不可行
            seq = paddyexp.generateEpisode(cfg, 'heavy_rain', 3);
            % 设置极高水位观测, 可能导致QP约束冲突
            obs = struct('waterLevel', 200);  % 远超安全上限
            ctrlState = struct('prevAction', 0);

            [action, ctrlState] = paddyexp.mpcController(cfg, obs, ctrlState, seq, 1);

            % 无论QP是否成功, 都应有有效的有限动作
            testCase.verifyTrue(isfinite(action), '即使QP失败, 回退动作也应为有限值');
            testCase.verifyGreaterThanOrEqual(action, cfg.actionMin);
            testCase.verifyLessThanOrEqual(action, cfg.actionMax);
        end

        % testComputeMetricsStructure - 指标结构体字段完整性
        function testComputeMetricsStructure(testCase)
            cfg = paddyexp.config();
            T = cfg.T;

            ts = struct();
            ts.trueWaterLevel = cfg.targetWaterLevel * ones(T, 1) + randn(T, 1) * 5;
            ts.action = 3 * ones(T, 1);
            ts.actualIrrigation = 3 * ones(T, 1);
            ts.decisionTimeMs = 0.5 * ones(T, 1);
            ts.mpcFallback = false(T, 1);

            m = paddyexp.computeMetrics(cfg, ts, 'rule');
            testCase.verifyTrue(isfield(m, 'waterLevelMAE'));
            testCase.verifyTrue(isfield(m, 'bandViolationRate'));
            testCase.verifyTrue(isfield(m, 'safetyViolationCount'));
            testCase.verifyTrue(isfield(m, 'totalIrrigationMm'));
            testCase.verifyTrue(isfield(m, 'switchCount'));
            testCase.verifyTrue(isfield(m, 'meanDecisionTimeMs'));
            testCase.verifyTrue(isfield(m, 'mpcFallbackCount'));
        end

        % testSafetyViolationCountsContiguousEvents - 安全越界按连续事件计数
        function testSafetyViolationCountsContiguousEvents(testCase)
            cfg = paddyexp.config();

            % 构造含两个连续越界区间的水位序列
            h = cfg.targetWaterLevel * ones(50, 1);
            h(10:15) = -5;     % 第一个越界事件: 6步连续低水位
            h(30:35) = 120;    % 第二个越界事件: 6步连续高水位
            h(40) = -3;        % 第三个越界事件: 1步单独低水位

            ts = struct();
            ts.trueWaterLevel = h;
            ts.action = zeros(50, 1);
            ts.actualIrrigation = zeros(50, 1);
            ts.decisionTimeMs = zeros(50, 1);
            ts.mpcFallback = false(50, 1);

            m = paddyexp.computeMetrics(cfg, ts, 'rule');
            testCase.verifyEqual(m.safetyViolationCount, 3, ...
                '应计数为3个连续越界事件, 而非12个采样点');
        end

        % testSameSeqForBothControllers - 同一scenario+seed下两个控制器扰动序列一致
        function testSameSeqForBothControllers(testCase)
            cfg = paddyexp.config();
            scenario = 'dry';
            seed = 42;

            % 生成一份序列
            seq = paddyexp.generateEpisode(cfg, scenario, seed);

            % 运行两个控制器
            [envStateR, obsR] = paddyexp.reset(cfg, seq);
            [envStateM, obsM] = paddyexp.reset(cfg, seq);
            ctrlStateR = struct();
            ctrlStateM = struct();

            for t = 1:cfg.T
                [actR, ctrlStateR] = paddyexp.ruleController(cfg, obsR, ctrlStateR);
                [actM, ctrlStateM] = paddyexp.mpcController(cfg, obsM, ctrlStateM, seq, t);

                [envStateR, obsR, infoR] = paddyexp.stepDynamics(cfg, envStateR, actR, seq, t);
                [envStateM, obsM, infoM] = paddyexp.stepDynamics(cfg, envStateM, actM, seq, t);

                % 外生变量必须相同
                testCase.verifyEqual(infoR.rainfall, infoM.rainfall, ...
                    sprintf('rainfall不一致 at t=%d', t));
                testCase.verifyEqual(infoR.ET, infoM.ET, ...
                    sprintf('ET不一致 at t=%d', t));
                testCase.verifyEqual(infoR.actualInfiltration, infoM.actualInfiltration, ...
                    sprintf('infiltration不一致 at t=%d', t));
            end
        end

        % testRuleControllerDoesNotUseFuture - 规则控制器不依赖seq和t参数
        function testRuleControllerDoesNotUseFuture(testCase)
            cfg = paddyexp.config();
            % 规则控制器仅使用obs, 不读取未来或外生序列
            obs = struct('waterLevel', 30);
            [act1, st1] = paddyexp.ruleController(cfg, obs, []);
            [act2, st2] = paddyexp.ruleController(cfg, obs, []);
            testCase.verifyEqual(act1, act2);
            testCase.verifyEqual(st1.irrigating, st2.irrigating);
        end

        % testSmokeOutputs - 全流程smoke输出完整性验证
        function testSmokeOutputs(testCase)
            cfg = paddyexp.config();
            % 写入 tempdir 下的唯一临时目录 (不删除, 保留系统临时产物)
            tmpDir = fullfile(tempdir, ['paddy_smoke_test_' datestr(now, 'yyyymmdd_HHMMSS')]);
            out = runMpcBaselineExperiment('smoke', tmpDir);

            outDir = out.outputDir;
            testCase.verifyTrue(isfolder(outDir), '输出目录应存在');

            % 验证所有预期文件存在
            expectedFiles = {'manifest.json', 'episode_metrics.csv', ...
                'paired_deltas.csv', 'aggregate_metrics.csv', ...
                'representative_timeseries.csv', ...
                'timeseries_comparison.png', 'metrics_comparison.png'};
            for i = 1:length(expectedFiles)
                fpath = fullfile(outDir, expectedFiles{i});
                testCase.verifyTrue(isfile(fpath), ...
                    sprintf('缺失输出文件: %s', expectedFiles{i}));
            end

            % 验证 episode_metrics.csv 行数: 6场景×2种子=12对, ×2控制器=24行
            em = readtable(fullfile(outDir, 'episode_metrics.csv'));
            testCase.verifyEqual(height(em), 24, '应恰好24条episode指标');

            % 验证 paired_deltas.csv 行数: 12对
            pd = readtable(fullfile(outDir, 'paired_deltas.csv'));
            testCase.verifyEqual(height(pd), 12, '应恰好12条配对差值');

            % 验证 aggregate_metrics.csv 统一schema
            agg = readtable(fullfile(outDir, 'aggregate_metrics.csv'));
            expectedCols = {'rowType', 'controller', 'scenario', 'metric', ...
                'n', 'mean', 'std', 'min', 'max', 'positiveRatio', 'improvementRatio', 'lowerIsBetter'};
            for i = 1:length(expectedCols)
                testCase.verifyTrue(ismember(expectedCols{i}, agg.Properties.VariableNames), ...
                    sprintf('aggregate_metrics 缺少列: %s', expectedCols{i}));
            end
            testCase.verifyGreaterThan(height(agg), 0, 'aggregate_metrics 不应为空');
            % 应包含 controller_summary (6场景×2控制器×7指标=84行) + paired_delta (6场景×7指标=42行) = 126行
            nCtrlSummary = sum(strcmp(agg.rowType, 'controller_summary'));
            nPairedDelta = sum(strcmp(agg.rowType, 'paired_delta'));
            testCase.verifyEqual(nCtrlSummary, length(cfg.scenarios) * 2 * 7, ...
                'controller_summary 行数不正确');
            testCase.verifyEqual(nPairedDelta, length(cfg.scenarios) * 7, ...
                'paired_delta 行数不正确');

            % 验证 representative_timeseries.csv 存在且有数据
            rt = readtable(fullfile(outDir, 'representative_timeseries.csv'));
            testCase.verifyEqual(height(rt), cfg.T, '代表时序应恰好96行');

            % 验证 manifest.json 可 jsondecode
            manifestPath = fullfile(outDir, 'manifest.json');
            fid = fopen(manifestPath, 'r');
            raw = fread(fid, inf, 'uint8=>char')';
            fclose(fid);
            m = jsondecode(raw);
            testCase.verifyEqual(m.mode, 'smoke');
            testCase.verifyEqual(m.nPairs, 12);

            % 验证 PNG 可读取 (imread成功则文件有效)
            try
                imread(fullfile(outDir, 'timeseries_comparison.png'));
                imread(fullfile(outDir, 'metrics_comparison.png'));
                pngOk = true;
            catch
                pngOk = false;
            end
            testCase.verifyTrue(pngOk, 'PNG文件应可被 imread 读取');
        end

    end
end
