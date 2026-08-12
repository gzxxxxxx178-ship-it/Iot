#!/usr/bin/env python3
"""
RAG检索实验主脚本。

同时评估BM25和hybrid两种检索器，各自在validation集上独立选择阈值，
在test集上冻结阈值各运行一次。

用法:
    python3 rag/run_rag_experiment.py --mode confirmatory
    python3 rag/run_rag_experiment.py --mode smoke --output-root /tmp/ds-rag-smoke

参数:
    --mode: 运行模式，'confirmatory'（完整运行）或 'smoke'（快速验证）
    --output-root: 输出根目录，默认 '.'（即rag/results/下创建时间戳目录）
    --seed: 随机种子，当前用于smoke分层抽样的确定性排序，默认 42
"""

import argparse
import datetime
import json
import os
import sys

# 确保项目根目录在Python路径中
script_dir = os.path.dirname(os.path.abspath(__file__))
project_dir = os.path.dirname(script_dir)
if project_dir not in sys.path:
    sys.path.insert(0, project_dir)

from rag.src.corpus import load_sources, load_chunks, load_questions, validate_corpus_consistency
from rag.src.retriever import Retriever
from rag.src.evaluator import run_evaluation, select_threshold, write_results
from rag.src.metrics import compute_all_metrics


# 待评估的检索器配置
RETRIEVER_CONFIGS = [
    {'mode': 'bm25', 'name': 'bm25'},
    {'mode': 'hybrid', 'name': 'hybrid'},
]


def _stratified_smoke_sample(
    questions: list[dict],
    in_domain_count: int = 2,
    ood_count: int = 2,
) -> list[dict]:
    """
    从问题集中分层抽样，确保包含指定数量的in-domain和out-of-domain问题。

    参数:
        questions: 问题字典列表
        in_domain_count: 至少需要的in-domain问题数
        ood_count: 至少需要的out-of-domain问题数

    返回:
        抽样后的问题列表
    """
    in_domain = [q for q in questions if not q['should_abstain']]
    ood = [q for q in questions if q['should_abstain']]

    # 按question_id排序保证确定性
    in_domain.sort(key=lambda q: q['question_id'])
    ood.sort(key=lambda q: q['question_id'])

    sample = in_domain[:in_domain_count] + ood[:ood_count]
    return sample


def main() -> None:
    """
    RAG实验主函数。

    流程：
    1. 加载并校验语料
    2. 对每个检索器（BM25、hybrid）：
       a. 建立索引
       b. 在validation集上选择最优拒答阈值
       c. 冻结阈值，在test集上运行一次最终评估
    3. 汇总所有retriever结果并输出到时间戳目录
    """
    parser = argparse.ArgumentParser(description='RAG检索实验')
    parser.add_argument(
        '--mode', choices=['confirmatory', 'smoke'], default='confirmatory',
        help='运行模式：confirmatory=完整运行, smoke=快速验证',
    )
    parser.add_argument(
        '--output-root', default=None,
        help='输出根目录，默认根据模式自动选择',
    )
    parser.add_argument('--seed', type=int, default=42, help='随机种子（smoke分层使用确定性排序）')
    args = parser.parse_args()

    print(f"[RAG] 启动实验，模式={args.mode}，seed={args.seed}")

    # 数据文件路径（相对于项目根目录）
    data_dir = os.path.join(project_dir, 'rag', 'data')
    sources_path = os.path.join(data_dir, 'sources.json')
    chunks_path = os.path.join(data_dir, 'chunks.jsonl')
    questions_path = os.path.join(data_dir, 'eval_questions.jsonl')

    # 加载数据
    print("[RAG] 加载语料...")
    try:
        sources = load_sources(sources_path)
        print(f"  sources: {len(sources)} 条")
    except Exception as e:
        print(f"[ERROR] 加载sources失败: {e}")
        sys.exit(1)

    try:
        chunks = load_chunks(chunks_path)
        print(f"  chunks: {len(chunks)} 条")
    except Exception as e:
        print(f"[ERROR] 加载chunks失败: {e}")
        sys.exit(1)

    try:
        questions = load_questions(questions_path)
        print(f"  questions: {len(questions)} 条")
    except Exception as e:
        print(f"[ERROR] 加载questions失败: {e}")
        sys.exit(1)

    # 校验corpus一致性
    warnings = validate_corpus_consistency(sources, chunks)
    for w in warnings:
        print(f"  [WARN] {w}")

    # 分区
    validation_questions = [q for q in questions if q['split'] == 'validation']
    test_questions = [q for q in questions if q['split'] == 'test']
    print(f"  validation: {len(validation_questions)} 题, test: {len(test_questions)} 题")

    # smoke模式使用分层抽样
    if args.mode == 'smoke':
        validation_questions = _stratified_smoke_sample(
            validation_questions, in_domain_count=2, ood_count=2,
        )
        test_questions = _stratified_smoke_sample(
            test_questions, in_domain_count=2, ood_count=2,
        )
        ids_val = {q['question_id'] for q in validation_questions}
        ids_test = {q['question_id'] for q in test_questions}
        print(f"  [smoke] 分层抽样: validation={len(validation_questions)} ({len([q for q in validation_questions if not q['should_abstain']])}in+{len([q for q in validation_questions if q['should_abstain']])}ood), "
              f"test={len(test_questions)} ({len([q for q in test_questions if not q['should_abstain']])}in+{len([q for q in test_questions if q['should_abstain']])}ood)")

    valid_chunk_ids = {c['chunk_id'] for c in chunks}
    valid_source_ids = {s['source_id'] for s in sources}

    # 对每个检索器独立评估
    retriever_results = []

    for config in RETRIEVER_CONFIGS:
        retriever_name = config['name']
        retriever_mode = config['mode']
        print(f"\n[RAG] ====== 检索器: {retriever_name} ======")

        # 建立索引
        print(f"[RAG] 建立索引 ({retriever_name})...")
        retriever = Retriever(mode=retriever_mode)
        retriever.index(chunks)

        # 阶段1：在validation集上选择阈值
        print(f"[RAG] 阶段1: validation集阈值选择...")
        best_threshold, threshold_results, _ = select_threshold(
            retriever, validation_questions, top_k=10, retriever_name=retriever_name,
        )
        print(f"  最优阈值: {best_threshold:.8f}")

        # 在validation集上用最优阈值运行
        validation_results = run_evaluation(
            retriever, validation_questions, threshold=best_threshold, top_k=10,
            retriever_name=retriever_name,
        )

        # 阶段2：冻结阈值，在test集上运行一次
        print(f"[RAG] 阶段2: test集评估 (冻结阈值={best_threshold:.8f})...")
        test_results = run_evaluation(
            retriever, test_questions, threshold=best_threshold, top_k=10,
            retriever_name=retriever_name,
        )

        # 计算test集指标
        test_metrics = compute_all_metrics(
            test_results, valid_chunk_ids, valid_source_ids,
        )

        print(f"\n[RAG] {retriever_name} Test集指标:")
        for k, v in sorted(test_metrics.items()):
            print(f"  {k}: {v:.6f}")

        retriever_results.append({
            'name': retriever_name,
            'threshold': best_threshold,
            'validation_results': validation_results,
            'test_results': test_results,
            'threshold_selection': threshold_results,
            'test_metrics': test_metrics,
        })

    # 确定输出目录
    if args.output_root:
        output_root = args.output_root
    elif args.mode == 'smoke':
        output_root = '/tmp/ds-rag-smoke'
    else:
        output_root = os.path.join(project_dir, 'rag', 'results')

    timestamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
    output_dir = os.path.join(output_root, f'rag_experiment_{timestamp}')

    print(f"\n[RAG] 写入结果到: {output_dir}")
    write_results(
        output_dir=output_dir,
        mode=args.mode,
        retriever_results=retriever_results,
        chunks=chunks,
        sources=sources,
    )

    # 同时保存全量指标到manifest
    all_metrics_path = os.path.join(output_dir, 'all_metrics.json')
    with open(all_metrics_path, 'w', encoding='utf-8') as f:
        json.dump({
            rr['name']: {k: round(v, 6) for k, v in rr['test_metrics'].items()}
            for rr in retriever_results
        }, f, ensure_ascii=False, indent=2)

    print(f"\n[RAG] 实验完成！结果已保存到 {output_dir}")
    print(f"  - retrieval_metrics.csv")
    print(f"  - question_results.csv")
    print(f"  - threshold_selection.csv")
    print(f"  - rag_manifest.json")
    print(f"  - VALIDATION.md")


if __name__ == '__main__':
    main()
