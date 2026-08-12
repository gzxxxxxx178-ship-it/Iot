"""
评估模块：在validation集上为每个retriever独立选择拒答阈值，在test集上运行最终评估。
"""

import json
import os
import csv
import datetime
from rag.src.retriever import Retriever
from rag.src.answerer import generate_answer
from rag.src.metrics import (
    compute_all_metrics,
    ood_recall,
    in_domain_answer_recall,
    balanced_abstention_accuracy,
    false_answer_rate,
    false_abstain_rate,
)


def run_evaluation(
    retriever: Retriever,
    questions: list[dict],
    threshold: float = 0.01,
    top_k: int = 10,
    retriever_name: str = 'hybrid',
) -> list[dict]:
    """
    对给定问题集运行检索与回答评估。

    对每个问题：
    1. 执行检索，获取top_k结果
    2. 生成回答（含拒答决策）
    3. 收集检索chunk_id和引用

    参数:
        retriever: 已索引的检索器实例
        questions: 问题字典列表
        threshold: 拒答检索得分阈值
        top_k: 检索返回的最大结果数
        retriever_name: 检索器名称标识，写入结果中

    返回:
        每个问题的评估结果字典列表
    """
    results = []

    for q in questions:
        # 检索
        search_results = retriever.search(q['question'], top_k=top_k)
        retrieved_chunk_ids = [
            chunk['chunk_id'] for chunk, _ in search_results
        ]

        # 生成回答
        answer = generate_answer(q['question'], search_results, threshold=threshold)

        results.append({
            'question_id': q['question_id'],
            'question': q['question'],
            'split': q['split'],
            'should_abstain': q['should_abstain'],
            'gold_chunk_ids': q.get('gold_chunk_ids', []),
            'retrieved_chunk_ids': retrieved_chunk_ids,
            'abstained': answer['abstain'],
            'abstain_reason': answer.get('abstain_reason', ''),
            'answer': answer['answer'],
            'citations': answer['citations'],
            'top_scores': [
                round(score, 6) for _, score in search_results[:top_k]
            ],
            'threshold': threshold,
            'retriever': retriever_name,
        })

    return results


def select_threshold(
    retriever: Retriever,
    validation_questions: list[dict],
    top_k: int = 10,
    num_steps: int = 20,
    retriever_name: str = 'hybrid',
) -> tuple[float, list[dict], list[dict]]:
    """
    在validation集上选择最优拒答阈值。

    候选阈值覆盖[0, 略高于最大检索得分]以确保评估"全部回答"和"全部拒答"端点。
    选择策略：
    - 主指标：balanced_abstention_accuracy（OOD recall与in-domain answer recall的均值）
    - 第一并列：false_answer_rate更低者优先
    - 第二并列：阈值更高者优先

    参数:
        retriever: 已索引的检索器实例
        validation_questions: validation集问题列表
        top_k: 检索返回的最大结果数
        num_steps: 候选阈值数量
        retriever_name: 检索器名称

    返回:
        (最优阈值, 各阈值评估结果列表, 最优阈值的详细结果列表)
    """
    # 先运行所有问题获取最高检索得分，确定搜索范围
    all_scores = []
    for q in validation_questions:
        raw_results = retriever.search_raw(q['question'], top_k=top_k)
        if raw_results:
            all_scores.append(raw_results[0][1])

    if not all_scores:
        # 无检索结果，返回保守阈值
        return 0.01, [], []

    max_score = max(all_scores)

    # 生成候选阈值：必须覆盖0、观测得分范围、以及略高于最大得分
    candidates = []
    # 端点：0（全部回答）
    candidates.append(0.0)
    # 观察范围：等距取样
    for i in range(1, num_steps):
        candidates.append(max_score * i / num_steps)
    # 端点：高于最大得分（全部拒答）
    candidates.append(max_score * 1.05)
    # 去重排序，保留真实0.0端点
    candidates = sorted(set(candidates))

    best_balanced = -1.0
    best_far = 1.0
    best_threshold = candidates[0]
    best_results = []
    all_threshold_results = []

    for t in candidates:
        results = run_evaluation(
            retriever, validation_questions, threshold=t, top_k=top_k,
            retriever_name=retriever_name,
        )
        should = [r['should_abstain'] for r in results]
        predicted = [r['abstained'] for r in results]

        bal = balanced_abstention_accuracy(predicted, should)
        far = false_answer_rate(predicted, should)
        fas = false_abstain_rate(predicted, should)
        ood = ood_recall(predicted, should)
        idr = in_domain_answer_recall(predicted, should)

        all_threshold_results.append({
            'retriever': retriever_name,
            'threshold': round(t, 8),
            'ood_recall': round(ood, 6),
            'in_domain_answer_recall': round(idr, 6),
            'balanced_abstention_accuracy': round(bal, 6),
            'false_answer_rate': round(far, 6),
            'false_abstain_rate': round(fas, 6),
        })

        # 选择最佳阈值：主指标balanced accuracy，并列优先false_answer_rate更低，再并列优先阈值更高
        if bal > best_balanced or (bal == best_balanced and far < best_far) or \
           (bal == best_balanced and far == best_far and t > best_threshold):
            best_balanced = bal
            best_far = far
            best_threshold = t
            best_results = results

    return best_threshold, all_threshold_results, best_results


def write_results(
    output_dir: str,
    mode: str,
    retriever_results: list[dict],
    chunks: list[dict],
    sources: list[dict],
) -> None:
    """
    将所有评估结果写入输出目录。

    每个retriever_results元素包含一个retriever的完整结果。
    支持多retriever独立输出，CSV中增加retriever列。

    输出文件：
    - retrieval_metrics.csv（含retriever列）
    - question_results.csv（含retriever列）
    - threshold_selection.csv（含retriever列）
    - rag_manifest.json
    - VALIDATION.md

    参数:
        output_dir: 输出目录路径
        mode: 运行模式（'confirmatory' 或 'smoke'）
        retriever_results: 每个retriever的评估结果列表，每项含：
            - name: str, retriever名称
            - threshold: float, 选定的阈值
            - validation_results: list[dict] | None
            - test_results: list[dict] | None
            - threshold_selection: list[dict] | None
            - test_metrics: dict
        chunks: 知识块列表
        sources: 来源列表
    """
    os.makedirs(output_dir, exist_ok=True)

    # 1. retrieval_metrics.csv（每个retriever一行或多行）
    metrics_path = os.path.join(output_dir, 'retrieval_metrics.csv')
    with open(metrics_path, 'w', newline='', encoding='utf-8') as f:
        fieldnames = ['retriever'] + sorted(
            set().union(*(rr['test_metrics'].keys() for rr in retriever_results))
        )
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for rr in retriever_results:
            row = {'retriever': rr['name']}
            for k, v in rr['test_metrics'].items():
                row[k] = round(v, 6)
            writer.writerow(row)

    # 2. question_results.csv（含retriever列）
    all_results = []
    for rr in retriever_results:
        if rr.get('validation_results'):
            all_results.extend(rr['validation_results'])
        if rr.get('test_results'):
            all_results.extend(rr['test_results'])

    if all_results:
        qr_path = os.path.join(output_dir, 'question_results.csv')
        fieldnames = [
            'retriever', 'question_id', 'question', 'split', 'should_abstain',
            'abstained', 'abstain_reason', 'gold_chunk_ids',
            'retrieved_chunk_ids', 'citations', 'top_scores',
        ]
        with open(qr_path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction='ignore')
            writer.writeheader()
            for r in all_results:
                row = {k: r.get(k, '') for k in fieldnames}
                for key in ['gold_chunk_ids', 'retrieved_chunk_ids', 'citations', 'top_scores']:
                    row[key] = json.dumps(row[key], ensure_ascii=False)
                writer.writerow(row)

    # 3. threshold_selection.csv（含retriever列）
    all_thresholds = []
    for rr in retriever_results:
        if rr.get('threshold_selection'):
            all_thresholds.extend(rr['threshold_selection'])

    if all_thresholds:
        ts_path = os.path.join(output_dir, 'threshold_selection.csv')
        fieldnames = [
            'retriever', 'threshold', 'ood_recall', 'in_domain_answer_recall',
            'balanced_abstention_accuracy', 'false_answer_rate', 'false_abstain_rate',
        ]
        with open(ts_path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction='ignore')
            writer.writeheader()
            for row in all_thresholds:
                writer.writerow(row)

    # 4. rag_manifest.json（记录每个retriever的独立阈值和指标，以及实际题数）
    retriever_entries = {}
    all_question_ids = set()
    for rr in retriever_results:
        val_ids = {r['question_id'] for r in (rr.get('validation_results') or [])}
        test_ids = {r['question_id'] for r in (rr.get('test_results') or [])}
        all_question_ids.update(val_ids)
        all_question_ids.update(test_ids)
        retriever_entries[rr['name']] = {
            'threshold': round(rr['threshold'], 8),
            'num_validation_questions': len(val_ids),
            'num_test_questions': len(test_ids),
            'test_metrics': {k: round(v, 6) for k, v in rr['test_metrics'].items()},
        }

    manifest = {
        'timestamp': datetime.datetime.now().strftime('%Y-%m-%dT%H:%M:%S'),
        'mode': mode,
        'threshold_strategy': 'balanced_abstention_accuracy (primary), lower false_answer_rate (tiebreak 1), higher threshold (tiebreak 2)',
        'num_chunks': len(chunks),
        'num_sources': len(sources),
        'num_unique_questions': len(all_question_ids),
        'retrievers': retriever_entries,
    }
    manifest_path = os.path.join(output_dir, 'rag_manifest.json')
    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    # 5. VALIDATION.md
    _write_validation_md(output_dir, mode, retriever_results)


def _write_validation_md(
    output_dir: str,
    mode: str,
    retriever_results: list[dict],
) -> None:
    """
    生成VALIDATION.md验证记录，按retriever分别报告test指标和题数。

    参数:
        output_dir: 输出目录
        mode: 运行模式
        retriever_results: 每个retriever的评估结果
    """
    md_path = os.path.join(output_dir, 'VALIDATION.md')

    # 跨retriever运行的总行数（每个retriever独立评估产生独立的validation+test行）
    total_rows = sum(
        len(rr.get('validation_results', [])) + len(rr.get('test_results', []))
        for rr in retriever_results
    )

    md_content = f"""# RAG检索实验验证记录

## 概述

- **运行模式**: {mode}
- **时间戳**: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
- **检索器数量**: {len(retriever_results)}
- **阈值选择策略**: balanced_abstention_accuracy 为主指标，false_answer_rate 和阈值更高为并列因子

## 各检索器题数与Test集指标

"""
    for rr in retriever_results:
        val_ids = {r['question_id'] for r in (rr.get('validation_results') or [])}
        test_ids = {r['question_id'] for r in (rr.get('test_results') or [])}
        val_in = len({r['question_id'] for r in (rr.get('validation_results') or []) if not r['should_abstain']})
        val_ood = len({r['question_id'] for r in (rr.get('validation_results') or []) if r['should_abstain']})
        test_in = len({r['question_id'] for r in (rr.get('test_results') or []) if not r['should_abstain']})
        test_ood = len({r['question_id'] for r in (rr.get('test_results') or []) if r['should_abstain']})

        md_content += f"### {rr['name']}\n\n"
        md_content += f"- **选定阈值**: {rr['threshold']:.8f}\n"
        md_content += f"- **Validation题数**: {len(val_ids)}（域内 {val_in} + OOD {val_ood}）\n"
        md_content += f"- **Test题数**: {len(test_ids)}（域内 {test_in} + OOD {test_ood}）\n\n"
        md_content += "| 指标 | 值 |\n|------|-----|\n"
        for k, v in sorted(rr['test_metrics'].items()):
            md_content += f"| {k} | {v:.6f} |\n"
        md_content += "\n"

    md_content += f"""## 说明

- **跨retriever运行总行数**: {total_rows}（每个retriever对相同validation/test题目独立评估产生独立行，不等于去重题数）
- **模式**: {mode}
"""
    if mode == 'smoke':
        md_content += "- **smoke模式警告**: 此模式仅用于管道完整性检查与代码边界验证，"
        md_content += "只用4题（2域内+2 OOD），**结果不作为最终性能结论**。\n"

    md_content += f"""
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
"""
    with open(md_path, 'w', encoding='utf-8') as f:
        f.write(md_content)
