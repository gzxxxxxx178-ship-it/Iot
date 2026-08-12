"""
基于确定性extractive/template方式的回答生成模块。
不调用任何LLM，所有回答由规则和模板生成。
"""

import re


# 直接控制请求的检测关键词（中文和英文）
_CONTROL_KEYWORDS = [
    '打开', '关闭', '启动', '停止', '调节', '控制', '运行',
    '泵', '阀', '灌溉泵', '水泵', '电磁阀',
    '灌溉', '排水', '开关',
    '执行', '下发', '遥控', '指令',
    'open', 'close', 'start', 'stop', 'control', 'pump', 'valve',
]


def is_control_request(query: str) -> bool:
    """
    检测查询是否为直接控制设备请求。

    通过关键词匹配判断用户是否在请求对泵、阀等
    执行器进行直接操作。该检测优先于其他所有回答逻辑。

    参数:
        query: 用户查询文本

    返回:
        如果是控制请求返回True，否则False
    """
    query_lower = query.lower()
    # 检查多个关键词组合（至少包含一个动作词和一个设备词）
    action_words = {'打开', '关闭', '启动', '停止', '调节', '控制', '运行',
                    '执行', '下发', '遥控', 'open', 'close', 'start', 'stop', 'control'}
    device_words = {'泵', '阀', '灌溉泵', '水泵', '电磁阀', '灌溉', 'pump', 'valve'}

    has_action = any(w in query_lower for w in action_words)
    has_device = any(w in query_lower for w in device_words)

    # 同时包含动作词和设备词，判定为控制请求
    if has_action and has_device:
        return True

    # 直接包含完整控制指令模式
    control_patterns = [
        r'(打开|关闭|启动|停止|调节|控制)\s*(泵|阀|灌溉|水泵)',
        r'(泵|阀|灌溉|水泵)\s*(打开|关闭|启动|停止)',
        r'(灌溉|排水)\s*\d+\s*(分钟|小时|秒)',
    ]
    for pattern in control_patterns:
        if re.search(pattern, query):
            return True

    return False


def _detect_extrapolation_risk(query: str) -> bool:
    """
    检测查询是否存在地域外推风险。

    检查是否将特定地区的标准/阈值应用到不匹配的地区（如将江苏标准用于江西）。

    参数:
        query: 用户查询文本

    返回:
        如果存在地域外推风险返回True
    """
    province_pairs = [
        ('江苏', '江西'), ('江苏', '湖南'), ('江苏', '湖北'),
        ('江苏', '安徽'), ('江苏', '广东'), ('江苏', '广西'),
        ('irri', '江西'), ('irri', '湖南'),
    ]
    query_lower = query.lower()
    for src, tgt in province_pairs:
        if src in query_lower and tgt in query_lower:
            return True
    return False


def _detect_insufficient_evidence(
    query: str,
    evidence_chunks: list[dict],
) -> tuple[bool, str]:
    """
    检测当前证据是否不足以支撑可靠回答。

    基于查询特征与知识块的applicability/limitations/text/tags进行判定，
    不使用question_id或test标签。拒答原因可区分。

    参数:
        query: 用户查询文本
        evidence_chunks: 将用于回答的证据知识块列表（最多3个）

    返回:
        (是否证据不足, 拒答子原因字符串)
    """
    query_lower = query.lower()
    all_tags = set()
    all_text = ''
    all_applicability = ''
    all_limitations = ''
    for c in evidence_chunks:
        all_tags.update(c.get('tags', []))
        all_text += c.get('text', '') + ' '
        all_applicability += c.get('applicability', '') + ' '
        all_limitations += c.get('limitations', '') + ' '

    # 1) 请求特定外地/区域的最佳天数或精确处方，但库中只有通用或异地资料
    specific_regions = ['江西', '湖南', '湖北', '广东', '广西', '安徽', '四川', '云南',
                       '黑龙江', '吉林', '辽宁', '赣州', '洞庭', '鄱阳']
    has_specific_region = any(r in query for r in specific_regions)
    has_specific_numbers = bool(re.search(r'\d+\s*(天|日|厘米|cm)', query))

    if has_specific_region and has_specific_numbers:
        # 检查证据是否覆盖该区域或为通用
        # 注意：正向覆盖只能来自applicability，limitations中的否定提及不是覆盖
        region_covered = any(r in all_applicability for r in specific_regions)
        is_generic = '通用' in all_applicability + all_tags or 'IRRI通用' in all_applicability
        has_local_standard = any(tag in all_tags for tag in ['江苏标准', '地方标准'])
        if not region_covered and (is_generic or has_local_standard):
            return True, 'insufficient_evidence:regional_prescription'
    elif has_specific_region:
        # 问某地灌溉方案但没有该地资料
        has_prescription_query = any(w in query_lower for w in [
            '灌溉方案', '处方', '标准', '规范', '采用', '适用',
            '降到', '应该', '复灌',
        ])
        if has_prescription_query:
            # 正向覆盖只能来自applicability，limitations中的"未经江西"等否定是明确不适用
            region_covered = any(r in all_applicability for r in specific_regions)
            if not region_covered:
                return True, 'insufficient_evidence:regional_prescription'

    # 2) 索取本项目田间实测结果，但项目只有合成仿真
    field_measure_patterns = ['实测', '田间实测', '田间试验', '本项目.*测', '实验田']
    is_asking_field = any(re.search(p, query) for p in field_measure_patterns)
    has_simulation_only = ('合成仿真' in all_limitations or '合成软件在环' in all_text or
                          '无田间标定' in all_limitations or '非公开行业基准' in all_limitations)
    if is_asking_field and has_simulation_only:
        # 检查是否所有证据都注明是合成/仿真结果
        if '实测' not in all_tags and '田间' not in all_tags:
            return True, 'insufficient_evidence:simulation_not_field'

    # 3) 索取知识库未收录的标准精确条款
    standard_clause_patterns = [
        r'(规定|条款|条文).*具体.*(数值|上限|下限)',
        r'(具体|精确).*(灌水|排水|水层).*(上限|下限|数值|阈值)',
        r'(标准|规范).*具体.*多少',
    ]
    is_asking_exact_clause = any(re.search(p, query) for p in standard_clause_patterns)
    # 检查证据是否直接包含标准数值
    has_exact_clause = bool(re.search(r'(上限|下限|规定).*\d+\s*cm', all_text))
    if is_asking_exact_clause and not has_exact_clause:
        return True, 'insufficient_evidence:standard_clause_not_in_kb'

    # 4) 询问特定生育期精确阈值，但证据只说明生育期影响阈值
    growth_stage_patterns = ['分蘖期', '拔节期', '孕穗期', '抽穗期', '灌浆期', '成熟期',
                            '返青期', '苗期', '育苗期']
    has_growth_stage = any(g in query for g in growth_stage_patterns)
    # 请求语义：具体/精确/多少/阈值等词 + 生育期 → 正在索取精确数值
    request_precision_words = ['具体', '精确', '多少', '阈值', '上限', '下限', '降到多少']
    has_precision_request = any(w in query for w in request_precision_words)
    has_exact_stage_threshold = has_growth_stage and (has_specific_numbers or has_precision_request)
    if has_exact_stage_threshold:
        # 检查证据中是否有该生育期的具体阈值
        stage_in_evidence = any(g in all_text for g in growth_stage_patterns if g in query)
        if not stage_in_evidence:
            return True, 'insufficient_evidence:growth_stage_threshold'

    # 5) 盐碱地等未覆盖工况
    uncovered_conditions = ['盐碱地', '盐碱', '盐渍', '咸水', '海水稻']
    has_uncovered = any(c in query for c in uncovered_conditions)
    if has_uncovered:
        covered_in_evidence = any(c in all_text + all_applicability + all_limitations for c in uncovered_conditions)
        if not covered_in_evidence:
            return True, 'insufficient_evidence:uncovered_condition'

    # 6) 询问本系统"LLM事实正确率"但回答器未调用LLM
    llm_accuracy_patterns = [r'LLM.*(正确率|准确率|事实)', r'(语言模型|大模型).*(正确率|准确率)',
                            r'生成.*(正确率|准确率|质量)']
    is_asking_llm_accuracy = any(re.search(p, query) for p in llm_accuracy_patterns)
    if is_asking_llm_accuracy:
        return True, 'insufficient_evidence:no_llm_in_system'

    return False, ''


def _detect_evidence_conflict(evidence_chunks: list[dict]) -> bool:
    """
    检测将用于回答的证据知识块之间是否存在真实冲突。

    仅审查实际用于回答的候选证据（最多3个）。只有同一测量对象、
    适用条件可比且数值/范围确实不相容时才标记冲突。
    不同测量维度（如复灌触发水位 vs 复灌后目标水层）不视为冲突。

    参数:
        evidence_chunks: 将用于回答的最多3个知识块

    返回:
        如果检测到真实冲突返回True
    """
    if len(evidence_chunks) < 2:
        return False

    # 对每对证据检查同一测量维度上的数值冲突
    for i in range(len(evidence_chunks)):
        for j in range(i + 1, len(evidence_chunks)):
            if _values_conflict_on_same_dimension(evidence_chunks[i], evidence_chunks[j]):
                return True
    return False


def _values_conflict_on_same_dimension(c1: dict, c2: dict) -> bool:
    """
    检查两个知识块是否在同一测量维度上给出不相容的数值。

    仅通过语义文本模式（非宽泛tag）判定维度归属，
    提取数值区间后检查是否不相容（不重叠）。
    互补维度（如触发深度 vs 复灌目标深度）不视为冲突。

    参数:
        c1, c2: 两个知识块

    返回:
        如果值在同一维度上明显冲突返回True
    """
    t1 = c1.get('text', '')
    t2 = c2.get('text', '')

    # 不同来源才可能冲突
    if c1['source_id'] == c2['source_id']:
        return False

    # 定义仅由语义文本模式判定的测量维度（不使用宽泛tag交集）
    dimensions = [
        {
            'key': 'trigger_depth',
            'name': '复灌触发深度',
            'pattern': r'降到.*?(?:土表|表面|田面).*?(?:下|以下|约)\s*(\d+(?:\.\d+)?)\s*cm',
        },
        {
            'key': 'flood_depth',
            'name': '复灌后水层深度',
            'pattern': r'(?:复灌|恢复|补)\D{0,10}?(?:至|到|约)\s*(\d+(?:\.\d+)?)\s*cm\s*(?:水层|深度)?|(?:复灌|恢复|补)\s*(\d+(?:\.\d+)?)\s*[～\-~—–]\s*(\d+(?:\.\d+)?)\s*cm',
        },
        {
            'key': 'water_saving',
            'name': '节水率',
            'pattern': r'(?:可?\s*减少|节省|节约)\D{0,8}(\d+(?:\.\d+)?)\s*%|(\d+(?:\.\d+)?)\s*%\D{0,8}(?:用水|灌溉)',
        },
        {
            'key': 'methane',
            'name': '甲烷减排率',
            'pattern': r'甲烷\D{0,8}(\d+(?:\.\d+)?)\s*%|(\d+(?:\.\d+)?)\s*%\D{0,8}甲烷',
        },
    ]

    for dim in dimensions:
        m1 = re.search(dim['pattern'], t1)
        m2 = re.search(dim['pattern'], t2)
        if not m1 or not m2:
            continue  # 不是同一测量维度

        # 同一维度 → 提取数值区间
        vals1 = _extract_nums_from_text(t1, dim['pattern'])
        vals2 = _extract_nums_from_text(t2, dim['pattern'])

        if not vals1 or not vals2:
            continue  # 无法提取数值，不足以判定冲突

        # 适用条件可比性检查
        app1 = c1.get('applicability', '')
        app2 = c2.get('applicability', '')
        if _applicability_differs(app1, app2):
            continue  # 适用条件不同，不是真冲突

        # 数值区间不重叠检查
        lo1, hi1 = min(vals1), max(vals1)
        lo2, hi2 = min(vals2), max(vals2)
        if hi1 < lo2 or hi2 < lo1:
            return True  # 区间不重叠 → 冲突

    return False


def _extract_nums_from_text(text: str, pattern: str) -> list[float]:
    """
    从文本中按语义模式提取数值列表。

    参数:
        text: 文本
        pattern: 含捕获组的正则模式

    返回:
        提取到的浮点数列表
    """
    nums = []
    for m in re.finditer(pattern, text):
        for g in m.groups():
            if g is not None:
                try:
                    nums.append(float(g))
                except ValueError:
                    pass
    return nums


def _applicability_differs(app1: str, app2: str) -> bool:
    """
    检查两个知识块的适用条件是否明显不同。

    如果一个是通用建议另一个是特定地方标准，
    或者适用范围不重叠，视为适用条件不同。

    参数:
        app1, app2: 适用条件文本

    返回:
        如果适用条件明显不同返回True
    """
    generic_terms = ('通用', 'IRRI通用', 'IRRI')
    specific_terms = ('江苏', '地方标准', 'DB32')

    g1 = any(t in app1 for t in generic_terms)
    g2 = any(t in app2 for t in generic_terms)
    s1 = any(t in app1 for t in specific_terms)
    s2 = any(t in app2 for t in specific_terms)

    # 一个通用一个地方 → 条件不同
    return (g1 and s2) or (g2 and s1)


def _select_evidence(
    query: str,
    results: list[tuple[dict, float]],
    max_evidence: int = 3,
) -> list[dict]:
    """
    从检索结果中选择将用于构建回答的证据。

    过滤规则：
    1. 优先取top最多max_evidence个知识块
    2. 过滤掉文本主题与查询明显无关的尾部弱相关结果
    3. 保留互补证据（如组合问题需要多个知识块的信息）

    参数:
        query: 用户查询
        results: (知识块, 得分) 列表，按得分降序
        max_evidence: 最大证据数量

    返回:
        选中的证据知识块列表
    """
    if not results:
        return []

    # 取top候选
    candidates = results[:max_evidence * 2]  # 取更多候选用于筛选
    query_tokens = set(query)

    selected = []
    selected_titles = set()

    for chunk, score in candidates:
        # 如果已有足够证据，停止
        if len(selected) >= max_evidence:
            break

        title = chunk.get('title', '')
        text = chunk.get('text', '')

        # 避免重复标题
        if title in selected_titles:
            continue

        # 主题相关性过滤：检查查询关键词是否出现在知识块中
        relevance_score = _topic_relevance(query, chunk)
        if relevance_score == 0 and len(selected) > 0:
            # 尾部完全不相关的跳过（但允许第一个候选即使弱相关也保留）
            continue

        selected.append(chunk)
        selected_titles.add(title)

    return selected[:max_evidence]


def _topic_relevance(query: str, chunk: dict) -> int:
    """
    计算查询与知识块的主题相关性得分。

    基于查询中非停用词在知识块的text/title/tags中出现的数量。

    参数:
        query: 用户查询
        chunk: 知识块

    返回:
        相关性得分（命中关键词数）
    """
    text = chunk.get('text', '') + ' ' + chunk.get('title', '')
    tags = ' '.join(chunk.get('tags', []))

    # 简单实现：计算查询中的中文词在知识块中的出现
    query_words = _extract_query_keywords(query)
    score = 0
    for w in query_words:
        if w in text or w in tags:
            score += 1
    return score


def _extract_query_keywords(query: str) -> list[str]:
    """
    从查询中提取有意义的关键词（中文2-4字词）。

    参数:
        query: 用户查询

    返回:
        关键词列表
    """
    # 移除非中文字符，提取中文词片段
    chinese_chars = re.findall(r'[一-鿿]+', query)
    keywords = []
    for segment in chinese_chars:
        if len(segment) >= 2:
            keywords.append(segment)
            # 2-gram
            for i in range(len(segment) - 1):
                keywords.append(segment[i:i + 2])
    return list(set(keywords))


def generate_answer(
    query: str,
    results: list[tuple[dict, float]],
    threshold: float = 0.01,
) -> dict:
    """
    基于检索结果生成回答。

    回答策略：
    1. 首先检测是否为直接控制请求 → 拒绝执行
    2. 检查检索得分是否低于阈值 → 拒答
    3. 选择将用于回答的证据（最多3个）
    4. 检查是否存在地域外推风险 → 拒答
    5. 检查证据是否充足 → 拒答（新增多场景检测）
    6. 检查证据是否冲突 → 拒答（仅审查选中的top证据）
    7. 域内且证据充分 → 返回证据摘要和引用

    参数:
        query: 用户查询文本
        results: (知识块, 得分) 列表，按得分降序
        threshold: 最低检索得分阈值，低于此值拒答

    返回:
        回答字典，包含：
        - abstain: 是否拒答（bool）
        - answer: 回答文本（str）
        - citations: 引用列表（list[str]），格式为 "source_id#chunk_id"
        - abstain_reason: 拒答原因（仅拒答时，str）
        - evidence: 使用的证据知识块（list[dict]）
    """
    # 优先级1：检测直接控制请求
    if is_control_request(query):
        return {
            'abstain': True,
            'answer': '抱歉，本系统不直接下发泵阀控制指令。请根据当前规则库、MPC策略和设备状态通过人工确认后执行操作。建议：(1) 检查当前灌溉规则和自动化策略；(2) 确认MPC模型输出的建议控制量；(3) 通过设备管理界面人工确认后下发指令。',
            'citations': [],
            'abstain_reason': 'control_request',
            'evidence': [],
        }

    # 过滤低于阈值的检索结果
    valid_results = [(chunk, score) for chunk, score in results if score >= threshold]

    # 优先级2：无足够证据时拒答
    if not valid_results:
        return {
            'abstain': True,
            'answer': '抱歉，根据当前知识库的内容，无法提供该问题的可靠答案。建议：(1) 查阅当地农业技术推广部门的最新灌溉标准；(2) 咨询水稻栽培专家获取针对性的田间管理建议。',
            'citations': [],
            'abstain_reason': 'below_threshold',
            'evidence': [],
        }

    # 优先级3：选择将用于回答的证据
    evidence_chunks = _select_evidence(query, valid_results, max_evidence=3)

    # 如果没有选出任何证据
    if not evidence_chunks:
        return {
            'abstain': True,
            'answer': '抱歉，检索到的知识块与您的问题关联不足，无法提供可靠的答案。',
            'citations': [],
            'abstain_reason': 'below_threshold',
            'evidence': [],
        }

    # 优先级4：检测地域外推风险
    if _detect_extrapolation_risk(query):
        return {
            'abstain': True,
            'answer': '抱歉，您的问题涉及将特定地区（如江苏）的灌溉标准应用到其他地区（如江西），这属于不当的地域外推。各地区的灌溉标准基于本地气候、土壤和耕作制度制定，不可直接套用。建议查阅目标地区的本地灌溉技术标准。',
            'citations': [],
            'abstain_reason': 'regional_extrapolation',
            'evidence': [],
        }

    # 优先级5：检测证据是否充足
    insufficient, reason = _detect_insufficient_evidence(query, evidence_chunks)
    if insufficient:
        reason_msgs = {
            'insufficient_evidence:regional_prescription': '抱歉，您询问的特定区域田间处方需要该地区的本地试验数据。当前知识库仅包含通用或异地资料，无法提供针对该区域的精确灌溉参数。建议查阅当地的农业技术推广部门或地方标准。',
            'insufficient_evidence:simulation_not_field': '抱歉，当前知识库中的实验数据均来自合成软件在环仿真，不包含田间实测结果。仿真结果不能直接等同于田间性能。如需田间实测数据，请等待后续田间验证实验完成。',
            'insufficient_evidence:standard_clause_not_in_kb': '抱歉，当前知识库未收录该标准的完整条文和具体数值条款。建议直接查阅标准原文或联系标准发布机构获取精确参数。',
            'insufficient_evidence:growth_stage_threshold': '抱歉，当前知识库仅说明生育期会影响灌溉阈值，但未提供该特定生育期的精确数值。不同品种和地区的具体阈值需基于本地试验确定。',
            'insufficient_evidence:uncovered_condition': '抱歉，当前知识库未覆盖您所询问的特殊工况（如盐碱地等），无法提供可靠的灌溉建议。建议咨询相关领域的农业专家。',
            'insufficient_evidence:no_llm_in_system': '抱歉，当前RAG系统使用确定性template/extractive方式生成回答，不调用任何LLM（语言模型）。因此不存在"LLM事实正确率"这一指标。本系统的评估指标为检索召回率、拒答准确率等可单测的检索与行为指标。',
        }
        return {
            'abstain': True,
            'answer': reason_msgs.get(reason, '抱歉，当前证据不足以提供可靠回答。'),
            'citations': [],
            'abstain_reason': reason,
            'evidence': evidence_chunks,
        }

    # 优先级6：检测证据冲突（仅审查选中的证据）
    if _detect_evidence_conflict(evidence_chunks):
        return {
            'abstain': True,
            'answer': '抱歉，检索到的知识块之间存在真实的不一致信息（同一测量维度上数值不相容），无法确定可靠的答案。建议查阅原始来源并咨询领域专家以核实相关数据。',
            'citations': [],
            'abstain_reason': 'evidence_conflict',
            'evidence': evidence_chunks,
        }

    # 优先级7：域内回答 — 使用选中的证据构建摘要
    citations = [f"{c['source_id']}#{c['chunk_id']}" for c in evidence_chunks]

    # 构建简短证据摘要：拼接evidence的title和text首句
    evidence_parts = []
    for c in evidence_chunks:
        first_sentence = c['text'].split('。')[0] + '。'
        evidence_parts.append(f"【{c['title']}】{first_sentence}")

    answer = (
        f"根据知识库检索结果：\n\n"
        + '\n'.join(evidence_parts)
        + f"\n\n请注意：以上信息来源于检索到的知识块，具体适用性需结合实地条件判断。"
    )

    return {
        'abstain': False,
        'answer': answer,
        'citations': citations,
        'abstain_reason': None,
        'evidence': evidence_chunks,
    }
