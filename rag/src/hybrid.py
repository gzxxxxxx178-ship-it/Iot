"""
混合检索模块：通过Reciprocal Rank Fusion（RRF）融合BM25和字符3-gram TF-IDF的检索结果。
"""


def reciprocal_rank_fusion(
    rankings: list[list[tuple[int, float]]],
    k: int = 60
) -> list[tuple[int, float]]:
    """
    使用Reciprocal Rank Fusion（RRF）融合多个排序列表。

    RRF公式：score(d) = Σ_{r in rankings} 1 / (k + rank_r(d))
    其中 rank_r(d) 是文档d在排序r中的排名（1-indexed）。

    参数:
        rankings: 多个排序列表，每个元素为(文档索引, 得分)的列表
        k: RRF常数，控制排名对得分的影响程度，默认60

    返回:
        融合后的(文档索引, RRF得分)列表，按得分降序排列
    """
    scores: dict[int, float] = {}

    for ranking in rankings:
        for rank, (doc_idx, _) in enumerate(ranking, start=1):
            scores[doc_idx] = scores.get(doc_idx, 0.0) + 1.0 / (k + rank)

    result = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return result


def explicit_normalization_fusion(
    rankings: list[list[tuple[int, float]]],
    weights: list[float] | None = None
) -> list[tuple[int, float]]:
    """
    使用显式min-max归一化后加权求和的融合方法。

    对每个排序列表的得分做[0,1]归一化，再按给定权重加权求和。

    参数:
        rankings: 多个排序列表，每个元素为(文档索引, 得分)的列表
        weights: 各排序列表的权重列表，默认等权重（均为1.0）

    返回:
        融合后的(文档索引, 融合得分)列表，按得分降序排列
    """
    if weights is None:
        weights = [1.0] * len(rankings)

    # 对每个排序列表的得分做min-max归一化
    normalized = []
    for ranking in rankings:
        if not ranking:
            normalized.append({})
            continue
        scores = [s for _, s in ranking]
        min_s = min(scores)
        max_s = max(scores)
        if max_s == min_s:
            normalized.append({doc: 1.0 for doc, _ in ranking})
        else:
            normalized.append({
                doc: (s - min_s) / (max_s - min_s)
                for doc, s in ranking
            })

    # 加权求和
    fused: dict[int, float] = {}
    for norm, w in zip(normalized, weights):
        for doc, score in norm.items():
            fused[doc] = fused.get(doc, 0.0) + w * score

    result = sorted(fused.items(), key=lambda x: x[1], reverse=True)
    return result


class HybridRetriever:
    """
    混合检索器。

    组合BM25（词项级匹配）和字符3-gram TF-IDF（字符级匹配）两种检索器，
    通过RRF或显式归一化融合两者的排序结果，提升检索鲁棒性。
    """

    def __init__(
        self,
        bm25_retriever,
        tfidf_retriever,
        fusion_method: str = 'rrf',
        k: int = 60,
        weights: list[float] | None = None,
    ):
        """
        初始化混合检索器。

        参数:
            bm25_retriever: BM25检索器实例（需已索引）
            tfidf_retriever: CharTrigramTFIDF检索器实例（需已索引）
            fusion_method: 融合方法，'rrf' 或 'normalize'，默认'rrf'
            k: RRF常数，仅fusion_method='rrf'时使用，默认60
            weights: 归一化融合权重，仅fusion_method='normalize'时使用
        """
        self.bm25 = bm25_retriever
        self.tfidf = tfidf_retriever
        self.fusion_method = fusion_method
        self.k = k
        self.weights = weights

    def search(
        self,
        query_text: str,
        query_tokens: list[str],
        top_k: int = 10,
    ) -> list[tuple[int, float]]:
        """
        执行混合检索：分别用BM25和TF-IDF检索，再融合排序结果。

        参数:
            query_text: 原始查询文本字符串（用于TF-IDF字符3-gram检索）
            query_tokens: 分词后的查询token列表（用于BM25检索）
            top_k: 返回的最大文档数

        返回:
            (文档索引, 融合得分) 列表，按得分降序排列
        """
        # 各自检索，取top_k*2以提供足够候选用于融合
        bm25_results = self.bm25.search(query_tokens, top_k=top_k * 2)
        tfidf_results = self.tfidf.search(query_text, top_k=top_k * 2)

        if self.fusion_method == 'rrf':
            fused = reciprocal_rank_fusion([bm25_results, tfidf_results], k=self.k)
        else:
            fused = explicit_normalization_fusion(
                [bm25_results, tfidf_results], weights=self.weights
            )

        return fused[:top_k]
