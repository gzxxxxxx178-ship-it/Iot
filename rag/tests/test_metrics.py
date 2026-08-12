"""
测试评估指标计算的正确性和边界情况。
"""

import unittest
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from rag.src.metrics import (
    recall_at_k,
    mrr_at_k,
    ndcg_at_k,
    abstention_accuracy,
    false_answer_rate,
    false_abstain_rate,
    ood_recall,
    in_domain_answer_recall,
    balanced_abstention_accuracy,
    citation_id_validity,
    citation_coverage,
    citation_precision,
    compute_all_metrics,
)


class TestRecall(unittest.TestCase):
    """Recall@k计算测试。"""

    def test_perfect_recall(self):
        """测试全部命中返回1.0。"""
        self.assertEqual(recall_at_k(['a', 'b', 'c'], ['a', 'b'], k=3), 1.0)

    def test_partial_recall(self):
        """测试部分命中返回对应比例。"""
        self.assertEqual(recall_at_k(['a', 'd', 'e'], ['a', 'b'], k=3), 0.5)

    def test_no_recall(self):
        """测试无命中返回0.0。"""
        self.assertEqual(recall_at_k(['d', 'e', 'f'], ['a', 'b'], k=3), 0.0)

    def test_k_limits_recall(self):
        """测试k截断正确限制搜索范围。"""
        # k=1只考虑第一个结果
        self.assertEqual(recall_at_k(['a', 'b', 'c'], ['b'], k=1), 0.0)

    def test_empty_gold(self):
        """测试空gold返回0.0。"""
        self.assertEqual(recall_at_k(['a', 'b'], [], k=3), 0.0)


class TestMRR(unittest.TestCase):
    """MRR@k计算测试。"""

    def test_first_position(self):
        """测试首个位置命中返回1.0。"""
        self.assertEqual(mrr_at_k(['a', 'b', 'c'], ['a'], k=3), 1.0)

    def test_second_position(self):
        """测试第二个位置命中返回1/2。"""
        self.assertEqual(mrr_at_k(['x', 'a', 'c'], ['a'], k=3), 0.5)

    def test_no_hit(self):
        """测试无命中返回0.0。"""
        self.assertEqual(mrr_at_k(['x', 'y', 'z'], ['a'], k=3), 0.0)

    def test_k_truncation(self):
        """测试k截断。"""
        # 相关文档在第5位但k=3
        self.assertEqual(mrr_at_k(['a', 'b', 'c', 'd', 'e'], ['e'], k=3), 0.0)


class TestNDCG(unittest.TestCase):
    """nDCG@k计算测试。"""

    def test_perfect_ordering(self):
        """测试完美排序（二元相关度下所有相关文档在无关文档之前）返回1.0。"""
        result = ndcg_at_k(['a', 'b', 'c'], ['a', 'b', 'c'], k=3)
        self.assertAlmostEqual(result, 1.0, places=6)

    def test_imperfect_ordering_with_irrelevant_at_top(self):
        """测试非相关文档占据高位时nDCG低于1.0。"""
        # 'x'是不相关文档，排在第1位，相关文档排在2、3位
        result = ndcg_at_k(['x', 'a', 'b'], ['a', 'b'], k=3)
        self.assertLess(result, 1.0)
        self.assertGreater(result, 0.0)

    def test_all_relevant_same_order_equals_one(self):
        """测试二元相关度下所有相关文档以任何顺序排列在前k位都等于1.0。"""
        # 二元相关度下，只要所有的gold都在前k位被发现，IDCG=DCG
        result = ndcg_at_k(['b', 'a', 'c'], ['a', 'b'], k=3)
        self.assertAlmostEqual(result, 1.0, places=6)

    def test_graded_relevance_penalizes_downgraded(self):
        """测试分级相关度下，高相关文档被降位会降低nDCG。"""
        # a: 高相关(3.0), b: 中相关(2.0), c: 低相关(1.0)
        relevance_map = {'a': 3.0, 'b': 2.0, 'c': 1.0}
        # 理想排序: a, b, c
        # 降位排序: c, a, b (低相关排到高位)
        perfect = ndcg_at_k(['a', 'b', 'c'], ['a', 'b', 'c'], k=3, relevance_map=relevance_map)
        degraded = ndcg_at_k(['c', 'a', 'b'], ['a', 'b', 'c'], k=3, relevance_map=relevance_map)
        self.assertAlmostEqual(perfect, 1.0, places=6)
        self.assertLess(degraded, perfect)

    def test_empty_gold(self):
        """测试无相关文档返回0.0。"""
        self.assertEqual(ndcg_at_k(['a', 'b'], [], k=3), 0.0)

    def test_no_hit(self):
        """测试无命中返回0.0。"""
        self.assertEqual(ndcg_at_k(['x', 'y'], ['a', 'b'], k=3), 0.0)


class TestAbstentionAccuracy(unittest.TestCase):
    """拒答准确率测试。"""

    def test_perfect_accuracy(self):
        """测试完全正确返回1.0。"""
        self.assertEqual(
            abstention_accuracy([True, False, True], [True, False, True]),
            1.0,
        )

    def test_partial_accuracy(self):
        """测试部分正确。"""
        self.assertEqual(
            abstention_accuracy([True, True, False], [True, False, True]),
            1.0 / 3.0,
        )


class TestFalseAnswerRate(unittest.TestCase):
    """误答率测试。"""

    def test_no_false_answers(self):
        """测试无误答返回0.0。"""
        self.assertEqual(
            false_answer_rate([True, False, True], [True, False, True]),
            0.0,
        )

    def test_all_false_answers(self):
        """测试全部误答返回1.0。"""
        self.assertEqual(
            false_answer_rate([False, False], [True, True]),
            1.0,
        )

    def test_no_should_abstain(self):
        """测试无应拒答问题返回0.0。"""
        self.assertEqual(
            false_answer_rate([False, False], [False, False]),
            0.0,
        )


class TestCitationIdValidity(unittest.TestCase):
    """引用ID有效性测试。"""

    def test_all_valid(self):
        """测试全部有效返回1.0。"""
        self.assertEqual(
            citation_id_validity(
                [['s01#chunk_001', 's02#chunk_002']],
                {'chunk_001', 'chunk_002'},
                {'s01', 's02'},
            ),
            1.0,
        )

    def test_invalid_format(self):
        """测试无效格式被正确判定。"""
        self.assertEqual(
            citation_id_validity(
                [['invalid_format']],
                {'chunk_001'},
                {'s01'},
            ),
            0.0,
        )

    def test_invalid_id(self):
        """测试不存在的ID被判定为无效。"""
        self.assertEqual(
            citation_id_validity(
                [['s01#chunk_999']],
                {'chunk_001'},
                {'s01'},
            ),
            0.0,
        )


class TestCitationCoverage(unittest.TestCase):
    """引用覆盖率测试。"""

    def test_full_coverage(self):
        """测试完全覆盖返回1.0。"""
        self.assertEqual(
            citation_coverage(
                [['s01#a', 's02#b']],
                [['a', 'b']],
            ),
            1.0,
        )

    def test_partial_coverage(self):
        """测试部分覆盖。"""
        self.assertEqual(
            citation_coverage(
                [['s01#a']],
                [['a', 'b']],
            ),
            0.5,
        )


class TestFalseAbstainRate(unittest.TestCase):
    """误拒率测试。"""

    def test_no_false_abstains(self):
        """测试无误拒返回0.0。"""
        self.assertEqual(
            false_abstain_rate([False, True, False], [False, True, True]),
            0.0,
        )

    def test_all_false_abstains(self):
        """测试全部误拒返回1.0。"""
        self.assertEqual(
            false_abstain_rate([True, True], [False, False]),
            1.0,
        )

    def test_no_should_answer(self):
        """测试无应回答问题返回0.0。"""
        self.assertEqual(
            false_abstain_rate([True, True], [True, True]),
            0.0,
        )


class TestOODRecall(unittest.TestCase):
    """OOD召回率测试。"""

    def test_perfect_ood(self):
        """测试全部OOD问题被正确拒答返回1.0。"""
        self.assertEqual(
            ood_recall([True, True, False], [True, True, False]),
            1.0,
        )

    def test_partial_ood(self):
        """测试部分OOD问题被拒答。"""
        self.assertEqual(
            ood_recall([True, False], [True, True]),
            0.5,
        )


class TestInDomainAnswerRecall(unittest.TestCase):
    """In-domain回答召回率测试。"""

    def test_perfect_answer(self):
        """测试全部in-domain问题被回答返回1.0。"""
        self.assertEqual(
            in_domain_answer_recall([False, False, True], [False, False, True]),
            1.0,
        )

    def test_partial_answer(self):
        """测试部分in-domain问题被拒答。"""
        self.assertEqual(
            in_domain_answer_recall([True, False], [False, False]),
            0.5,
        )


class TestCitationPrecision(unittest.TestCase):
    """引用精度测试。"""

    def test_perfect_precision(self):
        """测试所有引用命中gold返回1.0。"""
        self.assertEqual(
            citation_precision(
                [['s01#a', 's01#b']],
                [['a', 'b']],
            ),
            1.0,
        )

    def test_partial_precision(self):
        """测试部分引用命中gold。"""
        self.assertEqual(
            citation_precision(
                [['s01#a', 's01#c']],  # c不在gold中
                [['a', 'b']],
            ),
            0.5,
        )

    def test_zero_precision(self):
        """测试全部引用不命中gold返回0.0。"""
        self.assertEqual(
            citation_precision(
                [['s01#x', 's01#y']],
                [['a', 'b']],
            ),
            0.0,
        )

    def test_no_gold_ids_skipped(self):
        """测试OOD问题无gold时跳过（不计入分母）。"""
        self.assertEqual(
            citation_precision(
                [['s01#x']],
                [[]],  # OOD问题
            ),
            1.0,  # 没有有效引用需评估
        )

    def test_no_citations(self):
        """测试全部拒答无引用返回1.0。"""
        self.assertEqual(
            citation_precision(
                [[]],
                [['a', 'b']],
            ),
            1.0,
        )


class TestBalancedAbstentionAccuracy(unittest.TestCase):
    """平衡拒答准确率测试。"""

    def test_perfect_balanced(self):
        """测试完美平衡返回1.0。"""
        # OOD: all abstained (2/2), In-domain: all answered (2/2)
        result = balanced_abstention_accuracy(
            [True, True, False, False],
            [True, True, False, False],
        )
        self.assertAlmostEqual(result, 1.0, places=6)

    def test_all_abstain_penalized(self):
        """测试全部拒答策略得分低于1.0（因in-domain也被拒答）。"""
        # OOD recall=1.0, in-domain answer recall=0.0 → balanced=0.5
        result = balanced_abstention_accuracy(
            [True, True, True, True],
            [True, True, False, False],
        )
        self.assertAlmostEqual(result, 0.5, places=6)

    def test_all_answer_penalized(self):
        """测试全部回答策略得分低于1.0（因OOD也被回答）。"""
        # OOD recall=0.0, in-domain answer recall=1.0 → balanced=0.5
        result = balanced_abstention_accuracy(
            [False, False, False, False],
            [True, True, False, False],
        )
        self.assertAlmostEqual(result, 0.5, places=6)


class TestComputeAllMetrics(unittest.TestCase):
    """综合指标计算测试。"""

    def test_basic_metrics_computation(self):
        """测试基本指标计算不报错且返回合理值。"""
        results = [
            {
                'should_abstain': False,
                'abstained': False,
                'gold_chunk_ids': ['a', 'b'],
                'retrieved_chunk_ids': ['a', 'c', 'b', 'd'],
                'citations': ['s01#a', 's01#c'],
            },
            {
                'should_abstain': True,
                'abstained': True,
                'gold_chunk_ids': [],
                'retrieved_chunk_ids': ['x'],
                'citations': [],
            },
        ]
        metrics = compute_all_metrics(
            results,
            valid_chunk_ids={'a', 'b', 'c', 'd', 'x'},
            valid_source_ids={'s01'},
        )
        self.assertIn('recall@1', metrics)
        self.assertIn('recall@3', metrics)
        self.assertIn('mrr@10', metrics)
        self.assertIn('ndcg@3', metrics)
        self.assertIn('abstention_accuracy', metrics)
        self.assertIn('false_answer_rate', metrics)
        self.assertIn('false_abstain_rate', metrics)
        self.assertIn('ood_recall', metrics)
        self.assertIn('in_domain_answer_recall', metrics)
        self.assertIn('balanced_abstention_accuracy', metrics)
        self.assertIn('citation_id_validity', metrics)
        self.assertIn('citation_coverage', metrics)
        self.assertIn('citation_precision', metrics)
        # 所有指标值在合理范围内
        for k, v in metrics.items():
            self.assertTrue(0.0 <= v <= 1.0, f"{k}={v} out of [0,1]")

    def test_balanced_accuracy_against_all_abstain(self):
        """测试阈值选择不会被"可完美区分"的合成数据误导为全部拒答。"""
        # 构造场景：所有in-domain问题检索得分较高，OOD问题得分较低
        # 一个合理阈值应能区分两者，而非全部拒答
        results = [
            # in-domain, 不应拒答，检索命中了gold chunk
            {
                'should_abstain': False,
                'abstained': False,
                'gold_chunk_ids': ['a'],
                'retrieved_chunk_ids': ['a', 'x', 'y'],
                'citations': ['s01#a'],
            },
            {
                'should_abstain': False,
                'abstained': False,
                'gold_chunk_ids': ['b'],
                'retrieved_chunk_ids': ['b', 'y', 'z'],
                'citations': ['s01#b'],
            },
            # OOD, 应拒答
            {
                'should_abstain': True,
                'abstained': True,
                'gold_chunk_ids': [],
                'retrieved_chunk_ids': [],
                'citations': [],
            },
            {
                'should_abstain': True,
                'abstained': True,
                'gold_chunk_ids': [],
                'retrieved_chunk_ids': [],
                'citations': [],
            },
        ]
        metrics = compute_all_metrics(
            results,
            valid_chunk_ids={'a', 'b', 'x', 'y', 'z'},
            valid_source_ids={'s01'},
        )
        # balanced accuracy 应为 1.0，不应偏向全部拒答
        self.assertAlmostEqual(metrics['balanced_abstention_accuracy'], 1.0, places=6)
        self.assertEqual(metrics['false_answer_rate'], 0.0)
        self.assertEqual(metrics['false_abstain_rate'], 0.0)


if __name__ == '__main__':
    unittest.main()
