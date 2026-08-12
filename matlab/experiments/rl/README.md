# 残差SAC强化学习实验

在MPC基线底座上, 使用 MATLAB Reinforcement Learning Toolbox 的连续动作SAC (Soft Actor-Critic) 学习有界残差, 叠加到MPC基础灌溉量上。规则安全屏蔽对最终动作拥有否决权。

**重要声明**: 所有结果均来自合成软件在环试验, 不声称节水率、增产率或田间有效性。

## 算法设计

### 控制架构

```
观测(6×1) → SAC Agent → 残差∈[-2,2]mm/步
                              ↓
MPC基础动作(quadprog) → 相加 → 安全屏蔽 → 最终动作∈[0,8]mm/步
```

- **MPC**: 滚动时域QP, 使用名义入渗/增益/延迟, 在线性化水量模型上求解
- **SAC**: 连续动作强化学习, 输出残差修正MPC的不足 (参数失配、延迟等)
- **安全屏蔽**: 确定性规则, 在水位接近安全边界时否决/修正最终动作

### 观测 (6×1, 全归一化钳制)

| 维度 | 含义 | 归一化 | 钳制 |
|------|------|--------|------|
| 1 | 归一化水位误差 | (obs_wl - target) / target | [-2, 2] |
| 2 | 上一最终动作 | prevAction / actionMax | [0, 1] |
| 3 | 当前MPC基础动作 | mpcAction / actionMax | [0, 1] |
| 4 | 当前降雨 | rainfall / 10 | [0, 1] |
| 5 | 当前蒸散 | ET / 1.0 | [0, 1] |
| 6 | 上一残差 | prevResidual / 2 | [-1, 1] |

**禁止输入**: actualInfiltration, actuatorGain, delaySteps, trueWaterLevel (不可观测真值)

### 动作空间

连续动作: 1维, 残差 ∈ [-2, 2] mm/步

### 奖励方程 (负加权和)

```
r = -( w₁ * ((h - h_target) / h_target)²          [水位跟踪误差]
     + w₂ * (a / a_max)                             [灌溉量代价]
     + w₃ * |a - a_prev| / a_max                    [动作变化率代价]
     + w₄ * I(h ∉ [bandLow, bandHigh])              [性能区间越界]
     + w₅ * I(h ∉ [safetyLow, safetyHigh])          [安全区间越界]
     + w₆ * I(shield_intervened) )                  [屏蔽干预代价]
```

其中 I(·) 为指示函数, h 为真实水位, a 为最终灌溉量。

默认权重: w₁=1.0, w₂=0.1, w₃=0.05, w₄=2.0, w₅=10.0, w₆=1.0

### 安全屏蔽规则 (确定性, 按优先级)

1. **高水位防溢流**: 观测水位 ≥ safetyHigh - 10 mm → 强制灌溉量 = 0
2. **低水位防干涸**: 观测水位 ≤ safetyLow + 5 mm → 保证灌溉 ≥ min(ruleIrrigationRate, actionMax)
3. **变化率限制**: 每步动作变化 ≤ 2 mm/步
4. **边界钳制**: finalAction ∈ [actionMin, actionMax]

## Seed分区

| 分区 | 范围 | 数量 | 用途 |
|------|------|------|------|
| 训练 | 10001 : 10100 | 100 | SAC训练 (轮转采样6场景) |
| 验证 | 20001 : 20010 | 10 | 超参选择 (不用于训练) |
| 最终测试 | 30001 : 30030 | 30 | 最终评估 (仅运行一次) |

三组互斥, 且均不与MPC基线使用的seed 1-30重叠。
Agent随机种子: `20260811`

## 目录结构

```
matlab/experiments/rl/
├── README.md                        # 本文件
├── residualRlConfig.m               # RL独立配置
├── createResidualRlEnvironment.m     # rlFunctionEnv环境
├── createResidualSacAgent.m          # SAC agent构建
├── buildResidualObservation.m        # 6×1观测构建
├── applySafetyShield.m              # 安全屏蔽函数
├── trainResidualSac.m               # 训练入口
├── evaluateResidualSac.m            # 评估入口 (4控制器对比)
├── renderResidualRlMetricsComparison.m # 冻结CSV离线重绘
└── runResidualRlPipeline.m          # 全流程编排
```

## 运行命令

```bash
# 单元测试
/Applications/MATLAB_R2025a.app/bin/matlab -batch "cd('/Volumes/out/Projects/DS-workplace/matlab/experiments'); addpath(genpath('.')); results=runtests('tests/testResidualRl.m'); assertSuccess(results);"

# Smoke模式 (快速验证全链路)
/Applications/MATLAB_R2025a.app/bin/matlab -batch "cd('/Volumes/out/Projects/DS-workplace/matlab/experiments'); addpath(genpath('.')); out=runResidualRlPipeline('smoke', fullfile(tempdir,'paddy-residual-rl-smoke')); disp(out);"

# Confirmatory模式 (600回合训练+验证+测试)
/Applications/MATLAB_R2025a.app/bin/matlab -batch "cd('/Volumes/out/Projects/DS-workplace/matlab/experiments'); addpath(genpath('.')); out=runResidualRlPipeline('confirmatory'); disp(out);"
```

### 各阶段单独运行

```matlab
% 仅训练
trainOut = trainResidualSac('smoke');
% 仅评估 (使用已训练agent)
evalOut = evaluateResidualSac('path/to/trained_agent.mat', 'test');
```

## 训练输出

| 文件 | 内容 |
|------|------|
| `trained_agent.mat` | 训练好的rlSACAgent |
| `training_stats.csv` | 每回合奖励、步数、移动平均 |
| `training_curve.png` | 回合奖励曲线图 |
| `training_manifest.json` | 训练参数、seed分区、奖励权重、MATLAB版本、时间戳 |

## 评估输出

| 文件 | 内容 |
|------|------|
| `episode_metrics.csv` | 四控制器×所有场景×所有seed的逐回合指标 |
| `paired_vs_mpc.csv` | SAC(屏蔽/未屏蔽) vs MPC 的指标差值 |
| `aggregate_metrics.csv` | 统一长表schema (controller_summary + paired_delta) |
| `representative_timeseries.csv` | 第一个配对的完整四控制器时序 |
| `timeseries_comparison.png` | 水位/动作/残差/屏蔽活动四面板对比图 |
| `metrics_comparison.png` | 各场景SAC vs MPC指标差值柱状图 |
| `evaluation_manifest.json` | 评估配置、seed范围、MATLAB版本 |

### 评估指标

| 指标 | 含义 |
|------|------|
| waterLevelMAE | 水位与目标平均绝对误差 (mm) |
| bandViolationRate | 水位在性能区间 [20,60] mm外的步数占比 |
| safetyViolationCount | 连续安全越界事件数 |
| totalIrrigationMm | 总有效灌溉量 (mm) |
| switchCount | 灌溉开关切换次数 |
| meanDecisionTimeMs | 平均决策耗时 (ms) |
| mpcFallbackCount | MPC QP回退次数 |
| shieldInterventionCount | 安全屏蔽干预步数 (新增) |
| meanAbsoluteResidual | 平均绝对残差 (mm/步, 新增) |

## 真实性边界

- 所有实验均使用合成扰动序列 (`paddyexp.generateEpisode`)
- 水量模型为合成SIL, 未经验证的土壤/作物/气候参数
- MPC获得预测窗口内的完美降雨/ET预报 (短时域完美预报假设), 可能高估MPC性能
- SAC的观测和奖励均基于仿真内部信息, 不含实际田间传感器误差
- 安全屏蔽阈值基于合成安全区间, 非农艺标定
- 结果仅用于控制器方法比较, 不构成对田间节水、增产或控制优效的结论
- 所有seed和参数固定后不再修改; 不得根据最终测试结果反向调整奖励或网络

## 冻结确认性结果（2026-08-12）

确认性实验采用600个训练回合（57,600个环境步），随后使用验证seed `20001:20010` 和最终测试seed `30001:30030`。最终测试仅运行一次，共包含6类场景×30个seed×4种控制器=720次运行。完整产物位于 `results/pipeline_confirmatory_20260812_083550/`。

该次SAC训练是真实可复现的，但最终模型没有改善MPC，应作为失败对照而非性能优势证据。测试集上，安全屏蔽版相对MPC的水位MAE平均增加5.653 mm（标准差6.737 mm），178/180个配对更差；平均总灌溉量增加10.588 mm，170/180个配对更高。未屏蔽版水位MAE平均增加6.243 mm，180/180个配对均更差；平均总灌溉量增加20.517 mm。四类控制器的安全越界事件均为0，因此本实验不能证明SAC带来额外安全收益。

安全屏蔽仍体现出风险抑制作用：相对于未屏蔽SAC，它降低了平均水位MAE与灌溉量，并在180个测试回合中累计干预2,075步；但屏蔽版仍显著劣于原始MPC。申报材料可据此证明平台具备“训练—安全约束—独立评估—失败识别”的完整实验能力，不应表述为“强化学习优于MPC”。

## 已知限制

- SAC训练仅使用6个合成场景的固定参数集, 未覆盖结构化参数不确定性
- 观测不含入渗、执行器增益和延迟的真值, SAC须从水位误差中隐式推断
- 未进行预报噪声消融 (MPC短时域完美预报假设未受挑战)
- 未与PID、模糊控制等替代控制器比较
- 当前SAC奖励和状态设计在重雨、间歇降雨场景中出现明显退化；后续研究应重新设计为更保守的残差门控或MPC优势约束，并使用新的验证/测试seed重新预注册实验，不能在本次测试结果上继续调参后沿用相同测试集作无偏结论
