# MATLAB 冻结实验发布器

## 概述

`publishFrozenExperimentResults.m` 读取三个冻结实验目录的确认性结果，构造结构化实验运行并通过 POST `/api/research/experiments` 上传到 Java 后端。所有关键指标从实际 CSV/JSON 文件中提取计算，不含硬编码数值。

## 读取的文件与 Schema

### MPC 实验

| 文件 | 来源目录 | Schema |
|------|----------|--------|
| `aggregate_metrics.csv` | `matlab/experiments/results/mpc_baseline_confirmatory_20260811_164430/` | 列: `rowType, controller, scenario, metric, n, mean, std, min, max, positiveRatio, improvementRatio, lowerIsBetter`。rowType 过滤 `controller_summary` / `paired_delta` |
| `paired_deltas.csv` | 同上目录 | 列: `scenario, seed, delta_waterLevelMAE, delta_bandViolationRate, ...`（每种子一行） |
| `manifest.json` | 同上目录 | `config.mpcHorizon`, `nSeedsPerScenario`, `scenarios[]` 等 |

### RL 实验（读取 test 子目录）

| 文件 | 来源目录 | Schema |
|------|----------|--------|
| `aggregate_metrics.csv` | `.../test/evaluation_test_20260812_091440/` | 同 MPC 格式；过滤 `controller_summary` 和 `mpc_residual_sac_shielded` |
| `paired_vs_mpc.csv` | 同上目录 | 列: `scenario, seed, controller, delta_waterLevelMAE, delta_bandViolationRate, ..., shieldInterventionCount` |

### RAG 实验

| 文件 | 来源目录 | Schema |
|------|----------|--------|
| `retrieval_metrics.csv` | `rag/results/rag_experiment_20260812_101005/` | 列: `retriever, abstention_accuracy, false_answer_rate, citation_precision, recall@1, recall@3, mrr@1, ...`。按 `retriever` 筛选 `bm25`/`hybrid` |
| `rag_manifest.json` | 同上目录 | `retrievers.bm25.threshold`, `retrievers.hybrid.threshold` 等 |

## Schema Fail-Fast 行为

函数在执行任何上传（含 dry-run）前对每个实验阶段执行：

1. **缺文件**: 任一必需文件不存在 → `assert` 报错，立即终止，不生成任何 payload
2. **空数据**: 读取后行为空表 → `assert` 报错终止
3. **缺列/行**: 按 `retriever` / `controller` / `metric` 筛选后无匹配行 → `assert` 报错终止（如 RAG 缺失 `bm25` 或 `hybrid` 行）
4. **非有限值**: 所有从文件读取并参与指标计算的数字经 `isfinite()` 校验；NaN/Inf/-Inf → `assert` 报错终止
5. **种子数不一致**: RL `aggregate_metrics.csv` 中 `n` 列非单一值 → `assert` 报错终止

这些检查确保不会将无效数据上传到后端数据库。

## 冻结实验

| 实验 | 目录 | 状态 | 关键结论 |
|------|------|------|----------|
| MPC基线 | `matlab/experiments/results/mpc_baseline_confirmatory_20260811_164430` | MIXED | 水位MAE改善29.1%，但灌溉量增加 |
| 残差SAC | `matlab/experiments/rl/results/pipeline_confirmatory_20260812_083550` | FAILED | SAC全指标劣于MPC |
| RAG检索 | `rag/results/rag_experiment_20260812_101005` | MIXED | BM25优秀(0误答)，hybrid失败(33%误答) |

## 使用方式

### Dry-run（默认，不联网）

```matlab
% 在 MATLAB 中运行
addpath('matlab/integration');
results = publishFrozenExperimentResults();  % 默认 dryRun=true
% 或显式指定
results = publishFrozenExperimentResults(dryRun=true);
```

返回的 `results` 数组中每项含 `payload` 字段，可检查构造的 payload 内容。

### 联网发布（需后端运行 + 有效token）

```matlab
results = publishFrozenExperimentResults( ...
    baseUrl='http://localhost:8080', ...
    authToken='<YOUR_JWT_TOKEN>', ...
    dryRun=false);
```

## 真实性边界

1. **不重跑实验**: 本脚本仅读取已有结果文件，不重新执行仿真或评估。
2. **从文件读取指标**: 所有指标值来自冻结的 CSV/JSON 文件，通过 `readtable` 和 `jsondecode` 提取，不硬编码数值。
3. **合成仿真**: 所有实验均为MATLAB合成软件在环，不可声称田间实测。
4. **RL失败如实报告**: SAC状态标记为FAILED，不包装为优势。
5. **MPC用水trade-off**: 水位改善与灌溉增加两个方向均如实呈现。
6. **RAG双检索器**: BM25和hybrid指标分别保留，不合并为单一数值。

## 路径说明

函数按 `mfilename('fullpath')` 自动计算项目根目录，不硬编码绝对路径（如 `/Volumes/...`）。

## 依赖

- MATLAB R2019b 或更高（用于 `arguments` 块语法和 `readtable` `VariableNamingRule` 选项）
- 仅需联网时需 `webwrite`（MATLAB 内置，无需额外工具箱）
- dry-run 模式无需任何网络连接
