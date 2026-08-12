"""
测试基于规则的拒答与回答生成器。
"""

import unittest
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from rag.src.answerer import (
    is_control_request,
    generate_answer,
    _detect_evidence_conflict,
    _detect_extrapolation_risk,
    _detect_insufficient_evidence,
    _select_evidence,
    _values_conflict_on_same_dimension,
)


class TestControlRequestDetection(unittest.TestCase):
    """控制请求检测功能测试。"""

    def test_open_pump_detected(self):
        """测试"打开水泵"被正确检测。"""
        self.assertTrue(is_control_request("请帮我打开水泵"))

    def test_close_valve_detected(self):
        """测试"关闭阀门"被正确检测。"""
        self.assertTrue(is_control_request("关闭3号灌溉阀"))

    def test_irrigation_with_duration_detected(self):
        """测试带时长的灌溉请求被检测。"""
        self.assertTrue(is_control_request("灌溉30分钟"))

    def test_normal_question_not_detected(self):
        """测试正常问题不被误判为控制请求。"""
        self.assertFalse(is_control_request("AWD方法的水位阈值是多少？"))

    def test_irrigation_word_only_not_detected(self):
        """测试仅提及"灌溉"而不含动作词不触发。"""
        self.assertFalse(is_control_request("灌溉水层深度是多少？"))


class TestExtrapolationDetection(unittest.TestCase):
    """地域外推风险检测测试。"""

    def test_jiangsu_to_jiangxi_detected(self):
        """测试江苏标准用于江西被检测。"""
        result = _detect_extrapolation_risk(
            "将江苏标准用于江西稻田"
        )
        self.assertTrue(result)

    def test_same_province_not_flagged(self):
        """测试同一省份查询不触发。"""
        result = _detect_extrapolation_risk(
            "江苏水稻灌溉标准是什么"
        )
        self.assertFalse(result)


class TestEvidenceConflictDetection(unittest.TestCase):
    """证据冲突检测测试（审查修订：仅top evidence + 同维度真冲突）。"""

    def test_single_chunk_no_conflict(self):
        """测试单个知识块无冲突。"""
        chunks = [{'source_id': 's01', 'tags': ['阈值'], 'text': '降到土表下约15 cm时复灌',
                   'applicability': '', 'limitations': ''}]
        self.assertFalse(_detect_evidence_conflict(chunks))

    def test_complementary_not_conflict(self):
        """测试互补信息不被误判为冲突（复灌触发水位 vs 复灌后水层）。"""
        # chunk_001: 降到15cm → 复灌至~5cm (复灌触发 + 复灌深度)
        # chunk_004: 复灌2-5cm (复灌深度)
        # 这两个不应被判为冲突，因为一个是复灌触发点，一个是复灌目标深度
        chunks = [
            {
                'source_id': 's01', 'tags': ['AWD', '阈值', '水位', '复灌'],
                'text': '当水位降到土表下约15 cm时复灌至约5 cm水层。',
                'applicability': 'IRRI通用AWD建议', 'limitations': '',
            },
            {
                'source_id': 's02', 'tags': ['AWD', '复灌深度', '水层'],
                'text': 'AWD方法在田面无水后若干天再补2—5 cm水层。',
                'applicability': '通用AWD复灌参考', 'limitations': '',
            },
        ]
        # 不应该被标记为冲突：都是AWD阈值相关但测量维度不同
        self.assertFalse(_detect_evidence_conflict(chunks))

    def test_same_source_no_conflict(self):
        """测试同一来源不触发冲突。"""
        chunks = [
            {
                'source_id': 's01', 'tags': ['阈值'],
                'text': '降到土表下约15 cm时复灌。', 'applicability': '', 'limitations': '',
            },
            {
                'source_id': 's01', 'tags': ['水位'],
                'text': '复灌后水层约5 cm。', 'applicability': '', 'limitations': '',
            },
        ]
        self.assertFalse(_detect_evidence_conflict(chunks))

    def test_different_applicability_no_conflict(self):
        """测试适用条件不同不触发冲突（通用 vs 江苏特定）。"""
        chunks = [
            {
                'source_id': 's01', 'tags': ['AWD', '阈值'],
                'text': '降到土表下约15 cm时复灌。', 'applicability': '通用', 'limitations': '',
            },
            {
                'source_id': 's05', 'tags': ['标准', '阈值'],
                'text': '标准规定灌水上限。', 'applicability': '江苏省移栽水稻', 'limitations': '',
            },
        ]
        # 通用vs江苏，适用条件不同
        self.assertFalse(_detect_evidence_conflict(chunks))


    def test_same_dimension_true_conflict(self):
        """测试真正同维度不相容范围返回True（不重叠区间）。"""
        # 两个不同来源给出不重叠的复灌后水层深度：2 cm vs 6 cm
        chunks = [
            {
                'source_id': 's03',
                'tags': ['AWD', '复灌深度'],
                'text': '复灌至2 cm水层。',
                'applicability': '通用AWD建议', 'limitations': '',
            },
            {
                'source_id': 's04',
                'tags': ['AWD', '复灌深度', '标准'],
                'text': '根据标准复灌至6 cm。',
                'applicability': '通用AWD参考', 'limitations': '',
            },
        ]
        # 2cm vs 6cm: 不重叠 → 真冲突
        self.assertTrue(_detect_evidence_conflict(chunks))

    def test_same_dimension_overlapping_no_conflict(self):
        """测试同维度重叠区间不视为冲突。"""
        # [3-6] cm vs 5 cm: 重叠 → 不冲突
        chunks = [
            {
                'source_id': 's03',
                'tags': ['AWD', '复灌深度'],
                'text': '补3-6 cm水层。',
                'applicability': '通用AWD建议', 'limitations': '',
            },
            {
                'source_id': 's04',
                'tags': ['AWD', '复灌深度'],
                'text': '复灌至5 cm。',
                'applicability': '通用AWD参考', 'limitations': '',
            },
        ]
        self.assertFalse(_detect_evidence_conflict(chunks))


class TestInsufficientEvidenceDetection(unittest.TestCase):
    """证据不足检测测试（审查修订：多场景覆盖）。"""

    def setUp(self):
        """准备通用知识块。"""
        self.generic_chunk = {
            'chunk_id': 'c1', 'source_id': 's01', 'title': 'AWD阈值',
            'text': 'AWD在水位降到土表下约15 cm时复灌至约5 cm。',
            'tags': ['AWD', '阈值'],
            'applicability': 'IRRI通用AWD建议；参考值',
            'limitations': '未经江西或中国其他省份田间标定',
        }

    def test_regional_prescription_detected(self):
        """测试区域处方请求被检测为证据不足。"""
        insufficient, reason = _detect_insufficient_evidence(
            "江西赣州的双季稻田AWD应该降到多少厘米复灌？",
            [self.generic_chunk],
        )
        self.assertTrue(insufficient)
        self.assertIn('regional_prescription', reason)

    def test_regional_negation_not_considered_coverage(self):
        """测试limitations中的否定提及不被视作区域覆盖。"""
        # limitations说"未经江西标定" = 江西不适用，不是已覆盖
        insufficient, reason = _detect_insufficient_evidence(
            "江西应该采用什么AWD标准？",
            [self.generic_chunk],
        )
        self.assertTrue(insufficient)
        self.assertIn('regional_prescription', reason)

    def test_field_measurement_request(self):
        """测试田间实测请求被检测。"""
        sim_chunk = {
            **self.generic_chunk,
            'text': '合成软件在环仿真结果。',
            'limitations': '合成仿真结果；无田间标定',
            'tags': ['仿真'],
        }
        insufficient, reason = _detect_insufficient_evidence(
            "本项目田间实测的AWD节水率是多少？",
            [sim_chunk],
        )
        self.assertTrue(insufficient)
        self.assertIn('simulation_not_field', reason)

    def test_growth_stage_exact_threshold(self):
        """测试生育期精确阈值请求被检测。"""
        insufficient, reason = _detect_insufficient_evidence(
            "水稻分蘖期AWD应该降到多少厘米？",
            [self.generic_chunk],
        )
        self.assertTrue(insufficient)
        self.assertIn('growth_stage_threshold', reason)

    def test_llm_accuracy_question(self):
        """测试LLM正确率问题被检测。"""
        insufficient, reason = _detect_insufficient_evidence(
            "这个RAG系统的语言模型生成答案的事实正确率有多高？",
            [self.generic_chunk],
        )
        self.assertTrue(insufficient)
        self.assertIn('no_llm_in_system', reason)

    def test_normal_question_not_insufficient(self):
        """测试正常问题不被标记为证据不足。"""
        sufficient_chunk = {
            **self.generic_chunk,
            'text': '典型安全AWD在水位降到土表下约15 cm时复灌至约5 cm水层。',
            'tags': ['AWD', '阈值', 'IRRI'],
            'applicability': '通用AWD建议',
            'limitations': '',
        }
        insufficient, _ = _detect_insufficient_evidence(
            "AWD的安全水位阈值是多少厘米？",
            [sufficient_chunk],
        )
        self.assertFalse(insufficient)


class TestGenerateAnswer(unittest.TestCase):
    """回答生成功能测试。"""

    def setUp(self):
        """准备测试知识块。"""
        self.awd_chunk = {
            'chunk_id': 'chunk_001',
            'source_id': 's01',
            'title': 'AWD安全复灌阈值',
            'text': '典型安全AWD在水位降到土表下约15 cm时复灌至约5 cm水层。干燥天数受多种因素影响。',
            'tags': ['AWD', '阈值', '水位'],
            'applicability': 'IRRI通用AWD建议；参考值',
            'limitations': '未经江西或中国其他省份田间标定',
        }

    def test_control_request_abstains(self):
        """测试控制请求被拒答。"""
        result = generate_answer(
            "请打开3号水泵",
            [(self.awd_chunk, 0.9)],
            threshold=0.01,
        )
        self.assertTrue(result['abstain'])
        self.assertEqual(result['abstain_reason'], 'control_request')

    def test_regional_extrapolation_abstains(self):
        """测试地域外推被拒答。"""
        result = generate_answer(
            "将江苏标准用于江西稻田灌溉",
            [(self.awd_chunk, 0.9)],
            threshold=0.01,
        )
        self.assertTrue(result['abstain'])
        self.assertEqual(result['abstain_reason'], 'regional_extrapolation')

    def test_insufficient_evidence_abstains(self):
        """测试证据不足时拒答（LLM正确率问题）。"""
        result = generate_answer(
            "这个RAG系统的LLM事实正确率有多高？",
            [(self.awd_chunk, 0.9)],
            threshold=0.01,
        )
        self.assertTrue(result['abstain'])
        self.assertIn('no_llm', result['abstain_reason'])

    def test_below_threshold_abstains(self):
        """测试低于阈值时拒答。"""
        result = generate_answer(
            "水稻分蘖期的具体AWD阈值是多少？",
            [(self.awd_chunk, 0.001)],
            threshold=0.01,
        )
        self.assertTrue(result['abstain'])

    def test_in_domain_generates_answer(self):
        """测试正常域内问题生成回答和引用。"""
        result = generate_answer(
            "AWD的安全水位阈值是多少？",
            [(self.awd_chunk, 0.9)],
            threshold=0.01,
        )
        self.assertFalse(result['abstain'])
        self.assertTrue(len(result['answer']) > 0)
        self.assertTrue(len(result['citations']) > 0)
        self.assertIn('s01#chunk_001', result['citations'])

    def test_citations_only_from_evidence(self):
        """测试引用只来自实际使用的证据。"""
        matching = {
            'chunk_id': 'chunk_001', 'source_id': 's01',
            'title': 'AWD安全复灌阈值',
            'text': '典型安全AWD在水位降到土表下约15 cm时复灌至约5 cm水层。',
            'tags': ['AWD', '阈值'],
            'applicability': 'IRRI通用AWD建议', 'limitations': '',
        }
        irrelevant = {
            'chunk_id': 'chunk_020', 'source_id': 's12',
            'title': 'RAG安全边界',
            'text': '本RAG系统遵循安全设计约束，不直接生成或下发泵阀控制命令。',
            'tags': ['RAG', '安全边界'],
            'applicability': '系统设计约束', 'limitations': '',
        }
        results = [(matching, 0.9), (irrelevant, 0.3)]
        result = generate_answer(
            "AWD的安全水位阈值是多少？",
            results,
            threshold=0.01,
        )
        self.assertFalse(result['abstain'])
        # 引用应包含匹配的知识块
        self.assertIn('s01#chunk_001', result['citations'])
        # 不相关的知识块不应出现在引用中
        all_cited = set(result['citations'])
        self.assertTrue(
            's12#chunk_020' not in all_cited or len(result['citations']) <= 3,
        )


if __name__ == '__main__':
    unittest.main()
