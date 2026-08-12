"""
测试字符3-gram TF-IDF检索器。
"""

import unittest
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from rag.src.tfidf import CharTrigramTFIDF, _char_trigrams


class TestCharTrigrams(unittest.TestCase):
    """字符3-gram提取函数单元测试。"""

    def test_short_text(self):
        """测试短文本（不足3字符）返回原文本。"""
        self.assertEqual(_char_trigrams("AB"), ["AB"])

    def test_empty_text(self):
        """测试空文本返回空列表。"""
        self.assertEqual(_char_trigrams(""), [])

    def test_normal_text(self):
        """测试正常长度文本。"""
        trigrams = _char_trigrams("ABCD")
        self.assertEqual(len(trigrams), 2)
        self.assertIn("ABC", trigrams)
        self.assertIn("BCD", trigrams)


class TestCharTrigramTFIDF(unittest.TestCase):
    """字符3-gram TF-IDF检索器单元测试。"""

    def setUp(self):
        """准备测试文档集（使用原始文本，不需要分词）。"""
        self.docs = [
            "水稻灌溉需要控制水位深度",
            "AWD交替干湿灌溉节水技术",
            "甲烷排放是水稻田温室气体来源",
            "江苏水稻节水灌溉技术规范",
        ]
        self.tfidf = CharTrigramTFIDF()
        self.tfidf.index(self.docs)

    def test_index_creates_vectors(self):
        """测试索引后向量列表非空。"""
        self.assertTrue(len(self.tfidf._doc_vectors) > 0)
        self.assertEqual(len(self.tfidf._doc_vectors), len(self.docs))

    def test_exact_text_returns_self(self):
        """测试精确匹配返回对应文档。"""
        results = self.tfidf.search("AWD交替干湿灌溉节水技术", top_k=3)
        self.assertTrue(len(results) > 0)
        # index 1的文档是完全匹配的
        self.assertEqual(results[0][0], 1)

    def test_similar_text_finds_relevant(self):
        """测试相似文本能找到相关文档。"""
        results = self.tfidf.search("节水灌溉方法", top_k=3)
        self.assertTrue(len(results) > 0)

    def test_cosine_scores_in_range(self):
        """测试余弦相似度在[0,1]范围内。"""
        results = self.tfidf.search("水稻", top_k=10)
        for _, score in results:
            self.assertGreaterEqual(score, 0.0)
            self.assertLessEqual(score, 1.0)

    def test_empty_query(self):
        """测试空查询返回空结果。"""
        results = self.tfidf.search("", top_k=10)
        self.assertEqual(len(results), 0)

    def test_empty_corpus(self):
        """测试空语料索引。"""
        tfidf_empty = CharTrigramTFIDF()
        tfidf_empty.index([])
        results = tfidf_empty.search("测试", top_k=10)
        self.assertEqual(len(results), 0)


if __name__ == '__main__':
    unittest.main()
