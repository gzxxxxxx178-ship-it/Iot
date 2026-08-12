# RAG检索实验验证记录

## 概述

- **运行模式**: confirmatory
- **时间戳**: 2026-08-12 10:10:05
- **检索器数量**: 2
- **阈值选择策略**: balanced_abstention_accuracy 为主指标，false_answer_rate 和阈值更高为并列因子

## 各检索器题数与Test集指标

### bm25

- **选定阈值**: 27.31768531
- **Validation题数**: 16（域内 11 + OOD 5）
- **Test题数**: 16（域内 10 + OOD 6）

| 指标 | 值 |
|------|-----|
| abstention_accuracy | 1.000000 |
| balanced_abstention_accuracy | 1.000000 |
| citation_coverage | 0.769231 |
| citation_id_validity | 1.000000 |
| citation_precision | 0.833333 |
| false_abstain_rate | 0.000000 |
| false_answer_rate | 0.000000 |
| in_domain_answer_recall | 1.000000 |
| mrr@1 | 1.000000 |
| mrr@10 | 1.000000 |
| mrr@3 | 1.000000 |
| ndcg@1 | 1.000000 |
| ndcg@10 | 1.000000 |
| ndcg@3 | 1.000000 |
| ood_recall | 1.000000 |
| recall@1 | 0.883333 |
| recall@10 | 1.000000 |
| recall@3 | 1.000000 |

### hybrid

- **选定阈值**: 0.03114754
- **Validation题数**: 16（域内 11 + OOD 5）
- **Test题数**: 16（域内 10 + OOD 6）

| 指标 | 值 |
|------|-----|
| abstention_accuracy | 0.875000 |
| balanced_abstention_accuracy | 0.833333 |
| citation_coverage | 1.000000 |
| citation_id_validity | 1.000000 |
| citation_precision | 0.520000 |
| false_abstain_rate | 0.000000 |
| false_answer_rate | 0.333333 |
| in_domain_answer_recall | 1.000000 |
| mrr@1 | 0.900000 |
| mrr@10 | 0.950000 |
| mrr@3 | 0.950000 |
| ndcg@1 | 0.900000 |
| ndcg@10 | 0.955065 |
| ndcg@3 | 0.955065 |
| ood_recall | 0.666667 |
| recall@1 | 0.783333 |
| recall@10 | 1.000000 |
| recall@3 | 1.000000 |

## 说明

- **跨retriever运行总行数**: 64（每个retriever对相同validation/test题目独立评估产生独立行，不等于去重题数）
- **模式**: confirmatory

## 安全与限制声明

1. 所有检索指标仅反映本系统在人工构造小样本基准上的表现，**不代表**LLM生成质量或田间有效性。
2. 评测数据为项目自建小样本基准，标注"人工构造/非公开行业基准"。
3. RAG系统的引用、检索和拒答机制降低了不可追溯回答的风险，但**不声称消除幻觉**。
4. 回答生成使用确定性extractive/template方式，不调用LLM。
5. 知识块内容为已核查来源的中文摘要，不编造作者、标准、数据或DOI。
6. confirmatory全量16题test每个retriever只执行一次，**阈值只由16题validation选择，不按confirmatory test调参**。开发期smoke曾重复使用固定4题test子集做管道/安全边界检查，因此该基准不是严格盲测；最终指标属于项目内部冻结小样本评估。
7. **当前不是LLM答案事实正确率评测**；所有指标（包括citation_precision）均为检索与拒答行为指标，不得伪称自动指标等于事实正确率。

## 拒答策略

- 阈值选择：在validation集上最大化balanced_abstention_accuracy（OOD recall与in-domain answer recall的均值）
- 控制请求：检测动作词+设备词组合，**最高优先级**拒答（不受阈值影响）
- 地域外推：检测跨省份/地区标准应用，**最高优先级**拒答（不受阈值影响）
- 证据不足：包括区域处方缺失、仿真非田间实测、标准条款未收录、生育期阈值缺失、未覆盖工况、LLM指标误问等子类
- 证据冲突：仅审查实际将用于回答的top证据，且仅当同一测量维度上数值不相容时标记
