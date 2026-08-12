"""
测试中文bigram/英文数字词项分词器。
"""

import unittest
import sys
import os

# 确保项目根目录在Python路径中
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from rag.src.tokenizer import tokenize


class TestTokenizer(unittest.TestCase):
    """分词器单元测试。"""

    def test_empty_text(self):
        """测试空文本返回空列表。"""
        self.assertEqual(tokenize(""), [])

    def test_pure_chinese_bigram(self):
        """测试纯中文文本生成bigram和unigram。"""
        tokens = tokenize("水稻灌溉")
        # bigrams: "水稻", "稻灌", "灌溉"; unigrams: "水", "稻", "灌", "溉"
        self.assertIn("水稻", tokens)
        self.assertIn("稻灌", tokens)
        self.assertIn("灌溉", tokens)
        self.assertIn("水", tokens)
        self.assertIn("稻", tokens)

    def test_pure_english(self):
        """测试纯英文文本按词项分词。"""
        tokens = tokenize("Hello World test")
        self.assertIn("hello", tokens)
        self.assertIn("world", tokens)
        self.assertIn("test", tokens)

    def test_mixed_text(self):
        """测试中英文混合文本。"""
        tokens = tokenize("AWD方法节水30%")
        # 英文部分
        self.assertIn("awd", tokens)
        self.assertIn("30", tokens)
        # 中文部分
        self.assertIn("方法", tokens)
        self.assertIn("节水", tokens)

    def test_no_english_chars_in_chinese_bigram(self):
        """测试中文bigram不包含英文字符。"""
        tokens = tokenize("水稻")
        for t in tokens:
            if len(t) == 2:
                self.assertTrue(all('一' <= c <= '鿿' or '㐀' <= c <= '䶿' for c in t),
                                f"bigram '{t}' contains non-CJK characters")

    def test_numbers_preserved(self):
        """测试数字被保留为词项。"""
        tokens = tokenize("温度25度 水位15cm")
        self.assertIn("25", tokens)
        self.assertIn("15cm", tokens)


if __name__ == '__main__':
    unittest.main()
