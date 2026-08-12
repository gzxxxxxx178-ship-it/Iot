"""
BM25检索算法实现。
使用Python 3标准库，基于Robertson-Sparck Jones公式。
"""

import math
from collections import Counter


class BM25:
    """
    BM25检索模型。

    实现标准BM25评分函数：score(D,Q) = Σ IDF(qi) * (tf(qi,D) * (k1+1)) / (tf(qi,D) + k1*(1-b+b*|D|/avgdl))

    属性:
        k1: 词频饱和度参数
        b: 文档长度归一化参数
    """

    def __init__(self, k1: float = 1.5, b: float = 0.75):
        """
        初始化BM25模型。

        参数:
            k1: 词频饱和度参数，控制词频对得分的影响程度，默认1.5
            b: 文档长度归一化参数，0=无归一化，1=完全归一化，默认0.75
        """
        self.k1 = k1
        self.b = b
        self._corpus_tokens: list[list[str]] = []
        self._doc_count = 0
        self._avg_doc_len = 0.0
        self._idf: dict[str, float] = {}
        self._doc_freq: Counter = Counter()

    def index(self, documents: list[list[str]]) -> None:
        """
        对文档集合建立索引，计算IDF和平均文档长度。

        参数:
            documents: 文档列表，每个文档是已分词的token列表
        """
        self._corpus_tokens = documents
        self._doc_count = len(documents)

        if self._doc_count == 0:
            self._avg_doc_len = 0.0
            return

        total_len = sum(len(doc) for doc in documents)
        self._avg_doc_len = total_len / self._doc_count

        # 计算文档频率（每个token出现在多少个文档中）
        self._doc_freq.clear()
        for doc in documents:
            unique_tokens = set(doc)
            for token in unique_tokens:
                self._doc_freq[token] += 1

        # 计算IDF（Robertson-Sparck Jones公式）
        self._idf.clear()
        for token, df in self._doc_freq.items():
            self._idf[token] = math.log(
                (self._doc_count - df + 0.5) / (df + 0.5) + 1.0
            )

    def score(self, query_tokens: list[str], doc_idx: int) -> float:
        """
        计算查询与指定文档的BM25得分。

        参数:
            query_tokens: 查询的分词token列表
            doc_idx: 文档在语料中的索引

        返回:
            BM25得分，非负浮点数
        """
        if doc_idx < 0 or doc_idx >= self._doc_count:
            return 0.0

        doc = self._corpus_tokens[doc_idx]
        doc_len = len(doc)
        if doc_len == 0:
            return 0.0

        term_freqs = Counter(doc)
        score = 0.0

        for token in set(query_tokens):
            if token not in self._idf:
                continue
            tf = term_freqs.get(token, 0)
            if tf == 0:
                continue

            idf = self._idf[token]
            numerator = tf * (self.k1 + 1)
            denominator = tf + self.k1 * (1 - self.b + self.b * doc_len / self._avg_doc_len)
            score += idf * numerator / denominator

        return score

    def search(self, query_tokens: list[str], top_k: int = 10) -> list[tuple[int, float]]:
        """
        检索与查询最相关的top_k文档。

        参数:
            query_tokens: 查询token列表
            top_k: 返回的最大文档数

        返回:
            (文档索引, BM25得分) 列表，按得分降序排列
        """
        if self._doc_count == 0:
            return []

        scores = []
        for i in range(self._doc_count):
            s = self.score(query_tokens, i)
            if s > 0:
                scores.append((i, s))

        scores.sort(key=lambda x: x[1], reverse=True)
        return scores[:top_k]
