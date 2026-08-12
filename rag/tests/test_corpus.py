"""
测试语料库加载与校验。
"""

import json
import os
import tempfile
import unittest
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from rag.src.corpus import (
    load_sources,
    load_chunks,
    load_questions,
    validate_corpus_consistency,
)


class TestCorpusLoading(unittest.TestCase):
    """语料加载功能测试。"""

    def setUp(self):
        """创建临时测试文件。"""
        self.tmpdir = tempfile.mkdtemp()

    def _write_json(self, filename, data):
        """写入JSON文件辅助方法。"""
        path = os.path.join(self.tmpdir, filename)
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False)
        return path

    def _write_jsonl(self, filename, lines):
        """写入JSONL文件辅助方法。"""
        path = os.path.join(self.tmpdir, filename)
        with open(path, 'w', encoding='utf-8') as f:
            for line in lines:
                f.write(json.dumps(line, ensure_ascii=False) + '\n')
        return path

    def test_load_sources_valid(self):
        """测试加载有效的sources文件。"""
        data = [{
            'source_id': 's01',
            'title': 'Test Source',
            'institution': 'Test',
            'url': 'https://example.com',
            'access_date': '2026-08-12',
            'scope': 'Test scope',
            'limitations': 'Test limitations',
        }]
        path = self._write_json('sources.json', data)
        sources = load_sources(path)
        self.assertEqual(len(sources), 1)
        self.assertEqual(sources[0]['source_id'], 's01')

    def test_load_sources_missing_field_raises(self):
        """测试缺少必填字段时抛出异常。"""
        data = [{
            'source_id': 's01',
            'title': 'Test Source',
            # 缺少其他必填字段
        }]
        path = self._write_json('sources.json', data)
        with self.assertRaises(ValueError):
            load_sources(path)

    def test_load_sources_duplicate_id_raises(self):
        """测试重复source_id抛出异常。"""
        data = [
            {
                'source_id': 's01', 'title': 'A', 'institution': 'X',
                'url': 'https://a.com', 'access_date': '2026-01-01',
                'scope': 'S', 'limitations': 'L',
            },
            {
                'source_id': 's01', 'title': 'B', 'institution': 'Y',
                'url': 'https://b.com', 'access_date': '2026-01-02',
                'scope': 'S', 'limitations': 'L',
            },
        ]
        path = self._write_json('sources.json', data)
        with self.assertRaises(ValueError):
            load_sources(path)

    def test_load_chunks_valid(self):
        """测试加载有效的chunks文件。"""
        lines = [{
            'chunk_id': 'chunk_001',
            'source_id': 's01',
            'title': 'Test Chunk',
            'text': '测试内容',
            'tags': ['AWD', '测试'],
            'applicability': '通用',
            'limitations': '无',
        }]
        path = self._write_jsonl('chunks.jsonl', lines)
        chunks = load_chunks(path)
        self.assertEqual(len(chunks), 1)
        self.assertEqual(chunks[0]['chunk_id'], 'chunk_001')

    def test_load_chunks_invalid_json(self):
        """测试无效JSON行抛出异常。"""
        path = os.path.join(self.tmpdir, 'chunks.jsonl')
        with open(path, 'w', encoding='utf-8') as f:
            f.write('this is not json\n')
        with self.assertRaises(ValueError):
            load_chunks(path)

    def test_load_chunks_tags_not_list_raises(self):
        """测试tags不是列表时抛出异常。"""
        lines = [{
            'chunk_id': 'chunk_001',
            'source_id': 's01',
            'title': 'Test',
            'text': '内容',
            'tags': 'not_a_list',
            'applicability': '通用',
            'limitations': '无',
        }]
        path = self._write_jsonl('chunks.jsonl', lines)
        with self.assertRaises(ValueError):
            load_chunks(path)

    def test_load_questions_valid(self):
        """测试加载有效的问题文件。"""
        lines = [{
            'question_id': 'q_001',
            'question': '测试问题',
            'split': 'validation',
            'should_abstain': False,
            'gold_chunk_ids': ['chunk_001'],
        }]
        path = self._write_jsonl('questions.jsonl', lines)
        questions = load_questions(path)
        self.assertEqual(len(questions), 1)

    def test_load_questions_ood_without_gold_ids(self):
        """测试should_abstain=True的问题不需要gold_chunk_ids。"""
        lines = [{
            'question_id': 'q_001',
            'question': '测试',
            'split': 'test',
            'should_abstain': True,
        }]
        path = self._write_jsonl('questions.jsonl', lines)
        questions = load_questions(path)
        self.assertEqual(len(questions), 1)

    def test_load_questions_indomain_without_gold_raises(self):
        """测试in-domain问题缺少gold_chunk_ids抛出异常。"""
        lines = [{
            'question_id': 'q_001',
            'question': '测试',
            'split': 'validation',
            'should_abstain': False,
            # 缺少gold_chunk_ids
        }]
        path = self._write_jsonl('questions.jsonl', lines)
        with self.assertRaises(ValueError):
            load_questions(path)

    def test_validate_corpus_consistency(self):
        """测试语料一致性校验。"""
        sources = [{
            'source_id': 's01', 'title': 'S', 'institution': 'I',
            'url': 'https://x.com', 'access_date': '2026-01-01',
            'scope': 'S', 'limitations': 'L',
        }]
        chunks = [{
            'chunk_id': 'c1', 'source_id': 's02',
            'title': 'T', 'text': 'X', 'tags': ['a'],
            'applicability': 'A', 'limitations': 'L',
        }]
        warnings = validate_corpus_consistency(sources, chunks)
        self.assertEqual(len(warnings), 1)
        self.assertIn('s02', warnings[0])


if __name__ == '__main__':
    unittest.main()
