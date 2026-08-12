"""
检索器模块：封装BM25、字符3-gram TF-IDF和混合检索的统一接口。
"""

from rag.src.tokenizer import tokenize
from rag.src.bm25 import BM25
from rag.src.tfidf import CharTrigramTFIDF
from rag.src.hybrid import HybridRetriever


class Retriever:
    """
    统一检索器。

    封装语料索引和检索接口，支持BM25单模式、TF-IDF单模式和hybrid混合模式。
    混合模式默认使用RRF融合。
    """

    def __init__(self, mode: str = 'hybrid'):
        """
        初始化检索器。

        参数:
            mode: 检索模式，'bm25'、'tfidf' 或 'hybrid'（默认）
        """
        if mode not in ('bm25', 'tfidf', 'hybrid'):
            raise ValueError(f"Unknown mode: {mode}, expected 'bm25', 'tfidf', or 'hybrid'")
        self.mode = mode
        self._chunks: list[dict] = []
        self._bm25 = BM25()
        self._tfidf = CharTrigramTFIDF()
        self._hybrid: HybridRetriever | None = None
        self._indexed = False

    def index(self, chunks: list[dict]) -> None:
        """
        对知识块集合建立索引。

        为BM25准备分词后的文档token列表，为TF-IDF准备原始文本，
        并构建hybrid检索器。

        参数:
            chunks: 知识块字典列表，每块至少包含'text'字段
        """
        self._chunks = chunks

        # 为BM25准备分词后的文档
        tokenized_docs = [tokenize(chunk['text']) for chunk in chunks]
        self._bm25.index(tokenized_docs)

        # 为TF-IDF准备原始文本（保留字符级信息）
        raw_texts = [chunk['text'] for chunk in chunks]
        self._tfidf.index(raw_texts)

        # 构建混合检索器
        if self.mode == 'hybrid':
            self._hybrid = HybridRetriever(self._bm25, self._tfidf)

        self._indexed = True

    def search(self, query: str, top_k: int = 10) -> list[tuple[dict, float]]:
        """
        执行检索，返回知识块和得分。

        参数:
            query: 查询文本字符串
            top_k: 返回的最大结果数

        返回:
            (知识块字典, 得分) 列表，按得分降序排列

        异常:
            RuntimeError: 尚未建立索引
        """
        if not self._indexed:
            raise RuntimeError("Retriever not indexed. Call index() first.")

        query_tokens = tokenize(query)

        if self.mode == 'bm25':
            results = self._bm25.search(query_tokens, top_k=top_k)
        elif self.mode == 'tfidf':
            results = self._tfidf.search(query, top_k=top_k)
        elif self.mode == 'hybrid':
            results = self._hybrid.search(query, query_tokens, top_k=top_k)
        else:
            raise ValueError(f"Unknown mode: {self.mode}")

        return [(self._chunks[idx], score) for idx, score in results]

    def search_raw(self, query: str, top_k: int = 10) -> list[tuple[int, float]]:
        """
        执行检索，返回文档索引和得分（供评估使用）。

        参数:
            query: 查询文本字符串
            top_k: 返回的最大结果数

        返回:
            (文档索引, 得分) 列表，按得分降序排列

        异常:
            RuntimeError: 尚未建立索引
        """
        if not self._indexed:
            raise RuntimeError("Retriever not indexed. Call index() first.")

        query_tokens = tokenize(query)

        if self.mode == 'bm25':
            return self._bm25.search(query_tokens, top_k=top_k)
        elif self.mode == 'tfidf':
            return self._tfidf.search(query, top_k=top_k)
        elif self.mode == 'hybrid':
            return self._hybrid.search(query, query_tokens, top_k=top_k)
        else:
            raise ValueError(f"Unknown mode: {self.mode}")

    def get_chunk(self, idx: int) -> dict:
        """
        根据索引获取知识块。

        参数:
            idx: 文档索引

        返回:
            知识块字典

        异常:
            IndexError: 索引超出范围
        """
        if idx < 0 or idx >= len(self._chunks):
            raise IndexError(f"Chunk index {idx} out of range [0, {len(self._chunks)})")
        return self._chunks[idx]
