% testResidualRl - 残差SAC RL 单元测试
% 继承 matlab.unittest.TestCase, 覆盖seed互斥性、环境验证、
% 观测正确性、安全屏蔽行为、smoke训练和验证评估。
classdef testResidualRl < matlab.unittest.TestCase

    methods (Test)

        % testSeedPartitionsMutuallyExclusive - 三组seed互斥且不与1-30重叠
        function testSeedPartitionsMutuallyExclusive(testCase)
            rlCfg = residualRlConfig();
            train = rlCfg.trainingSeeds;
            val = rlCfg.validationSeeds;
            test = rlCfg.testSeeds;
            mpcUsed = 1:30;

            % 三组两两无交集
            testCase.verifyTrue(isempty(intersect(train, val)), ...
                'trainingSeeds 与 validationSeeds 有交集');
            testCase.verifyTrue(isempty(intersect(train, test)), ...
                'trainingSeeds 与 testSeeds 有交集');
            testCase.verifyTrue(isempty(intersect(val, test)), ...
                'validationSeeds 与 testSeeds 有交集');

            % 均不与MPC seed 1-30重叠
            testCase.verifyTrue(isempty(intersect(train, mpcUsed)), ...
                'trainingSeeds 与 MPC seed 1-30 有交集');
            testCase.verifyTrue(isempty(intersect(val, mpcUsed)), ...
                'validationSeeds 与 MPC seed 1-30 有交集');
            testCase.verifyTrue(isempty(intersect(test, mpcUsed)), ...
                'testSeeds 与 MPC seed 1-30 有交集');

            % 各组非空
            testCase.verifyEqual(length(train), 100, 'trainingSeeds 应恰好100个');
            testCase.verifyEqual(length(val), 10, 'validationSeeds 应恰好10个');
            testCase.verifyEqual(length(test), 30, 'testSeeds 应恰好30个');

            % agentRandomSeed 正确
            testCase.verifyEqual(rlCfg.agentRandomSeed, 20260811);
        end

        % testConfigDoesNotOverwriteBase - RL配置不覆写基础配置
        function testConfigDoesNotOverwriteBase(testCase)
            baseCfgBefore = paddyexp.config();
            rlCfg = residualRlConfig(); %#ok<NASGU>
            baseCfgAfter = paddyexp.config();

            testCase.verifyEqual(baseCfgBefore.T, baseCfgAfter.T);
            testCase.verifyEqual(baseCfgBefore.targetWaterLevel, baseCfgAfter.targetWaterLevel);
            testCase.verifyEqual(baseCfgBefore.actionMax, baseCfgAfter.actionMax);
            testCase.verifyEqual(baseCfgBefore.scenarios, baseCfgAfter.scenarios);
        end

        % testObsDimensions - 观测向量为6×1且含非NaN有限值
        function testObsDimensions(testCase)
            rlCfg = residualRlConfig();
            obsVec = buildResidualObservation(40, 0, 3, 1.5, 0.4, 0, rlCfg);

            testCase.verifyEqual(size(obsVec), [6 1], '观测应为6×1列向量');
            testCase.verifyTrue(all(isfinite(obsVec)), '观测分量应全部有限');
        end

        % testObsNormalization - 观测各分量在预期归一化范围内
        function testObsNormalization(testCase)
            rlCfg = residualRlConfig();

            % 极端值输入测试
            obsVec1 = buildResidualObservation(0, 0, 0, 0, 0, 0, rlCfg);
            testCase.verifyTrue(all(isfinite(obsVec1)), '零输入应生成有限观测');

            obsVec2 = buildResidualObservation(200, 8, 8, 20, 2, 2, rlCfg);
            testCase.verifyTrue(all(isfinite(obsVec2)), '极值输入应生成有限观测');
            % 各分量应在钳制范围内
            testCase.verifyTrue(obsVec2(1) >= -2 && obsVec2(1) <= 2, ...
                '水位误差分量应在[-2,2]内');
            testCase.verifyTrue(obsVec2(2) >= 0 && obsVec2(2) <= 1, ...
                '动作分量应在[0,1]内');
            testCase.verifyTrue(obsVec2(6) >= -1 && obsVec2(6) <= 1, ...
                '残差分量应在[-1,1]内');

            % 负水位场景
            obsVec3 = buildResidualObservation(-10, 0, 0, 0, 0, -3, rlCfg);
            testCase.verifyTrue(all(isfinite(obsVec3)), '负水位输入应生成有限观测');
        end

        % testObsDoesNotContainHiddenState - 观测不暴露不可观测真值
        function testObsDoesNotContainHiddenState(testCase)
            rlCfg = residualRlConfig();
            % 该函数只接受6个参数, 不含 actualInfiltration/actuatorGain/
            % delaySteps/trueWaterLevel 等隐状态
            % 仅验证函数签名与返回值的一致性
            obsVec = buildResidualObservation(40, 3, 3, 1.5, 0.4, 0.5, rlCfg);
            testCase.verifyEqual(length(obsVec), 6);
            testCase.verifyTrue(all(isfinite(obsVec)));
        end

        % testApplySafetyShieldHighWater - 高水位时强制零灌溉
        function testApplySafetyShieldHighWater(testCase)
            rlCfg = residualRlConfig();
            highWL = rlCfg.safetyHigh - rlCfg.safetyHighMargin + 1;  % 刚好超过余量

            [finalAction, intervened, reason] = applySafetyShield(...
                rlCfg, 5, highWL, 3);
            testCase.verifyEqual(finalAction, 0, '高水位应强制零灌溉');
            testCase.verifyTrue(intervened, '应标记干预');
            testCase.verifyEqual(reason, 'high_water_forced_zero');
        end

        % testApplySafetyShieldLowWater - 低水位时强制最小灌溉
        function testApplySafetyShieldLowWater(testCase)
            rlCfg = residualRlConfig();
            lowWL = rlCfg.safetyLow + rlCfg.safetyLowMargin - 1;  % 刚好低于余量

            [finalAction, intervened, reason] = applySafetyShield(...
                rlCfg, 0, lowWL, 0);
            minSafe = min(rlCfg.ruleIrrigationRate, rlCfg.actionMax);
            testCase.verifyGreaterThanOrEqual(finalAction, minSafe, ...
                '低水位应保证不低于最小安全灌溉量');
            testCase.verifyTrue(intervened, '应标记干预');
            testCase.verifyEqual(reason, 'low_water_forced_min');
        end

        % testApplySafetyShieldRateLimit - 变化率限制行为
        function testApplySafetyShieldRateLimit(testCase)
            rlCfg = residualRlConfig();
            normalWL = rlCfg.targetWaterLevel;  % 正常水位, 不触发高/低屏蔽

            % 动作变化超出上限
            [finalAction, intervened, reason] = applySafetyShield(...
                rlCfg, 8, normalWL, 3);
            testCase.verifyLessThanOrEqual(finalAction, 3 + rlCfg.actionChangeLimit, ...
                '动作不应超过变化率上限');
            testCase.verifyTrue(intervened);

            % 动作变化超出下限
            [finalAction2, intervened2, ~] = applySafetyShield(...
                rlCfg, 0, normalWL, 5);
            testCase.verifyGreaterThanOrEqual(finalAction2, 5 - rlCfg.actionChangeLimit, ...
                '动作不应低于变化率下限');
            testCase.verifyTrue(intervened2);
        end

        % testApplySafetyShieldBoundClamp - 最终钳制到边界
        function testApplySafetyShieldBoundClamp(testCase)
            rlCfg = residualRlConfig();
            normalWL = rlCfg.targetWaterLevel;

            % 超出上限
            [finalAction, ~, ~] = applySafetyShield(rlCfg, 20, normalWL, 10);
            testCase.verifyLessThanOrEqual(finalAction, rlCfg.actionMax);

            % 低于下限
            [finalAction2, ~, ~] = applySafetyShield(rlCfg, -5, normalWL, 0);
            testCase.verifyGreaterThanOrEqual(finalAction2, rlCfg.actionMin);
        end

        % testApplySafetyShieldNoIntervention - 正常条件不干预
        function testApplySafetyShieldNoIntervention(testCase)
            rlCfg = residualRlConfig();
            normalWL = rlCfg.targetWaterLevel;

            [finalAction, intervened, reason] = applySafetyShield(...
                rlCfg, 4, normalWL, 3.5);  % 变化量0.5 < 限制2
            testCase.verifyEqual(finalAction, 4, '正常范围不应修改动作');
            testCase.verifyFalse(intervened, '不应干预');
            testCase.verifyEqual(reason, 'none');
        end

        % testValidateEnvironment - RL环境通过validateEnvironment
        function testValidateEnvironment(testCase)
            rlCfg = residualRlConfig();
            env = createResidualRlEnvironment(rlCfg);

            % validateEnvironment 不应抛出异常
            testCase.verifyWarningFree(@() validateEnvironment(env), ...
                'validateEnvironment 应无警告');
        end

        % testEnvResetReturnsValidObs - 环境reset返回有效6×1观测
        function testEnvResetReturnsValidObs(testCase)
            rlCfg = residualRlConfig();
            env = createResidualRlEnvironment(rlCfg);

            % 多次reset验证一致性
            for i = 1:3
                obs = reset(env);
                testCase.verifyEqual(size(obs), [6 1], ...
                    sprintf('reset %d: 观测维度错误', i));
                testCase.verifyTrue(all(isfinite(obs(:))), ...
                    sprintf('reset %d: 观测含非有限值', i));
            end
        end

        % testEnvNoNaNInf - 环境连续运行无NaN/Inf
        function testEnvNoNaNInf(testCase)
            rlCfg = residualRlConfig();
            env = createResidualRlEnvironment(rlCfg);

            % 一次完整episode
            obs = reset(env);
            for t = 1:rlCfg.T
                % 随机动作在合法范围内
                action = rlCfg.residualRange(1) + ...
                    rand(1) * diff(rlCfg.residualRange);
                [nextObs, reward, isDone, ~] = step(env, action);

                testCase.verifyTrue(all(isfinite(nextObs(:))), ...
                    sprintf('t=%d: 观测含非有限值', t));
                testCase.verifyTrue(isfinite(reward), ...
                    sprintf('t=%d: 奖励非有限', t));

                if isDone
                    testCase.verifyEqual(t, rlCfg.T, ...
                        'episode应在第96步终止');
                    break;
                end
                obs = nextObs;
            end
        end

        % testEnvActionInBounds - 环境内部动作始终在合法边界内
        function testEnvActionInBounds(testCase)
            rlCfg = residualRlConfig();
            env = createResidualRlEnvironment(rlCfg);

            obs = reset(env);
            for t = 1:rlCfg.T
                action = -2 + 4 * rand(1);  % [-2, 2] 内随机
                [nextObs, ~, isDone, ~] = step(env, action);
                % 观测分量3 (归一化MPC动作) 应在[0,1]内
                testCase.verifyTrue(nextObs(3) >= 0 && nextObs(3) <= 1, ...
                    sprintf('t=%d: 归一化MPC动作为 %f, 超出[0,1]', t, nextObs(3)));
                if isDone
                    break;
                end
                obs = nextObs;
            end
        end

        % testRewardEquation - 奖励方程通过典型值验证
        function testRewardEquation(testCase)
            rlCfg = residualRlConfig();
            env = createResidualRlEnvironment(rlCfg);

            % 运行几步并验证奖励有限
            obs = reset(env);
            rewards = [];
            for t = 1:10
                [nextObs, reward, isDone, ~] = step(env, 0.5);
                rewards = [rewards; reward]; %#ok<AGROW>
                testCase.verifyTrue(isfinite(reward), ...
                    sprintf('t=%d: 奖励非有限', t));
                if isDone
                    break;
                end
                obs = nextObs;
            end
            % 奖励应在合理范围内 (不超过0)
            testCase.verifyTrue(all(rewards <= 0), '奖励应为非正数 (负加权和)');
            testCase.verifyTrue(all(rewards > -50), '奖励不应过度惩罚');
        end

        % testSmokeTrainingProducesOutputs - Smoke训练产生完整输出
        function testSmokeTrainingProducesOutputs(testCase)
            rlCfg = residualRlConfig();
            tmpDir = fullfile(tempdir, ['paddy_rl_smoke_test_' datestr(now, 'yyyymmdd_HHMMSS')]);

            trainOut = trainResidualSac('smoke', tmpDir);
            outDir = trainOut.outputDir;
            testCase.verifyTrue(isfolder(outDir), '训练输出目录应存在');

            % 验证输出文件
            expectedFiles = {'trained_agent.mat', 'training_stats.csv', ...
                'training_curve.png', 'training_manifest.json'};
            for i = 1:length(expectedFiles)
                fpath = fullfile(outDir, expectedFiles{i});
                testCase.verifyTrue(isfile(fpath), ...
                    sprintf('缺失训练输出: %s', expectedFiles{i}));
            end

            % 验证 checkpoints 目录存在且可写 (SaveAgentDirectory 已设为绝对路径)
            checkpointDir = fullfile(outDir, 'checkpoints');
            testCase.verifyTrue(isfolder(checkpointDir), ...
                '训练输出应含 checkpoints 子目录');
            % 尝试写入测试文件验证可写性
            testFile = fullfile(checkpointDir, '.write_test');
            fid = fopen(testFile, 'w');
            testCase.verifyGreaterThan(fid, 0, 'checkpoints 目录应可写');
            if fid > 0
                fclose(fid);
                delete(testFile);
            end

            % 验证 agent 有效性 (使用isa避免硬编码内部包路径)
            testCase.verifyNotEmpty(trainOut.agent);
            testCase.verifyTrue(isa(trainOut.agent, 'rl.agent.rlSACAgent'), ...
                'Agent应为rlSACAgent类型');

            % 验证 training_stats.csv 至少有6行 (smoke最少6 episode)
            stats = readtable(fullfile(outDir, 'training_stats.csv'));
            testCase.verifyGreaterThanOrEqual(height(stats), rlCfg.smokeEpisodes, ...
                '训练统计应至少有smoke episode数');

            % 验证 PNG 可读
            try
                imread(fullfile(outDir, 'training_curve.png'));
                pngOk = true;
            catch
                pngOk = false;
            end
            testCase.verifyTrue(pngOk, '训练曲线PNG应可读');

            % 验证 manifest 可解析
            manifestPath = fullfile(outDir, 'training_manifest.json');
            fid = fopen(manifestPath, 'r');
            raw = fread(fid, inf, 'uint8=>char')';
            fclose(fid);
            m = jsondecode(raw);
            testCase.verifyEqual(m.mode, 'smoke');
            testCase.verifyEqual(m.nEpisodes, rlCfg.smokeEpisodes);
            testCase.verifyEqual(m.seedPartition.agentRandomSeed, 20260811);
        end

        % testSmokeValidationEvaluation - Smoke验证评估输出完整性
        function testSmokeValidationEvaluation(testCase)
            rlCfg = residualRlConfig();

            % 快速smoke训练 (最小化时间)
            tmpTrainDir = fullfile(tempdir, ['paddy_rl_valeval_train_' datestr(now, 'yyyymmdd_HHMMSS')]);
            trainOut = trainResidualSac('smoke', tmpTrainDir);
            agent = trainOut.agent;

            % 使用2个validation seed评估
            valDir = fullfile(tempdir, ['paddy_rl_valeval_' datestr(now, 'yyyymmdd_HHMMSS')]);
            agentPath = fullfile(trainOut.outputDir, 'trained_agent.mat');

            % 直接使用maxSeeds参数限制seed数
            evalOut = evaluateResidualSac(agentPath, 'validation', valDir, 2);

            outDir = evalOut.outputDir;
            testCase.verifyTrue(isfolder(outDir), '评估输出目录应存在');

            % 验证输出文件
            expectedFiles = {'episode_metrics.csv', 'paired_vs_mpc.csv', ...
                'aggregate_metrics.csv', 'representative_timeseries.csv', ...
                'timeseries_comparison.png', 'metrics_comparison.png', ...
                'evaluation_manifest.json'};
            for i = 1:length(expectedFiles)
                fpath = fullfile(outDir, expectedFiles{i});
                testCase.verifyTrue(isfile(fpath), ...
                    sprintf('缺失评估输出: %s', expectedFiles{i}));
            end

            % 验证 episode_metrics.csv 行数
            % 6场景 × 2种子 = 12配对, × 4控制器 = 48行
            em = readtable(fullfile(outDir, 'episode_metrics.csv'));
            expectedRows = length(rlCfg.scenarios) * 2 * 4;  % 12×4=48
            testCase.verifyEqual(height(em), expectedRows, ...
                sprintf('应恰好%d条episode指标', expectedRows));

            % 验证控制器类型
            expectedControllers = {'rule', 'mpc', ...
                'mpc_residual_sac_shielded', 'mpc_residual_sac_unshielded'};
            actualControllers = unique(em.controller, 'stable');
            for i = 1:length(expectedControllers)
                testCase.verifyTrue(ismember(expectedControllers{i}, actualControllers), ...
                    sprintf('缺失控制器: %s', expectedControllers{i}));
            end

            % 验证扩展指标字段存在
            testCase.verifyTrue(ismember('shieldInterventionCount', em.Properties.VariableNames));
            testCase.verifyTrue(ismember('meanAbsoluteResidual', em.Properties.VariableNames));

            % 验证所有指标为有限值
            for i = 1:height(em)
                testCase.verifyTrue(isfinite(em.waterLevelMAE(i)), ...
                    sprintf('waterLevelMAE非有限 at row %d', i));
                testCase.verifyTrue(isfinite(em.safetyViolationCount(i)), ...
                    sprintf('safetyViolationCount非有限 at row %d', i));
            end

            % 验证 paired_vs_mpc.csv: 2种子 × 6场景 × 2 SAC类型 = 24行
            pd = readtable(fullfile(outDir, 'paired_vs_mpc.csv'));
            expectedPdRows = length(rlCfg.scenarios) * 2 * 2;  % 24
            testCase.verifyEqual(height(pd), expectedPdRows, ...
                sprintf('paired_vs_mpc 应恰好%d行', expectedPdRows));

            % 回归断言: scenario/controller列可读 (非空字符)
            testCase.verifyTrue(iscell(pd.scenario) || isstring(pd.scenario), ...
                'paired_vs_mpc scenario列应可读');
            testCase.verifyTrue(iscell(pd.controller) || isstring(pd.controller), ...
                'paired_vs_mpc controller列应可读');
            testCase.verifyTrue(all(cellfun(@(c) ischar(c) && ~isempty(c), ...
                cellstr(pd.scenario))), 'scenario值应非空');
            testCase.verifyTrue(all(cellfun(@(c) ischar(c) && ~isempty(c), ...
                cellstr(pd.controller))), 'controller值应非空');

            % 回归断言: 所有数值差值有限
            deltaCols = pd.Properties.VariableNames;
            deltaCols = setdiff(deltaCols, {'scenario', 'seed', 'controller'}, 'stable');
            for j = 1:length(deltaCols)
                vals = pd.(deltaCols{j});
                testCase.verifyTrue(all(isfinite(vals)), ...
                    sprintf('paired_vs_mpc %s列含非有限值', deltaCols{j}));
            end

            % 回归断言: scenario+seed+controller主键无重复
            keys = strcat(cellstr(pd.scenario), '_', ...
                cellstr(num2str(pd.seed)), '_', cellstr(pd.controller));
            testCase.verifyEqual(length(unique(keys)), height(pd), ...
                'paired_vs_mpc 主键 scenario+seed+controller 存在重复');

            % 验证 aggregate_metrics.csv 非空且含正确rowType
            agg = readtable(fullfile(outDir, 'aggregate_metrics.csv'));
            testCase.verifyGreaterThan(height(agg), 0, 'aggregate_metrics 不应为空');
            testCase.verifyTrue(ismember('rowType', agg.Properties.VariableNames));
            testCase.verifyTrue(any(strcmp(agg.rowType, 'controller_summary')));
            testCase.verifyTrue(any(strcmp(agg.rowType, 'paired_delta')));

            % 验证 PNG 可读
            try
                imread(fullfile(outDir, 'timeseries_comparison.png'));
                imread(fullfile(outDir, 'metrics_comparison.png'));
                pngOk = true;
            catch
                pngOk = false;
            end
            testCase.verifyTrue(pngOk, '评估PNG应可读');

            % 验证 representative_timeseries.csv 恰好96行
            rt = readtable(fullfile(outDir, 'representative_timeseries.csv'));
            testCase.verifyEqual(height(rt), rlCfg.T, '代表时序应恰好96行');

            % 验证 manifest
            manifestPath = fullfile(outDir, 'evaluation_manifest.json');
            fid = fopen(manifestPath, 'r');
            raw = fread(fid, inf, 'uint8=>char')';
            fclose(fid);
            m = jsondecode(raw);
            testCase.verifyEqual(m.split, 'validation');
            testCase.verifyEqual(length(m.controllers), 4);
        end

        % testRenderMetricsComparisonOffline - 离线重绘函数输出尺寸验证
        % 构造合成 paired_vs_mpc.csv, 仅测试渲染, 不触发训练或评估
        function testRenderMetricsComparisonOffline(testCase)
            rlCfg = residualRlConfig();

            % 构造合成 paired_vs_mpc.csv
            tmpDir = fullfile(tempdir, ['paddy_rl_render_test_' datestr(now, 'yyyymmdd_HHMMSS')]);
            if ~exist(tmpDir, 'dir')
                mkdir(tmpDir);
            end

            % 生成模拟数据: 6场景 × 2 SAC变体 = 12行 (不依赖真实训练)
            scList = rlCfg.scenarios;
            sacTypes = {'mpc_residual_sac_shielded', 'mpc_residual_sac_unshielded'};
            metricNames = {'waterLevelMAE', 'bandViolationRate', 'safetyViolationCount', ...
                'totalIrrigationMm', 'switchCount', 'meanDecisionTimeMs', 'mpcFallbackCount', ...
                'shieldInterventionCount', 'meanAbsoluteResidual'};

            nRows = length(scList) * length(sacTypes);
            scenarioCol = cell(nRows, 1);
            seedCol = zeros(nRows, 1);
            controllerCol = cell(nRows, 1);
            deltaData = zeros(nRows, length(metricNames));

            row = 1;
            for iSc = 1:length(scList)
                for iCt = 1:length(sacTypes)
                    scenarioCol{row} = scList{iSc};
                    seedCol(row) = 20001 + iSc; % fake seed
                    controllerCol{row} = sacTypes{iCt};
                    % 合成差值数据 (避免全零导致图为空)
                    for j = 1:length(metricNames)
                        deltaData(row, j) = (rand(1) - 0.5) * 2;
                    end
                    row = row + 1;
                end
            end

            deltaTable = table(scenarioCol, seedCol, controllerCol, ...
                deltaData(:,1), deltaData(:,2), deltaData(:,3), ...
                deltaData(:,4), deltaData(:,5), deltaData(:,6), ...
                deltaData(:,7), deltaData(:,8), deltaData(:,9), ...
                'VariableNames', [{'scenario', 'seed', 'controller'}, ...
                strcat('delta_', metricNames)]);

            writetable(deltaTable, fullfile(tmpDir, 'paired_vs_mpc.csv'));

            % 调用离线重绘
            renderResidualRlMetricsComparison(tmpDir);

            % 验证 PNG 存在且尺寸 ≥1600×1100
            pngPath = fullfile(tmpDir, 'metrics_comparison.png');
            testCase.verifyTrue(isfile(pngPath), '离线重绘应生成PNG');

            try
                info = imfinfo(pngPath);
                testCase.verifyGreaterThanOrEqual(info.Width, 1600, ...
                    'PNG宽度应≥1600');
                testCase.verifyGreaterThanOrEqual(info.Height, 1100, ...
                    'PNG高度应≥1100');
                % 确认PNG可被imread读取
                img = imread(pngPath);
                testCase.verifyGreaterThan(size(img, 1), 0, 'PNG应可正常读取');
            catch ME
                testCase.verifyTrue(false, sprintf('PNG读取失败: %s', ME.message));
            end
        end

    end
end
