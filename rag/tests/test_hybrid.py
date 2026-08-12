"""
测试混合检索（RRF融合）。
"""

import unittest
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from rag.src.hybrid import (
    reciprocal_rank_fusion,
    explicit_normalization_fusion,
    HybridRetriever,
)
from rag.src.bm25 import BM25
from rag.src.tfidf import CharTrigramTFIDF
from rag.src.tokenizer import tokenize


class TestRRF(unittest.TestCase):
    """RRF融合函数单元测试。"""

    def test_single_ranking_unchanged_order(self):
        """测试单排序列表经RRF后保持原有顺序。"""
        ranking = [[(0, 0.9), (1, 0.7), (2, 0.5)]]
        result = reciprocal_rank_fusion(ranking, k=60)
        self.assertEqual([r[0] for r in result], [0, 1, 2])

    def test_two_rankings_merge(self):
        """测试两个排序列表正确融合。"""
        r1 = [(0, 0.9), (1, 0.5)]
        r2 = [(2, 0.8), (0, 0.3)]
        result = reciprocal_rank_fusion([r1, r2], k=60)
        # doc 0出现在两个列表中都靠前，应该排在前面
        doc_ids = [r[0] for r in result]
        self.assertIn(0, doc_ids)
        self.assertIn(1, doc_ids)
        self.assertIn(2, doc_ids)

    def test_empty_rankings(self):
        """测试空ranking列表返回空结果。"""
        result = reciprocal_rank_fusion([[], []], k=60)
        self.assertEqual(len(result), 0)

    def test_rrf_with_different_k(self):
        """测试不同k值影响得分但不太影响排序。"""
        r1 = [(0, 0.9)]
        r2 = [(0, 0.1)]
        result_small_k = reciprocal_rank_fusion([r1, r2], k=1)
        result_large_k = reciprocal_rank_fusion([r1, r2], k=100)
        self.assertTrue(len(result_small_k) > 0)
        self.assertTrue(len(result_large_k) > 0)


class TestExplicitNormalizationFusion(unittest.TestCase):
    """显式归一化融合单元测试。"""

    def test_equal_weights(self):
        """测试等权重融合。"""
        r1 = [(0, 1.0), (1, 0.5)]
        r2 = [(1, 0.8), (0, 0.2)]
        result = explicit_normalization_fusion([r1, r2])
        self.assertTrue(len(result) > 0)

    def test_different_weights(self):
        """测试不同权重融合。"""
        r1 = [(0, 1.0)]
        r2 = [(1, 1.0)]
        result = explicit_normalization_fusion([r1, r2], weights=[2.0, 1.0])
        self.assertEqual(result[0][0], 0)


class TestHybridRetrieverIntegration(unittest.TestCase):
    """混合检索器集成测试。"""

    def setUp(self):
        """准备文档和索引。"""
        self.docs = [
            "水稻灌溉需要控制水位深度",
            "AWD交替干湿灌溉节水技术",
            "甲烷排放是水稻田温室气体来源",
            "江苏水稻节水灌溉技术规范",
        ]
        self.bm25 = BM25()
        self.bm25.index([tokenize(d) for d in self.docs])
        self.tfidf = CharTrigramTFIDF()
        self.tfidf.index(self.docs)

    def test_hybrid_rrf_search(self):
        """测试RRF混合检索。"""
        hybrid = HybridRetriever(self.bm25, self.tfidf, fusion_method='rrf')
        results = hybrid.search("水稻灌溉节水", tokenize("水稻灌溉节水"), top_k=3)
        self.assertTrue(len(results) > 0)

    def test_hybrid_normalize_search(self):
        """测试归一化混合检索。"""
        hybrid = HybridRetriever(self.bm25, self.tfidf, fusion_method='normalize')
        results = hybrid.search("节水技术", tokenize("节水技术"), top_k=3)
        self.assertTrue(len(results) > 0)


if __name__ == '__main__':
    unittest.main()
