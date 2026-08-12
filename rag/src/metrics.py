"""
检索与回答评估指标计算模块。

实现所有评价指标的可单测、透明计算：
- Recall@k（检索召回率）
- MRR@k（平均倒数排名）
- nDCG@k（归一化折损累计增益）
- Abstention Accuracy（拒答准确率）
- False-answer Rate（误答率：本应拒答但未拒答的比例）
- False-abstain Rate（误拒率：不应拒答但拒答的比例）
- OOD Recall（应拒答问题中被实际拒答的比例）
- In-domain Answer Recall（应回答问题中被实际回答的比例）
- Balanced Abstention Accuracy（OOD recall与in-domain answer recall的均值）
- Citation ID Validity（引用ID有效性）
- Citation Coverage（引用覆盖率：gold被引用覆盖的比例）
- Citation Precision（引用精度：所有in-domain回答引用中命中gold的比例）
"""

import math


def recall_at_k(
    retrieved_ids: list[str],
    gold_ids: list[str],
    k: int,
) -> float:
    """
    计算Recall@k：在前k个检索结果中命中的gold文档比例。

    参数:
        retrieved_ids: 检索返回的文档chunk_id列表（按排名顺序）
        gold_ids: 标注的相关文档chunk_id列表
        k: 截断值

    返回:
        recall值，范围[0, 1]
    """
    if not gold_ids:
        return 0.0
    top_k = set(retrieved_ids[:k])
    hits = sum(1 for g in gold_ids if g in top_k)
    return hits / len(gold_ids)


def mrr_at_k(
    retrieved_ids: list[str],
    gold_ids: list[str],
    k: int,
) -> float:
    """
    计算MRR@k（Mean Reciprocal Rank）：第一个相关文档排名的倒数。

    参数:
        retrieved_ids: 检索返回的文档chunk_id列表（按排名顺序）
        gold_ids: 标注的相关文档chunk_id列表
        k: 截断值（仅考虑前k个结果）

    返回:
        MRR值，范围[0, 1]；若在前k个结果中无相关文档则返回0
    """
    for rank, doc_id in enumerate(retrieved_ids[:k], start=1):
        if doc_id in gold_ids:
            return 1.0 / rank
    return 0.0


def ndcg_at_k(
    retrieved_ids: list[str],
    gold_ids: list[str],
    k: int,
    relevance_map: dict[str, float] | None = None,
) -> float:
    """
    计算nDCG@k（归一化折损累计增益）。

    默认使用二值相关度（1=相关，0=不相关）。
    若提供relevance_map，可使用分级相关度。

    参数:
        retrieved_ids: 检索返回的文档chunk_id列表（按排名顺序）
        gold_ids: 标注的相关文档chunk_id列表
        k: 截断值
        relevance_map: 文档ID到相关度分值的映射（可选），默认gold_ids中为1.0

    返回:
        nDCG值，范围[0, 1]；若无相关文档则返回0.0
    """
    if not gold_ids:
        return 0.0

    # 构建相关度映射
    if relevance_map is None:
        rel = {gid: 1.0 for gid in gold_ids}
    else:
        rel = dict(relevance_map)

    # 计算DCG@k
    dcg = 0.0
    for rank, doc_id in enumerate(retrieved_ids[:k], start=1):
        gain = rel.get(doc_id, 0.0)
        dcg += gain / math.log2(rank + 1)

    # 计算IDCG@k（理想排序的DCG）
    ideal_gains = sorted(rel.values(), reverse=True)
    idcg = 0.0
    for rank, gain in enumerate(ideal_gains[:k], start=1):
        idcg += gain / math.log2(rank + 1)

    if idcg == 0.0:
        return 0.0

    return dcg / idcg


def abstention_accuracy(
    predicted_abstain: list[bool],
    should_abstain: list[bool],
) -> float:
    """
    计算拒答准确率：正确拒答决策的比例。

    参数:
        predicted_abstain: 系统实际是否拒答的布尔列表
        should_abstain: 标注是否应当拒答的布尔列表

    返回:
        准确率，范围[0, 1]
    """
    if not predicted_abstain:
        return 0.0
    correct = sum(1 for p, s in zip(predicted_abstain, should_abstain) if p == s)
    return correct / len(predicted_abstain)


def false_answer_rate(
    predicted_abstain: list[bool],
    should_abstain: list[bool],
) -> float:
    """
    计算误答率：在应当拒答的问题中，系统未拒答（给出了回答）的比例。

    误答率 = (should_abstain=True 但 predicted_abstain=False 的数量) / (should_abstain=True 的数量)

    参数:
        predicted_abstain: 系统实际是否拒答的布尔列表
        should_abstain: 标注是否应当拒答的布尔列表

    返回:
        误答率，范围[0, 1]；若无应拒答问题则返回0.0
    """
    should_count = sum(1 for s in should_abstain if s)
    if should_count == 0:
        return 0.0
    false_answers = sum(
        1 for p, s in zip(predicted_abstain, should_abstain)
        if s and not p
    )
    return false_answers / should_count


def false_abstain_rate(
    predicted_abstain: list[bool],
    should_abstain: list[bool],
) -> float:
    """
    计算误拒率：在不应拒答的问题中，系统错误拒答的比例。

    误拒率 = (should_abstain=False 但 predicted_abstain=True 的数量) / (should_abstain=False 的数量)

    参数:
        predicted_abstain: 系统实际是否拒答的布尔列表
        should_abstain: 标注是否应当拒答的布尔列表

    返回:
        误拒率，范围[0, 1]；若无不应拒答问题则返回0.0
    """
    answer_count = sum(1 for s in should_abstain if not s)
    if answer_count == 0:
        return 0.0
    false_abstains = sum(
        1 for p, s in zip(predicted_abstain, should_abstain)
        if not s and p
    )
    return false_abstains / answer_count


def ood_recall(
    predicted_abstain: list[bool],
    should_abstain: list[bool],
) -> float:
    """
    计算OOD召回率：在应当拒答的问题中，系统实际拒答的比例。

    OOD recall = (should_abstain=True 且 predicted_abstain=True 的数量) / (should_abstain=True 的数量)
    等价于 1 - false_answer_rate。

    参数:
        predicted_abstain: 系统实际是否拒答的布尔列表
        should_abstain: 标注是否应当拒答的布尔列表

    返回:
        OOD召回率，范围[0, 1]
    """
    return 1.0 - false_answer_rate(predicted_abstain, should_abstain)


def in_domain_answer_recall(
    predicted_abstain: list[bool],
    should_abstain: list[bool],
) -> float:
    """
    计算In-domain回答召回率：在不应拒答的问题中，系统实际给出回答的比例。

    In-domain answer recall = (should_abstain=False 且 predicted_abstain=False 的数量) / (should_abstain=False 的数量)
    等价于 1 - false_abstain_rate。

    参数:
        predicted_abstain: 系统实际是否拒答的布尔列表
        should_abstain: 标注是否应当拒答的布尔列表

    返回:
        In-domain回答召回率，范围[0, 1]
    """
    return 1.0 - false_abstain_rate(predicted_abstain, should_abstain)


def balanced_abstention_accuracy(
    predicted_abstain: list[bool],
    should_abstain: list[bool],
) -> float:
    """
    计算平衡拒答准确率：OOD recall与in-domain answer recall的算术均值。

    balanced = (OOD recall + in-domain answer recall) / 2
    该指标平等对待两种错误（误答和误拒），避免偏向"全部拒答"或"全部回答"。

    参数:
        predicted_abstain: 系统实际是否拒答的布尔列表
        should_abstain: 标注是否应当拒答的布尔列表

    返回:
        平衡准确率，范围[0, 1]
    """
    ood = ood_recall(predicted_abstain, should_abstain)
    in_domain = in_domain_answer_recall(predicted_abstain, should_abstain)
    return (ood + in_domain) / 2.0


def citation_id_validity(
    citations: list[list[str]],
    valid_chunk_ids: set[str],
    valid_source_ids: set[str],
) -> float:
    """
    计算引用ID有效性：格式正确且引用的chunk_id和source_id均存在的比例。

    参数:
        citations: 每个问题的引用列表（每项为"source_id#chunk_id"格式的字符串列表）
        valid_chunk_ids: 知识库中有效的chunk_id集合
        valid_source_ids: 知识库中有效的source_id集合

    返回:
        有效性比例，范围[0, 1]
    """
    total = 0
    valid = 0
    for citation_list in citations:
        for citation in citation_list:
            total += 1
            if _validate_citation(citation, valid_chunk_ids, valid_source_ids):
                valid += 1
    if total == 0:
        return 1.0  # 无引用视为有效（如全部拒答的情况）
    return valid / total


def _validate_citation(
    citation: str,
    valid_chunk_ids: set[str],
    valid_source_ids: set[str],
) -> bool:
    """
    验证单个引用格式和有效性。

    参数:
        citation: 引用字符串，应为"source_id#chunk_id"格式
        valid_chunk_ids: 有效的chunk_id集合
        valid_source_ids: 有效的source_id集合

    返回:
        是否有效
    """
    if '#' not in citation:
        return False
    parts = citation.split('#', 1)
    if len(parts) != 2:
        return False
    source_id, chunk_id = parts
    return source_id in valid_source_ids and chunk_id in valid_chunk_ids


def citation_coverage(
    citations: list[list[str]],
    gold_chunk_ids_list: list[list[str]],
) -> float:
    """
    计算引用覆盖率：在in-domain问题中，gold_chunk_ids被引用覆盖的比例。

    参数:
        citations: 每个问题的引用列表
        gold_chunk_ids_list: 每个问题对应的gold_chunk_ids

    返回:
        覆盖率，范围[0, 1]
    """
    total_gold = 0
    covered = 0
    for citation_list, gold_ids in zip(citations, gold_chunk_ids_list):
        if not gold_ids:
            continue
        cited_set = {c.split('#', 1)[1] if '#' in c else c for c in citation_list}
        for gid in gold_ids:
            total_gold += 1
            if gid in cited_set:
                covered += 1
    if total_gold == 0:
        return 1.0
    return covered / total_gold


def citation_precision(
    citations: list[list[str]],
    gold_chunk_ids_list: list[list[str]],
) -> float:
    """
    计算引用精度：在所有in-domain回答的引用中，命中gold_chunk_ids的比例。

    precision = (所有in-domain引用中命中gold的数量) / (所有in-domain引用的总数)

    参数:
        citations: 每个问题的引用列表，每项为"source_id#chunk_id"格式
        gold_chunk_ids_list: 每个问题对应的gold_chunk_ids

    返回:
        精度值，范围[0, 1]；若无in-domain引用则返回1.0
    """
    total_cited = 0
    hits = 0
    for citation_list, gold_ids in zip(citations, gold_chunk_ids_list):
        if not gold_ids:
            continue  # 跳过OOD问题
        for citation in citation_list:
            total_cited += 1
            # 从引用中提取chunk_id
            chunk_id = citation.split('#', 1)[1] if '#' in citation else citation
            if chunk_id in gold_ids:
                hits += 1
    if total_cited == 0:
        return 1.0  # 没有引用（如全部拒答），不算不精确
    return hits / total_cited


def compute_all_metrics(
    question_results: list[dict],
    valid_chunk_ids: set[str],
    valid_source_ids: set[str],
    k_values: tuple[int, ...] = (1, 3, 10),
) -> dict:
    """
    一次性计算所有评估指标。

    参数:
        question_results: 每个问题的结果字典列表，每个包含：
            - should_abstain: bool
            - abstained: bool
            - gold_chunk_ids: list[str]
            - retrieved_chunk_ids: list[str] (top-k)
            - citations: list[str]
        valid_chunk_ids: 有效chunk_id集合
        valid_source_ids: 有效source_id集合
        k_values: 需要计算的k值元组

    返回:
        指标字典，键如 'recall@1', 'recall@3', 'mrr@10', 'ndcg@3',
        'abstention_accuracy', 'false_answer_rate', 'citation_id_validity',
        'citation_coverage'
    """
    # 分离in-domain和out-of-domain结果
    in_domain = [r for r in question_results if not r['should_abstain']]
    all_results = question_results

    metrics = {}

    # Recall@k 和 MRR@k（仅in-domain）
    for k in k_values:
        recalls = [
            recall_at_k(r['retrieved_chunk_ids'], r['gold_chunk_ids'], k)
            for r in in_domain
        ]
        metrics[f'recall@{k}'] = sum(recalls) / len(recalls) if recalls else 0.0

        mrrs = [
            mrr_at_k(r['retrieved_chunk_ids'], r['gold_chunk_ids'], k)
            for r in in_domain
        ]
        metrics[f'mrr@{k}'] = sum(mrrs) / len(mrrs) if mrrs else 0.0

        ndcgs = [
            ndcg_at_k(r['retrieved_chunk_ids'], r['gold_chunk_ids'], k)
            for r in in_domain
        ]
        metrics[f'ndcg@{k}'] = sum(ndcgs) / len(ndcgs) if ndcgs else 0.0

    # 拒答准确率
    metrics['abstention_accuracy'] = abstention_accuracy(
        [r['abstained'] for r in all_results],
        [r['should_abstain'] for r in all_results],
    )

    # 误答率
    metrics['false_answer_rate'] = false_answer_rate(
        [r['abstained'] for r in all_results],
        [r['should_abstain'] for r in all_results],
    )

    # 误拒率
    metrics['false_abstain_rate'] = false_abstain_rate(
        [r['abstained'] for r in all_results],
        [r['should_abstain'] for r in all_results],
    )

    # OOD召回率
    metrics['ood_recall'] = ood_recall(
        [r['abstained'] for r in all_results],
        [r['should_abstain'] for r in all_results],
    )

    # In-domain回答召回率
    metrics['in_domain_answer_recall'] = in_domain_answer_recall(
        [r['abstained'] for r in all_results],
        [r['should_abstain'] for r in all_results],
    )

    # 平衡拒答准确率
    metrics['balanced_abstention_accuracy'] = balanced_abstention_accuracy(
        [r['abstained'] for r in all_results],
        [r['should_abstain'] for r in all_results],
    )

    # 引用ID有效性
    metrics['citation_id_validity'] = citation_id_validity(
        [r['citations'] for r in all_results],
        valid_chunk_ids,
        valid_source_ids,
    )

    # 引用覆盖率（仅in-domain含gold的问题）
    metrics['citation_coverage'] = citation_coverage(
        [r['citations'] for r in in_domain],
        [r['gold_chunk_ids'] for r in in_domain],
    )

    # 引用精度（仅in-domain含gold的问题）
    metrics['citation_precision'] = citation_precision(
        [r['citations'] for r in in_domain],
        [r['gold_chunk_ids'] for r in in_domain],
    )

    return metrics
