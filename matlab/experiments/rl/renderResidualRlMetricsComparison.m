% renderResidualRlMetricsComparison - 从 paired_vs_mpc.csv 离线重绘指标对比图
% 参数:
%   evaluationDir - 评估输出目录 (含 paired_vs_mpc.csv, 覆盖生成 metrics_comparison.png)
% 说明:
%   仅读取 CSV, 不重新评估。使用 tiledlayout(3,3) 布局确保 9 个指标全部在画布内。
%   场景名使用短显示名防止标签重叠, 图例置于底部横排不遮挡数据。
%   标题明确正值方向, 中性配色。
function renderResidualRlMetricsComparison(evaluationDir)
    % 读取 paired_vs_mpc.csv
    csvPath = fullfile(evaluationDir, 'paired_vs_mpc.csv');
    if ~isfile(csvPath)
        error('renderResidualRlMetricsComparison:missingCSV', ...
            '未找到 paired_vs_mpc.csv: %s', csvPath);
    end
    pd = readtable(csvPath);

    % 场景短显示名映射
    shortNames = containers.Map();
    shortNames('dry')                = 'Dry';
    shortNames('intermittent_rain')  = 'Int.Rain';
    shortNames('heavy_rain')         = 'HvyRain';
    shortNames('infiltration_shift') = 'Inf.Shift';
    shortNames('sensor_noise')       = 'Sens.Noise';
    shortNames('actuator_delay')     = 'Act.Delay';

    scList = unique(pd.scenario, 'stable');
    deltaFields = pd.Properties.VariableNames;
    deltaFields = setdiff(deltaFields, {'scenario', 'seed', 'controller'}, 'stable');
    sacCtrls = unique(pd.controller, 'stable');

    % 显式排列控制器顺序: shielded 在前, unshielded 在后 (保证图例顺序稳定)
    ctrlOrdered = cell(1, 2);
    for i = 1:length(sacCtrls)
        if contains(sacCtrls{i}, 'unshielded') || contains(sacCtrls{i}, 'unshield')
            ctrlOrdered{2} = sacCtrls{i};
        else
            ctrlOrdered{1} = sacCtrls{i};
        end
    end
    legendLabels = {'SAC (shielded)', 'SAC (unshielded)'};

    % 中性配色: 蓝/橙 (不暗示好坏方向)
    colors = [0.20 0.45 0.75;  0.85 0.45 0.20];

    % tiledlayout 3x3, 至少 1600x1100
    fig = figure('Position', [50, 50, 1600, 1100], 'Visible', 'off');
    t = tiledlayout(3, 3, 'TileSpacing', 'compact', 'Padding', 'compact');

    firstBars = [];  % 记录第一个子图的 Bar 对象供图例绑定

    for i = 1:length(deltaFields)
        ax = nexttile;
        hold on;

        % 构造 nScenario × 2 均值矩阵, 一次 grouped bar 绘制并列柱
        meansMatrix = zeros(length(scList), 2);
        for j = 1:length(scList)
            for ic = 1:2
                mask = strcmp(pd.scenario, scList{j}) & strcmp(pd.controller, ctrlOrdered{ic});
                if any(mask)
                    meansMatrix(j, ic) = mean(pd{mask, deltaFields{i}});
                end
            end
        end
        b = bar(ax, 1:length(scList), meansMatrix, 'grouped');
        for ic = 1:2
            b(ic).FaceColor = colors(ic, :);
            b(ic).FaceAlpha = 0.75;
        end
        if i == 1
            firstBars = b;
        end

        % 短场景标签
        shortScNames = cell(size(scList));
        for j = 1:length(scList)
            if isKey(shortNames, scList{j})
                shortScNames{j} = shortNames(scList{j});
            else
                shortScNames{j} = scList{j};
            end
        end
        set(ax, 'XTick', 1:length(scList), 'XTickLabel', shortScNames);
        xtickangle(ax, 25);

        % 指标短显示名
        metricLabel = strrep(deltaFields{i}, 'delta_', '');
        metricLabel = strrep(metricLabel, '_', '\_');
        title(ax, metricLabel, 'Interpreter', 'none', 'FontSize', 9);
        ylabel(ax, '\Delta vs MPC', 'FontSize', 8);
        grid(ax, 'on');
        set(ax, 'FontSize', 8);
    end

    % 图例: tiledlayout south 全局横排, 不遮挡数据; 绑定 Bar 对象保证顺序
    lg = legend(firstBars, legendLabels{:}, 'Orientation', 'horizontal', 'FontSize', 10);
    lg.Layout.Tile = 'south';

    % 总标题
    title(t, 'SAC vs MPC 指标差值 — 正值表示 SAC 指标高于 MPC', ...
        'FontSize', 14, 'FontWeight', 'bold');

    % 强制 figure 尺寸精确为指定值 (某些平台 saveas 依赖 Position)
    set(fig, 'PaperPositionMode', 'auto');

    % 覆盖保存
    saveas(fig, fullfile(evaluationDir, 'metrics_comparison.png'));
    close(fig);
end
