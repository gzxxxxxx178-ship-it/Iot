"""
测试BM25检索算法。
"""

import unittest
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from rag.src.bm25 import BM25
from rag.src.tokenizer import tokenize


class TestBM25(unittest.TestCase):
    """BM25检索器单元测试。"""

    def setUp(self):
        """准备测试文档集。"""
        self.docs = [
            "水稻灌溉需要控制水位深度",
            "AWD交替干湿灌溉节水技术",
            "甲烷排放是水稻田温室气体来源",
            "江苏水稻节水灌溉技术规范",
        ]
        self.tokenized = [tokenize(d) for d in self.docs]
        self.bm25 = BM25(k1=1.5, b=0.75)
        self.bm25.index(self.tokenized)

    def test_index_creates_idf(self):
        """测试索引后IDF字典非空。"""
        self.assertTrue(len(self.bm25._idf) > 0)

    def test_exact_match_returns_self(self):
        """测试精确匹配查询返回对应文档。"""
        query = tokenize("AWD交替干湿灌溉节水技术")
        results = self.bm25.search(query, top_k=3)
        self.assertTrue(len(results) > 0)
        # 第一个结果应为index 1（完全匹配的文档）
        self.assertEqual(results[0][0], 1)

    def test_related_query_finds_relevant(self):
        """测试相关查询能找到合适文档。"""
        query = tokenize("水稻节水方法")
        results = self.bm25.search(query, top_k=3)
        self.assertTrue(len(results) > 0)
        # 相关文档index 0, 1, 3应出现在结果中
        found_indices = {r[0] for r in results}
        self.assertTrue(0 in found_indices or 1 in found_indices or 3 in found_indices)

    def test_empty_query(self):
        """测试空查询返回空结果。"""
        results = self.bm25.search([], top_k=10)
        self.assertEqual(len(results), 0)

    def test_empty_corpus(self):
        """测试空语料索引。"""
        bm25_empty = BM25()
        bm25_empty.index([])
        results = bm25_empty.search(tokenize("测试"), top_k=10)
        self.assertEqual(len(results), 0)

    def test_scores_are_positive(self):
        """测试所有得分均为正数。"""
        query = tokenize("灌溉")
        results = self.bm25.search(query, top_k=10)
        for _, score in results:
            self.assertGreater(score, 0.0)

    def test_scores_descending(self):
        """测试结果按得分降序排列。"""
        query = tokenize("灌溉节水")
        results = self.bm25.search(query, top_k=10)
        scores = [s for _, s in results]
        self.assertEqual(scores, sorted(scores, reverse=True))


if __name__ == '__main__':
    unittest.main()
