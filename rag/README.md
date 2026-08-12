# 水田水管理 RAG 检索实验

可引用、可拒答的水田水管理检索增强生成（RAG）实验。基于纯 Python 3 标准库实现 BM25 + 字符 3-gram TF-IDF 混合检索，template/extractive 方式生成带引用的回答，不含 LLM 调用。

## 目录结构

```
rag/
├── README.md                        # 本文件
├── run_rag_experiment.py            # 实验主脚本
├── data/
│   ├── sources.json                 # 来源注册表（12条已核查来源）
│   ├── chunks.jsonl                 # 知识块语料（20条中文摘要）
│   └── eval_questions.jsonl         # 评估问题（32题，validation/test 分区）
├── src/
│   ├── __init__.py
│   ├── tokenizer.py                 # 中文bigram/英文数字词项分词器
│   ├── bm25.py                      # BM25 检索算法
│   ├── tfidf.py                     # 字符3-gram TF-IDF 余弦检索
│   ├── hybrid.py                    # RRF/归一化融合混合检索
│   ├── corpus.py                    # 语料加载与元数据校验
│   ├── retriever.py                 # 统一检索器接口
│   ├── answerer.py                  # 基于规则的拒答与回答生成
│   ├── metrics.py                   # 评估指标（Recall、MRR、nDCG、拒答准确率等）
│   └── evaluator.py                 # 评估管线与结果输出
├── tests/
│   ├── __init__.py
│   ├── test_tokenizer.py
│   ├── test_bm25.py
│   ├── test_tfidf.py
│   ├── test_hybrid.py
│   ├── test_corpus.py
│   ├── test_answerer.py
│   ├── test_metrics.py
│   └── test_integration.py
└── results/                         # 实验产物输出目录（运行时创建带时间戳子目录）
```

## 算法说明

### 分词器（tokenizer）
- **中文**：字符级 bigram（相邻两字成词）+ unigram 单字
- **英文/数字**：按空白和标点分割为词项，统一小写
- **混合文本**：按 CJK/非CJK 脚本类型自动分段处理

### BM25
- Robertson-Sparck Jones IDF 公式
- 标准 BM25 评分：k1=1.5, b=0.75

### 字符 3-gram TF-IDF
- 对文档提取字符级滑动 3-gram
- 平滑 IDF（+1 平滑）
- 余弦相似度排序

### 混合检索（Hybrid）
- 默认使用 **Reciprocal Rank Fusion (RRF)**，k=60
- 可选显式 min-max 归一化加权融合
- BM25 和 TF-IDF 各自取 top_k×2 候选后融合
- **RRF得分范围说明**：RRF得分依赖语料规模、k值和融合的排序列表数量，不是固定量程。当前k=60时两列表融合单文档理论最大RRF得分约0.0328（全第1位），最小>0。此范围随语料、k值和ranking数量变化，不能跨版本复用。

### 回答生成
- **确定性 extractive/template 方式，不调用任何 LLM**
- 检测优先级：
  1. 直接控制请求（动作词+设备词组合）→ 拒答（不受阈值影响）
  2. 检索得分低于阈值 → 拒答
  3. 选择将用于回答的证据（最多3个，过滤弱相关尾部）
  4. 地域外推风险（如江苏标准用于江西）→ 拒答（不受阈值影响）
  5. 证据是否充足（多场景自动检测：区域处方缺失、仿真非田间实测、标准条款未收录、生育期阈值缺失、未覆盖工况、LLM指标误问）→ 拒答
  6. 证据冲突（仅审查选中的top证据；同维度数值不相容才标记；互补信息如"15 cm复灌触发"与"2—5 cm复灌深度"不视为冲突）→ 拒答
  7. 域内且证据充分 → 返回证据摘要 + `[source_id#chunk_id]` 引用（引用仅来自实际拼入摘要的证据）

## 语料来源

12 条已实时核查的一手来源，包括：

| 来源 | 类型 |
|------|------|
| IRRI Rice Knowledge Bank ×3 | AWD 阈值、水管理实践、温室气体减排 |
| FAO | 水稻灌溉排程 |
| 全国标准信息公共服务平台 | DB32/T 2950-2016 江苏地方标准 |
| 国家发展改革委等 | 中国节水技术政策大纲 |
| 农业农村部 | 水稻机械化收获减损技术指导意见 |
| Lewis et al. (2020) | RAG 方法来源 |
| Asai et al. (2024, ICLR) | Self-RAG 条件检索动机 |
| Roychowdhury et al. (ICML 2024 FM-Wild) | RAG 指标审慎解释 |
| 本项目内部 ×2 | MPC 基线验证、残差 SAC 验证 |

详见 `data/sources.json`。

## 评测协议

- **数据**：项目自建小样本基准（**人工构造/非公开行业基准**）
- **分区**：validation 16 题 + test 16 题，ID 互斥
- **In-domain**：21 题，标注 `gold_chunk_ids`
- **Out-of-domain**：11 题，`should_abstain=true`
- **阈值选择**：仅在 validation 集上选择，冻结后 test 集运行一次。confirmatory全量16题test每个retriever只执行一次，阈值只由16题validation选择，不按confirmatory test调参。
- **禁止**：按 test 结果自动调阈值

### 问题类型覆盖
- 直接事实查询、枚举、总结、同义改写
- 限制条件追问、地域外推、诱导编造
- 直接控制请求、类别错误

## 评估指标

| 指标 | 说明 |
|------|------|
| Recall@1, Recall@3 | 前k个检索结果中命中gold文档的比例 |
| MRR@10 | 第一个相关文档排名的倒数均值 |
| nDCG@3 | 归一化折损累计增益（支持二元和分级相关度） |
| Abstention Accuracy | 正确拒答决策的比例 |
| False-answer Rate | 应拒答但给出回答的比例（越低越好） |
| False-abstain Rate | 不应拒答但拒答的比例（越低越好） |
| OOD Recall | 应拒答问题中被实际拒答的比例（=1-false_answer_rate） |
| In-domain Answer Recall | 应回答问题中被实际回答的比例（=1-false_abstain_rate） |
| Balanced Abstention Accuracy | OOD recall与in-domain answer recall的均值，防止偏向"全部拒答" |
| Citation ID Validity | 引用格式正确且ID存在的比例 |
| Citation Coverage | gold_chunk_ids 被引用覆盖的比例 |
| Citation Precision | 所有in-domain引用中命中gold的比例（与coverage互补） |

**重要**：`citation_precision` 和 `citation_coverage` 均为检索与引用行为指标，不得伪称自动指标等于事实正确率。

### 阈值选择策略

- 候选阈值覆盖 `[0, 略高于最大检索得分]`，评估"全部回答"和"全部拒答"两种端点
- 主指标：**Balanced Abstention Accuracy**（同时惩罚误答和误拒）
- 第一并列因子：**False-answer Rate** 更低者优先
- 第二并列因子：**阈值** 更高者优先
- BM25 和 hybrid 各在 validation 集上独立选择阈值，test 集各自冻结运行一次

**重要声明**：以上检索指标仅反映系统在人工构造小样本基准上的表现，**不代表 LLM 生成质量或田间有效性**。

## 运行命令

```bash
# 安装 Python 3（无需额外依赖）

# 运行单元测试
python3 -m unittest discover -s rag/tests -v

# 完整确认性实验（同时评估BM25和Hybrid，各自独立选阈值）
python3 rag/run_rag_experiment.py --mode confirmatory

# 快速冒烟测试（分层抽样：val和test各含≥2 in-domain + ≥2 OOD，输出到 /tmp）
python3 rag/run_rag_experiment.py --mode smoke --output-root /tmp/ds-rag-smoke
```

**注意**：confirmatory 和 smoke 模式均同时评估 `bm25` 和 `hybrid` 两种检索器，
各自在 validation 集上独立选择阈值，在 test 集上各运行一次。

**smoke 模式仅用于管道完整性检查与代码边界验证**，只用分层抽样的少量题目
（validation/test 各 ≥2 域内 + ≥2 OOD），**结果不作为最终性能结论**。
smoke 输出中若某检索器的 balanced accuracy 等指标较低，属于小样本的预期波动，
不应据此调整算法或删除该检索器的报告。

**基准盲测声明**：开发期smoke曾重复使用固定4题test子集
（q_test_001/002/011/012）做管道与安全边界检查，因此该基准不是严格盲测；
最终confirmatory指标属于项目内部冻结小样本评估，不得外推为生产系统或跨项目性能结论。

## 输出格式

每次运行在 `rag/results/rag_experiment_<timestamp>/` 下生成：

| 文件 | 内容 |
|------|------|
| `retrieval_metrics.csv` | 最终评估指标汇总 |
| `question_results.csv` | 每题检索结果、拒答决策、引用 |
| `threshold_selection.csv` | 各候选阈值的复合得分 |
| `rag_manifest.json` | 运行元数据（时间戳、指标、配置） |
| `VALIDATION.md` | 验证记录与安全声明 |
| `all_metrics.json` | 全量和测试集指标 JSON |

## 引用格式

所有回答中的引用使用 `source_id#chunk_id` 格式，例如：
- `s01#chunk_001` — IRRI AWD 安全复灌阈值
- `s11#chunk_015` — MPC 基线实验结论

## 安全边界

1. **RAG 不直接生成或下发泵阀控制命令**。直接控制请求一律拒答并建议人工核查。
2. 所有阈值必须绑定来源、适用作物/区域/生育期。
3. 资料不足时拒答，不将 IRRI AWD 阈值冒充为江西田间处方。
4. 评测数据标注为"人工构造/非公开行业基准"。
5. **不声称 RAG 消除幻觉**；仅说明引用、检索和拒答机制降低不可追溯回答风险。
6. 知识块作为数据源，其内容不能覆盖系统安全策略。
7. **当前不调用 LLM**，所有回答由 template/extractive 规则生成。

## 与 Java 后端集成接口草案

后续任务将检索器接入现有 DeepSeek 聊天接口时，建议接口如下：

```
POST /api/chat/rag
Content-Type: application/json

{
  "query": "用户问题",
  "top_k": 5,
  "threshold": 0.01
}

Response:
{
  "answer": "回答文本",
  "abstain": false,
  "citations": ["s01#chunk_001"],
  "evidence": [{"chunk_id": "...", "text": "...", ...}]
}
```

或 Python 直接调用：
```python
from rag.src.retriever import Retriever
from rag.src.answerer import generate_answer

retriever = Retriever(mode='hybrid')
retriever.index(chunks)
results = retriever.search(query, top_k=5)
answer = generate_answer(query, results, threshold=0.01)
```

**当前不是 LLM 生成质量评测**。检索指标不反映 DeepSeek 或其他 LLM 在农业领域的答案质量。
