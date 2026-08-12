# 稻田控制实验底座 & MPC基线

可复现的软件在环（SIL）稻田水位控制实验环境。预生成多场景降雨、蒸散、入渗、执行器滞后和传感器噪声序列，使规则控制和线性MPC在完全相同的扰动下运行，输出原始时序、逐回合指标、聚合结果和对比图。

**重要声明**: 所有结果均来自合成软件在环试验，**不声称节水率、增产率或田间有效性**。

## 依赖

- MATLAB R2025a（或更高）
- Optimization Toolbox（`quadprog` 用于MPC的QP求解）

## 模型方程

### 水量平衡（单位：mm/步，步长默认30分钟）

```
h[t] = h[t-1] + P[t] - ET[t] - I[t] + u_eff[t] - D[t]
```

| 符号 | 含义 | 单位 |
|------|------|------|
| h[t] | 田面水深 | mm |
| P[t] | 降雨量 | mm/步 |
| ET[t] | 蒸散量 | mm/步 |
| I[t] | 实际入渗量 | mm/步 |
| u_eff[t] | 有效灌溉量 = action[t - delaySteps] × gain[t] (纯延迟队列) | mm/步 |
| D[t] | 排水量 = min(maxDrainage, h[t] - safetyHigh) 当 h[t] > safetyHigh | mm/步 |

其中 `delaySteps` 为**每回合恒定的标量**（纯延迟步数），而非时变序列。
`actionHistory` 在 `stepDynamics` 中作为纯延迟队列使用：当前步指令存入队尾，
延迟 `delaySteps` 步后在队首取出作为有效灌溉量。

### 参数设定（合成仿真设定，非农艺处方）

| 参数 | 值 | 说明 |
|------|-----|------|
| 目标水深 | 40 mm | 控制目标 |
| 性能区间 | [20, 60] mm | 期望水位范围 |
| 安全区间 | [0, 100] mm | 绝对安全边界 |
| 灌溉动作范围 | [0, 8] mm/步 | 连续有界控制 |
| 默认回合长度 | 96步 (48小时) | — |

### 观测模型

```
obs[t] = h[t] + noise[t]
```

控制器仅能观测含噪声的水位，不知道真实的入渗率、执行器增益或延迟。

## 目录结构

```
matlab/experiments/
├── +paddyexp/                  # 核心模型包（后续RL复用, 已冻结）
│   ├── config.m                # 默认配置
│   ├── generateEpisode.m       # 预生成外生序列
│   ├── reset.m                 # 初始化回合状态
│   ├── stepDynamics.m          # 一步动力学
│   ├── ruleController.m        # 滞回规则控制器
│   ├── mpcController.m         # 线性MPC控制器
│   └── computeMetrics.m        # 逐回合指标计算
├── rl/                         # 残差SAC强化学习实验 (CTRL-02)
│   ├── README.md               # RL实验文档
│   ├── residualRlConfig.m      # RL独立配置
│   ├── createResidualRlEnvironment.m  # rlFunctionEnv环境
│   ├── createResidualSacAgent.m       # SAC agent构建
│   ├── buildResidualObservation.m     # 6×1观测构建
│   ├── applySafetyShield.m     # 安全屏蔽函数
│   ├── trainResidualSac.m      # 训练入口
│   ├── evaluateResidualSac.m   # 评估入口
│   ├── renderResidualRlMetricsComparison.m # 冻结CSV离线重绘
│   └── runResidualRlPipeline.m # 全流程编排
├── runMpcBaselineExperiment.m  # MPC基线实验入口
├── tests/
│   ├── testExperiment.m        # MPC基线单元测试
│   └── testResidualRl.m        # RL残差实验单元测试
├── results/                    # 运行时生成的输出目录
└── README.md
```

## 六个合成场景

所有场景均为**合成压力测试，不代表实际天气频率**。

| 场景 | 特点 | 压力维度 |
|------|------|----------|
| `dry` | 极少降雨, 高蒸散 | 持续灌溉需求 |
| `intermittent_rain` | 随机降雨脉冲 | 响应能力 |
| `heavy_rain` | 偶发强降雨 | 溢流/安全处理 |
| `infiltration_shift` | 入渗率中段阶跃 | 参数适应 |
| `sensor_noise` | 高幅度测量噪声 | 观测鲁棒性 |
| `actuator_delay` | 长执行器延迟+增益变化 | 延迟补偿 |

## 公平性协议

1. **相同扰动**: 每个 `scenario+seed` 对外生序列（rainfall, ET, actualInfiltration, sensorNoise, actuatorGain, delaySteps）只生成一次；规则控制器和MPC控制器在**完全相同的序列**上运行。
2. **确定性**: 相同 `seed` 重复生成episode结果完全一致 （`rng(seed, 'twister')`）。
3. **规则控制器**: 仅使用当前观测，不读取任何未来扰动数据。
4. **MPC控制器**: 仅使用 `t:t+mpcHorizon-1` 窗口内预生成的降雨和ET；对未知参数（实际入渗、执行器增益、实际延迟）只能使用名义值（`nominalInfiltration=0.5`, `nominalActuatorGain=1.0`, `nominalDelay=0`）。
   当前MPC在有限窗口内获得的是合成降雨和ET真值，等价于“短时域完美预报”假设；该设定可能高估存在预报误差时的MPC性能，后续必须增加预报噪声消融。
5. **QP安全回退**: QP求解失败或输出非有限值时，自动回退至简单规则动作，记录 `mpcFallbackCount`，不中断批处理。
6. **不事后调参**: 实验结果不用于反向修改同一批次的控制器参数。

## 运行命令

```bash
# 单元测试
/Applications/MATLAB_R2025a.app/bin/matlab -batch "cd('/Volumes/out/Projects/DS-workplace/matlab/experiments'); addpath(genpath('.')); results=runtests('tests'); assertSuccess(results);"

# Smoke模式 (6场景×2种子=12对)
/Applications/MATLAB_R2025a.app/bin/matlab -batch "cd('/Volumes/out/Projects/DS-workplace/matlab/experiments'); addpath(genpath('.')); out=runMpcBaselineExperiment('smoke'); disp(out.summary);"

# Confirmatory模式 (6场景×30种子=180对)
/Applications/MATLAB_R2025a.app/bin/matlab -batch "cd('/Volumes/out/Projects/DS-workplace/matlab/experiments'); addpath(genpath('.')); out=runMpcBaselineExperiment('confirmatory'); disp(out.summary);"

# 在MATLAB交互式环境中
cd('/Volumes/out/Projects/DS-workplace/matlab/experiments');
addpath(genpath('.'));
out = runMpcBaselineExperiment('smoke', fullfile(tempdir, 'paddy-mpc-smoke'));
```

## 输出说明

每次运行生成独立的带时间戳输出目录，包含：

| 文件 | 内容 |
|------|------|
| `manifest.json` | 运行模式、MATLAB版本、配置参数、场景列表、seed数、时间戳和免责声明 |
| `episode_metrics.csv` | 每对×控制器的逐回合指标（含 controller, scenario, seed, waterLevelMAE, bandViolationRate, safetyViolationCount, totalIrrigationMm, switchCount, meanDecisionTimeMs, mpcFallbackCount） |
| `paired_deltas.csv` | 每个 scenario+seed 对的 MPC−Rule 指标差值 |
| `aggregate_metrics.csv` | 统一长表schema (`rowType, controller, scenario, metric, n, mean, std, min, max, positiveRatio, improvementRatio, lowerIsBetter`)。`controller_summary` 行为控制器×场景聚合；`paired_delta` 行为 MPC−Rule 配对差值聚合 (positiveRatio=mean(Δ>0), improvementRatio=mean(Δ<0)，所有指标 lowerIsBetter=true) |
| `representative_timeseries.csv` | 第一个配对的完整时序（水位、动作、降雨、ET、入渗） |
| `timeseries_comparison.png` | 代表性场景的水位/动作/扰动三面板对比图 |
| `metrics_comparison.png` | 各场景MPC vs Rule指标差值柱状图 |

### 指标含义

| 指标 | 含义 | Δ正向含义 |
|------|------|-----------|
| waterLevelMAE | 水位与目标的平均绝对误差 (mm) | MPC误差更大 |
| bandViolationRate | 水位在 [20,60] mm 外的步数占比 | MPC违规更多 |
| safetyViolationCount | 连续安全越界事件数 | MPC越界事件更多 |
| totalIrrigationMm | 总有效灌溉量 (mm) | MPC用水更多 |
| switchCount | 灌溉开关切换次数 | MPC切换更频繁 |
| meanDecisionTimeMs | 平均决策耗时 (ms) | MPC耗时更长 |
| mpcFallbackCount | MPC QP回退次数 (规则为0) | MPC回退更多 |

## 结论边界

- 本实验仅比较规则滞回控制与线性MPC在**合成扰动**下的表现，不构成对真实田间控制优劣的结论。
- 所有参数和场景为合成设定，未经田间标定。
- MPC采用有限时域完美降雨/ET预报；未完成预报误差和预报偏差下的稳健性验证。
- 预生成的扰动序列固定后不再修改；控制器参数也固定为上述默认值。
- 不得使用实验结果宣称节水率、增产率或任何田间有效性。

## 后续RL复用点

`+paddyexp` package为后续强化学习提供稳定接口：

- `paddyexp.config()` — 默认配置，RL可通过修改字段适配
- `paddyexp.generateEpisode()` — episode生成，RL训练分离数据生成
- `paddyexp.reset()` — 初始化，RL环境接口
- `paddyexp.stepDynamics()` — step函数，RL环境核心
- `paddyexp.computeMetrics()` — 指标计算，RL评估复用

开发RL控制器时，可直接复用上述函数，避免复制模型代码。

## 残差SAC强化学习实验

`rl/` 目录包含基于 MPC 基线的安全残差 SAC 训练与独立评估实验。详见 [rl/README.md](rl/README.md)。

```bash
# RL 单元测试
/Applications/MATLAB_R2025a.app/bin/matlab -batch "cd('/Volumes/out/Projects/DS-workplace/matlab/experiments'); addpath(genpath('.')); results=runtests('tests/testResidualRl.m'); assertSuccess(results);"

# RL Smoke 模式
/Applications/MATLAB_R2025a.app/bin/matlab -batch "cd('/Volumes/out/Projects/DS-workplace/matlab/experiments'); addpath(genpath('.')); out=runResidualRlPipeline('smoke', fullfile(tempdir,'paddy-residual-rl-smoke')); disp(out);"
```
