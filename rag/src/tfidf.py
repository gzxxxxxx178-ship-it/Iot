"""
基于字符3-gram的TF-IDF余弦相似度检索。
使用Python 3标准库实现，不依赖第三方库。
"""

import math
from collections import Counter


def _char_trigrams(text: str) -> list[str]:
    """
    从文本提取字符级别的3-gram（滑动窗口大小为3）。

    参数:
        text: 输入文本字符串

    返回:
        字符3-gram列表；若文本长度不足3，返回原文本的单元素列表
    """
    if len(text) < 3:
        return [text] if text else []
    return [text[i:i + 3] for i in range(len(text) - 2)]


class CharTrigramTFIDF:
    """
    基于字符3-gram的TF-IDF检索器。

    对文档文本提取字符级3-gram作为特征，计算TF-IDF向量，
    使用余弦相似度衡量查询与文档的相关性。
    此方法不依赖语言特定的分词，对中文和混合文本通用。
    """

    def __init__(self):
        """初始化TF-IDF检索器，设定空索引。"""
        self._documents: list[str] = []
        self._doc_count = 0
        self._idf: dict[str, float] = {}
        self._doc_vectors: list[dict[str, float]] = []

    def index(self, documents: list[str]) -> None:
        """
        对文档集合建立TF-IDF索引。

        1. 对每篇文档提取字符3-gram
        2. 计算每个3-gram的IDF
        3. 计算每篇文档的归一化TF-IDF向量

        参数:
            documents: 文档文本字符串列表
        """
        self._documents = documents
        self._doc_count = len(documents)

        if self._doc_count == 0:
            return

        # 统计文档频率
        df = Counter()
        doc_trigrams_list = []
        for doc in documents:
            trigrams = _char_trigrams(doc)
            doc_trigrams_list.append(trigrams)
            unique = set(trigrams)
            for t in unique:
                df[t] += 1

        # 计算IDF（平滑版本）
        self._idf.clear()
        for t, freq in df.items():
            self._idf[t] = math.log((self._doc_count + 1) / (freq + 1)) + 1.0

        # 计算文档TF-IDF向量并归一化
        self._doc_vectors = []
        for trigrams in doc_trigrams_list:
            vec = {}
            tf = Counter(trigrams)
            doc_len = len(trigrams)
            if doc_len == 0:
                self._doc_vectors.append(vec)
                continue
            for t, count in tf.items():
                if t in self._idf:
                    vec[t] = (count / doc_len) * self._idf[t]
            # L2归一化
            norm = math.sqrt(sum(v * v for v in vec.values()))
            if norm > 0:
                vec = {k: v / norm for k, v in vec.items()}
            self._doc_vectors.append(vec)

    def search(self, query: str, top_k: int = 10) -> list[tuple[int, float]]:
        """
        检索与查询文本最相关的top_k文档。

        参数:
            query: 原始查询文本字符串
            top_k: 返回的最大文档数

        返回:
            (文档索引, 余弦相似度得分) 列表，按得分降序排列
        """
        if self._doc_count == 0:
            return []

        trigrams = _char_trigrams(query)
        if not trigrams:
            return []

        # 构建查询TF-IDF向量
        tf = Counter(trigrams)
        query_vec = {}
        for t, count in tf.items():
            if t in self._idf:
                query_vec[t] = (count / len(trigrams)) * self._idf[t]

        norm = math.sqrt(sum(v * v for v in query_vec.values()))
        if norm > 0:
            query_vec = {k: v / norm for k, v in query_vec.items()}

        # 计算与各文档的余弦相似度
        scores = []
        for i, doc_vec in enumerate(self._doc_vectors):
            dot = 0.0
            for t, w in query_vec.items():
                if t in doc_vec:
                    dot += w * doc_vec[t]
            if dot > 0:
                scores.append((i, dot))

        scores.sort(key=lambda x: x[1], reverse=True)
        return scores[:top_k]
