"""
集成测试：加载真实数据文件并执行端到端检索与评估流程。
"""

import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from rag.src.corpus import load_sources, load_chunks, load_questions, validate_corpus_consistency
from rag.src.retriever import Retriever
from rag.src.answerer import generate_answer
from rag.src.metrics import compute_all_metrics, balanced_abstention_accuracy, false_answer_rate
from rag.src.evaluator import select_threshold, write_results


class TestIntegration(unittest.TestCase):
    """端到端集成测试。"""

    @classmethod
    def setUpClass(cls):
        """加载真实语料数据。"""
        data_dir = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            'data',
        )
        cls.sources = load_sources(os.path.join(data_dir, 'sources.json'))
        cls.chunks = load_chunks(os.path.join(data_dir, 'chunks.jsonl'))
        cls.questions = load_questions(os.path.join(data_dir, 'eval_questions.jsonl'))

    def test_sources_count(self):
        """测试来源数量符合预期。"""
        self.assertGreaterEqual(len(self.sources), 12)
        for s in self.sources:
            self.assertIn('source_id', s)
            self.assertIn('title', s)
            self.assertIn('url', s)

    def test_chunks_count(self):
        """测试知识块数量符合预期。"""
        self.assertGreaterEqual(len(self.chunks), 18)
        for c in self.chunks:
            self.assertIn('chunk_id', c)
            self.assertIn('source_id', c)
            self.assertIn('text', c)
            self.assertTrue(len(c['text']) > 20, f"Chunk {c['chunk_id']} text too short")

    def test_questions_count(self):
        """测试问题数量符合预期。"""
        self.assertGreaterEqual(len(self.questions), 30)

    def test_validation_test_partition(self):
        """测试validation和test分区互斥。"""
        val_ids = {q['question_id'] for q in self.questions if q['split'] == 'validation'}
        test_ids = {q['question_id'] for q in self.questions if q['split'] == 'test'}
        self.assertTrue(val_ids.isdisjoint(test_ids),
                        "Validation and test question IDs must be disjoint")
        self.assertEqual(len(val_ids) + len(test_ids), len(self.questions))

    def test_in_domain_count(self):
        """测试in-domain问题数量>=20。"""
        in_domain = [q for q in self.questions if not q['should_abstain']]
        self.assertGreaterEqual(len(in_domain), 20,
                                f"Expected >=20 in-domain, got {len(in_domain)}")

    def test_out_of_domain_count(self):
        """测试out-of-domain问题数量>=10。"""
        ood = [q for q in self.questions if q['should_abstain']]
        self.assertGreaterEqual(len(ood), 10,
                                f"Expected >=10 OOD, got {len(ood)}")

    def test_corpus_consistency(self):
        """测试chunks引用的source_id都在sources中存在。"""
        warnings = validate_corpus_consistency(self.sources, self.chunks)
        self.assertEqual(len(warnings), 0,
                         f"Corpus inconsistency warnings: {warnings}")

    def test_retrieval_pipeline_bm25(self):
        """测试BM25检索管道：索引→检索→生成回答。"""
        retriever = Retriever(mode='bm25')
        retriever.index(self.chunks)
        query = "AWD的安全水位阈值是多少？"
        results = retriever.search(query, top_k=3)
        self.assertTrue(len(results) > 0, "BM25检索应返回至少一个结果")

    def test_retrieval_pipeline_hybrid(self):
        """测试hybrid检索管道：索引→检索→生成回答。"""
        retriever = Retriever(mode='hybrid')
        retriever.index(self.chunks)
        query = "AWD的安全水位阈值是多少？"
        results = retriever.search(query, top_k=3)
        self.assertTrue(len(results) > 0, "Hybrid检索应返回至少一个结果")

    def test_abstain_questions_actually_trigger(self):
        """测试至少部分out-of-domain问题触发拒答。"""
        retriever = Retriever(mode='hybrid')
        retriever.index(self.chunks)

        ood_questions = [q for q in self.questions if q['should_abstain']][:5]
        abstain_count = 0
        for q in ood_questions:
            results = retriever.search(q['question'], top_k=5)
            answer = generate_answer(q['question'], results, threshold=0.01)
            if answer['abstain']:
                abstain_count += 1

        self.assertGreater(abstain_count, 0,
                           "At least some OOD questions should trigger abstention")

    def test_in_domain_answers_have_citations(self):
        """测试域内回答包含有效引用。"""
        retriever = Retriever(mode='hybrid')
        retriever.index(self.chunks)

        valid_chunk_ids = {c['chunk_id'] for c in self.chunks}
        valid_source_ids = {s['source_id'] for s in self.sources}

        in_domain = [q for q in self.questions if not q['should_abstain']][:5]
        for q in in_domain:
            results = retriever.search(q['question'], top_k=5)
            answer = generate_answer(q['question'], results, threshold=0.0)
            if not answer['abstain']:
                for citation in answer['citations']:
                    self.assertIn('#', citation,
                                  f"Citation '{citation}' missing '#' separator")
                    parts = citation.split('#', 1)
                    self.assertIn(parts[0], valid_source_ids,
                                  f"Unknown source_id in citation: {citation}")
                    self.assertIn(parts[1], valid_chunk_ids,
                                  f"Unknown chunk_id in citation: {citation}")


class TestSmokeStratification(unittest.TestCase):
    """smoke分层抽样测试。"""

    @classmethod
    def setUpClass(cls):
        """加载数据。"""
        data_dir = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            'data',
        )
        cls.questions = load_questions(os.path.join(data_dir, 'eval_questions.jsonl'))

    def test_validation_has_in_domain_and_ood(self):
        """测试validation集至少含in-domain和OOD问题各>=2。"""
        val = [q for q in self.questions if q['split'] == 'validation']
        val_in = [q for q in val if not q['should_abstain']]
        val_ood = [q for q in val if q['should_abstain']]
        self.assertGreaterEqual(len(val_in), 2,
                                f"Validation needs >=2 in-domain, got {len(val_in)}")
        self.assertGreaterEqual(len(val_ood), 2,
                                f"Validation needs >=2 OOD, got {len(val_ood)}")

    def test_test_has_in_domain_and_ood(self):
        """测试test集至少含in-domain和OOD问题各>=2。"""
        tst = [q for q in self.questions if q['split'] == 'test']
        tst_in = [q for q in tst if not q['should_abstain']]
        tst_ood = [q for q in tst if q['should_abstain']]
        self.assertGreaterEqual(len(tst_in), 2,
                                f"Test needs >=2 in-domain, got {len(tst_in)}")
        self.assertGreaterEqual(len(tst_ood), 2,
                                f"Test needs >=2 OOD, got {len(tst_ood)}")


class TestOutputSchema(unittest.TestCase):
    """输出CSV schema测试。"""

    @classmethod
    def setUpClass(cls):
        """加载数据并准备临时输出目录。"""
        data_dir = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            'data',
        )
        cls.sources = load_sources(os.path.join(data_dir, 'sources.json'))
        cls.chunks = load_chunks(os.path.join(data_dir, 'chunks.jsonl'))
        cls.questions = load_questions(os.path.join(data_dir, 'eval_questions.jsonl'))
        cls.tmpdir = tempfile.mkdtemp()

    def test_csv_schema_includes_retriever(self):
        """测试输出CSV包含retriever列。"""
        # 使用最小smoke数据运行双retriever评估
        val = [q for q in self.questions if q['split'] == 'validation']
        tst = [q for q in self.questions if q['split'] == 'test']
        val_sample = [q for q in val if not q['should_abstain']][:1] + [q for q in val if q['should_abstain']][:1]
        tst_sample = [q for q in tst if not q['should_abstain']][:1] + [q for q in tst if q['should_abstain']][:1]

        retriever_results = []
        for mode, name in [('bm25', 'bm25'), ('hybrid', 'hybrid')]:
            retriever = Retriever(mode=mode)
            retriever.index(self.chunks)
            threshold, threshold_results, _ = select_threshold(
                retriever, val_sample, top_k=3, num_steps=5, retriever_name=name,
            )
            from rag.src.evaluator import run_evaluation
            val_results = run_evaluation(
                retriever, val_sample, threshold=threshold, top_k=3, retriever_name=name,
            )
            tst_results = run_evaluation(
                retriever, tst_sample, threshold=threshold, top_k=3, retriever_name=name,
            )
            valid_chunk_ids = {c['chunk_id'] for c in self.chunks}
            valid_source_ids = {s['source_id'] for s in self.sources}
            test_metrics = compute_all_metrics(tst_results, valid_chunk_ids, valid_source_ids)
            retriever_results.append({
                'name': name,
                'threshold': threshold,
                'validation_results': val_results,
                'test_results': tst_results,
                'threshold_selection': threshold_results,
                'test_metrics': test_metrics,
            })

        output_dir = os.path.join(self.tmpdir, 'test_output')
        write_results(output_dir=output_dir, mode='smoke', retriever_results=retriever_results,
                      chunks=self.chunks, sources=self.sources)

        # 验证 retrieval_metrics.csv 包含 retriever 列
        import csv
        with open(os.path.join(output_dir, 'retrieval_metrics.csv'), 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            self.assertIn('retriever', reader.fieldnames,
                          "retrieval_metrics.csv missing 'retriever' column")
            rows = list(reader)
            # 两个retriever都应出现在输出中
            retrievers_in_output = {row['retriever'] for row in rows}
            self.assertIn('bm25', retrievers_in_output, "BM25 should appear in retrieval_metrics.csv")
            self.assertIn('hybrid', retrievers_in_output, "Hybrid should appear in retrieval_metrics.csv")

        # 验证 question_results.csv 包含 retriever 列
        with open(os.path.join(output_dir, 'question_results.csv'), 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            self.assertIn('retriever', reader.fieldnames,
                          "question_results.csv missing 'retriever' column")
            rows = list(rows)
            retrievers_in_output = {row['retriever'] for row in rows}
            self.assertIn('bm25', retrievers_in_output)
            self.assertIn('hybrid', retrievers_in_output)

        # 验证 threshold_selection.csv 包含 retriever 列
        with open(os.path.join(output_dir, 'threshold_selection.csv'), 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            self.assertIn('retriever', reader.fieldnames,
                          "threshold_selection.csv missing 'retriever' column")
            rows = list(rows)
            retrievers_in_output = {row['retriever'] for row in rows}
            self.assertIn('bm25', retrievers_in_output)
            self.assertIn('hybrid', retrievers_in_output)

        # 验证 metrics 包含 citation_precision
        with open(os.path.join(output_dir, 'retrieval_metrics.csv'), 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            fieldnames = reader.fieldnames or []
            self.assertIn('citation_precision', fieldnames,
                          "retrieval_metrics.csv missing 'citation_precision' column")

    def test_threshold_includes_zero(self):
        """测试候选阈值包含真实0.0。"""
        val = [q for q in self.questions if q['split'] == 'validation']
        val_sample = [q for q in val if not q['should_abstain']][:1] + [q for q in val if q['should_abstain']][:1]
        retriever = Retriever(mode='bm25')
        retriever.index(self.chunks)
        _, threshold_results, _ = select_threshold(
            retriever, val_sample, top_k=3, num_steps=5, retriever_name='bm25',
        )
        thresholds = {row['threshold'] for row in threshold_results}
        self.assertIn(0.0, thresholds,
                      f"Threshold candidates must include 0.0, got: {thresholds}")


if __name__ == '__main__':
    unittest.main()
