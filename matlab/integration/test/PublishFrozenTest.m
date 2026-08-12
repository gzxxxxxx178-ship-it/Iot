classdef PublishFrozenTest < matlab.unittest.TestCase
    % PUBLISHFROZENTEST 冻结实验发布器单元测试
    %
    %   验证 dry-run 模式下三条 payload 的 runKey、状态、
    %   引用内容及与直接readtable读取值的比对。不做网络调用。

    methods (Test)

        % 验证 dry-run 返回三条 run，且含 payload 字段
        function testDryRunReturnsThreeRuns(testCase)
            results = publishFrozenExperimentResults(dryRun=true);
            testCase.verifyEqual(numel(results), 3, ...
                'dry-run应返回3条run结果');
            for i = 1:numel(results)
                testCase.verifyTrue(results(i).dryRun, ...
                    '所有结果应标记为dry-run');
                testCase.verifyTrue(isfield(results(i), 'payload'), ...
                    sprintf('第%d条结果应包含payload字段', i));
            end
        end

        % 验证 MPC 载荷：runKey和status
        function testMpcPayloadFields(testCase)
            results = publishFrozenExperimentResults(dryRun=true);
            mpcResult = results(1);
            testCase.verifyEqual(mpcResult.runKey, ...
                'mpc-baseline-confirmatory-20260811-164430', ...
                'MPC runKey应固定且可追溯');
            testCase.verifyEqual(mpcResult.payload.status, 'MIXED', ...
                'MPC状态应为MIXED');
            testCase.verifyEqual(mpcResult.payload.experimentType, 'MPC');
            testCase.verifyEqual(mpcResult.payload.sourceType, 'SIMULATION');
            % 验证指标数量
            testCase.verifyTrue(size(mpcResult.payload.metrics, 2) >= 8, ...
                'MPC应至少有8条指标');
        end

        % 验证 RL 载荷：runKey和失败状态
        function testRlPayloadFields(testCase)
            results = publishFrozenExperimentResults(dryRun=true);
            rlResult = results(2);
            testCase.verifyEqual(rlResult.runKey, ...
                'residual-sac-confirmatory-20260812-083550', ...
                'RL runKey应固定');
            testCase.verifyEqual(rlResult.payload.status, 'FAILED', ...
                'RL状态应为FAILED');
            testCase.verifyEqual(rlResult.payload.experimentType, 'RL');
            testCase.verifyEqual(rlResult.payload.sourceType, 'SIMULATION');
            % 验证RL摘要不含双百分号且包含单个百分号回合更差
            testCase.verifyTrue(isempty(strfind(rlResult.payload.resultSummary, '%%')), ...
                'RL摘要不应包含连续%%');
            testCase.verifyNotEmpty(strfind(rlResult.payload.resultSummary, '%回合更差'), ...
                'RL摘要应包含单个%%回合更差');
        end

        % 验证 RAG 载荷：包含BM25和hybrid双检索器指标
        function testRagPayloadHasBothRetrievers(testCase)
            results = publishFrozenExperimentResults(dryRun=true);
            ragResult = results(3);
            testCase.verifyEqual(ragResult.runKey, ...
                'rag-experiment-20260812-101005', ...
                'RAG runKey应固定');
            testCase.verifyEqual(ragResult.payload.status, 'MIXED');
            testCase.verifyEqual(ragResult.payload.experimentType, 'RAG');
            testCase.verifyEqual(ragResult.payload.sourceType, 'INTERNAL_BENCHMARK');

            % 检查指标中包含BM25和hybrid方法
            hasBm25 = false;
            hasHybrid = false;
            metrics = ragResult.payload.metrics;
            for i = 1:numel(metrics)
                method = metrics{i}.methodName;
                if strcmp(method, 'BM25'), hasBm25 = true; end
                if strcmp(method, 'hybrid'), hasHybrid = true; end
            end
            testCase.verifyTrue(hasBm25, 'RAG指标应包含BM25');
            testCase.verifyTrue(hasHybrid, 'RAG指标应包含hybrid');
        end

        % 验证 MPC payload指标与直接readtable读取值比对（容差1e-4）
        function testMpcPayloadVsReadtable(testCase)
            results = publishFrozenExperimentResults(dryRun=true);
            payload = results(1).payload;

            % 直接读取aggregate_metrics.csv
            funcPath = mfilename('fullpath');
            [funcDir, ~, ~] = fileparts(funcPath);
            projectRoot = fullfile(funcDir, '..', '..', '..');
            mpcAggFile = fullfile(projectRoot, 'matlab', 'experiments', ...
                'results', 'mpc_baseline_confirmatory_20260811_164430', ...
                'aggregate_metrics.csv');
            mpcAgg = readtable(mpcAggFile, 'Delimiter', ',', 'VariableNamingRule', 'preserve');
            isCs = strcmp(mpcAgg.rowType, 'controller_summary');
            mpcRows = isCs & strcmp(mpcAgg.controller, 'mpc');
            expectedMpcWlMae = mean(mpcAgg.mean( ...
                mpcRows & strcmp(mpcAgg.metric, 'waterLevelMAE')));

            % 在payload metrics中找到MPC水位MAE
            metrics = payload.metrics;
            payloadMpcWlMae = NaN;
            for i = 1:numel(metrics)
                m = metrics{i};
                if strcmp(m.methodName, 'MPC') && strcmp(m.metricName, '水位MAE')
                    payloadMpcWlMae = m.metricValue;
                    break;
                end
            end
            testCase.verifyEqual(payloadMpcWlMae, expectedMpcWlMae, ...
                'AbsTol', 1e-4, 'MPC水位MAE应与aggregate_metrics.csv一致');

            % 比对灌溉水量差的配对delta
            isPd = strcmp(mpcAgg.rowType, 'paired_delta');
            expectedDeltaIrr = mean(mpcAgg.mean(isPd & ...
                strcmp(mpcAgg.metric, 'delta_totalIrrigationMm')));
            payloadDeltaIrr = NaN;
            for i = 1:numel(metrics)
                m = metrics{i};
                if strcmp(m.methodName, 'MPC-规则') && strcmp(m.metricName, '灌溉水量差')
                    payloadDeltaIrr = m.metricValue;
                    break;
                end
            end
            testCase.verifyEqual(payloadDeltaIrr, expectedDeltaIrr, ...
                'AbsTol', 1e-4, '灌溉水量差应与aggregate_metrics.csv一致');
        end

        % 验证 RL payload指标与直接readtable读取值比对（容差1e-3）
        function testRlPayloadVsReadtable(testCase)
            results = publishFrozenExperimentResults(dryRun=true);
            payload = results(2).payload;

            % 直接读取RL test aggregate_metrics.csv
            funcPath = mfilename('fullpath');
            [funcDir, ~, ~] = fileparts(funcPath);
            projectRoot = fullfile(funcDir, '..', '..', '..');
            rlAggFile = fullfile(projectRoot, 'matlab', 'experiments', 'rl', ...
                'results', 'pipeline_confirmatory_20260812_083550', ...
                'test', 'evaluation_test_20260812_091440', ...
                'aggregate_metrics.csv');
            rlAgg = readtable(rlAggFile, 'Delimiter', ',', 'VariableNamingRule', 'preserve');
            isCs = strcmp(rlAgg.rowType, 'controller_summary');
            sacRows = isCs & strcmp(rlAgg.controller, 'mpc_residual_sac_shielded');
            expectedSacWlMae = mean(rlAgg.mean( ...
                sacRows & strcmp(rlAgg.metric, 'waterLevelMAE')));

            metrics = payload.metrics;
            payloadWlMae = NaN;
            for i = 1:numel(metrics)
                m = metrics{i};
                if strcmp(m.methodName, 'SAC+安全屏蔽') && strcmp(m.metricName, '水位MAE')
                    payloadWlMae = m.metricValue;
                    break;
                end
            end
            testCase.verifyEqual(payloadWlMae, expectedSacWlMae, ...
                'AbsTol', 1e-3, 'SAC水位MAE应与aggregate_metrics.csv一致');

            % 比对安全屏蔽干预数
            sacShieldMean = sum(rlAgg.mean( ...
                sacRows & strcmp(rlAgg.metric, 'shieldInterventionCount')));
            expectedShieldTotal = round(sacShieldMean * 30);
            payloadShield = NaN;
            for i = 1:numel(metrics)
                m = metrics{i};
                if strcmp(m.methodName, 'SAC') && strcmp(m.metricName, '安全屏蔽干预')
                    payloadShield = m.metricValue;
                    break;
                end
            end
            testCase.verifyEqual(payloadShield, expectedShieldTotal, ...
                '安全屏蔽干预数应与aggregate_metrics.csv一致');
        end

        % 验证 RAG payload指标与直接readtable读取值比对
        function testRagPayloadVsReadtable(testCase)
            results = publishFrozenExperimentResults(dryRun=true);
            payload = results(3).payload;

            % 直接读取retrieval_metrics.csv
            funcPath = mfilename('fullpath');
            [funcDir, ~, ~] = fileparts(funcPath);
            projectRoot = fullfile(funcDir, '..', '..', '..');
            ragMetricsFile = fullfile(projectRoot, 'rag', 'results', ...
                'rag_experiment_20260812_101005', 'retrieval_metrics.csv');
            ragTbl = readtable(ragMetricsFile, 'Delimiter', ',', 'VariableNamingRule', 'preserve');
            bm25Row = strcmp(ragTbl.retriever, 'bm25');
            expectedBm25CitePrec = ragTbl.citation_precision(bm25Row);

            metrics = payload.metrics;
            payloadCitePrec = NaN;
            for i = 1:numel(metrics)
                m = metrics{i};
                if strcmp(m.methodName, 'BM25') && strcmp(m.metricName, '引用精确率')
                    payloadCitePrec = m.metricValue;
                    break;
                end
            end
            testCase.verifyEqual(payloadCitePrec, expectedBm25CitePrec, ...
                'AbsTol', 1e-6, 'BM25引用精确率应与retrieval_metrics.csv一致');

            % 比对hybrid误答率
            hybridRow = strcmp(ragTbl.retriever, 'hybrid');
            expectedHybridFalseAns = ragTbl.false_answer_rate(hybridRow);
            payloadFalseAns = NaN;
            for i = 1:numel(metrics)
                m = metrics{i};
                if strcmp(m.methodName, 'hybrid') && strcmp(m.metricName, '误答率')
                    payloadFalseAns = m.metricValue;
                    break;
                end
            end
            testCase.verifyEqual(payloadFalseAns, expectedHybridFalseAns, ...
                'AbsTol', 1e-6, 'hybrid误答率应与retrieval_metrics.csv一致');
        end

        % 验证函数路径解析不硬编码绝对路径
        function testDoesNotUseHardcodedVolumesPath(testCase)
            funcPath = which('publishFrozenExperimentResults');
            fid = fopen(funcPath, 'r');
            content = fread(fid, '*char')';
            fclose(fid);
            testCase.verifyEqual(isempty(strfind(content, '/Volumes/')), true, ...
                '不应硬编码/Volumes/路径');
        end

        % 验证README和集成文档不使用struct()调用模式
        function testDocsDoNotUseStructCallPattern(testCase)
            funcPath = mfilename('fullpath');
            [testDir, ~, ~] = fileparts(funcPath);
            integrationDir = fileparts(testDir);  % test/ → integration/
            projectRoot = fullfile(testDir, '..', '..', '..');

            % 检查 matlab/integration/README.md
            readmePath = fullfile(integrationDir, 'README.md');
            testCase.verifyTrue(exist(readmePath, 'file') == 2, ...
                sprintf('README.md应存在: %s', readmePath));
            fid = fopen(readmePath, 'r');
            readmeContent = fread(fid, '*char')';
            fclose(fid);
            testCase.verifyTrue( ...
                isempty(strfind(readmeContent, 'publishFrozenExperimentResults(struct(')), ...
                'README.md不应包含struct()调用模式');

            % 检查 docs/integration-research-workbench.md
            docPath = fullfile(projectRoot, 'docs', ...
                'integration-research-workbench.md');
            testCase.verifyTrue(exist(docPath, 'file') == 2, ...
                sprintf('集成文档应存在: %s', docPath));
            fid = fopen(docPath, 'r');
            docContent = fread(fid, '*char')';
            fclose(fid);
            testCase.verifyTrue( ...
                isempty(strfind(docContent, 'publishFrozenExperimentResults(struct(')), ...
                '集成文档不应包含struct()调用模式');
        end
    end
end
