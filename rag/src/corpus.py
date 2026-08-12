"""
语料库加载与元数据校验模块。
使用Python 3标准库，负责加载和验证sources.json、chunks.jsonl、eval_questions.jsonl。
"""

import json
import os


def load_sources(path: str) -> list[dict]:
    """
    加载来源注册表（sources.json）。

    参数:
        path: sources.json文件路径

    返回:
        来源字典列表，每个字典包含source_id、title、institution、url等字段

    异常:
        ValueError: 缺少必填字段或source_id重复
    """
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError("sources.json must contain a JSON array")
    _validate_sources(data)
    return data


def _validate_sources(sources: list[dict]) -> None:
    """
    验证来源注册表的完整性和唯一性。

    参数:
        sources: 来源字典列表

    异常:
        ValueError: 缺少必填字段或source_id重复
    """
    required_fields = [
        'source_id', 'title', 'institution', 'url',
        'access_date', 'scope', 'limitations',
    ]
    seen_ids = set()
    for src in sources:
        for field in required_fields:
            if field not in src:
                raise ValueError(
                    f"Source {src.get('source_id', '?')} missing required field: {field}"
                )
        if src['source_id'] in seen_ids:
            raise ValueError(f"Duplicate source_id: {src['source_id']}")
        seen_ids.add(src['source_id'])


def load_chunks(path: str) -> list[dict]:
    """
    加载知识块JSONL文件（每行一个JSON对象）。

    参数:
        path: chunks.jsonl文件路径

    返回:
        知识块字典列表，每个字典包含chunk_id、source_id、title、text等字段

    异常:
        ValueError: JSON解析错误、缺少必填字段、chunk_id重复、tags不是列表
    """
    chunks = []
    with open(path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                chunk = json.loads(line)
            except json.JSONDecodeError as e:
                raise ValueError(f"Line {line_num}: invalid JSON: {e}")
            chunks.append(chunk)

    _validate_chunks(chunks)
    return chunks


def _validate_chunks(chunks: list[dict]) -> None:
    """
    验证知识块的结构完整性和一致性。

    检查项：必填字段、chunk_id唯一性、tags类型、source_id存在性。

    参数:
        chunks: 知识块字典列表

    异常:
        ValueError: 结构不符合要求
    """
    required_fields = [
        'chunk_id', 'source_id', 'title', 'text',
        'tags', 'applicability', 'limitations',
    ]
    seen_ids = set()
    for chunk in chunks:
        for field in required_fields:
            if field not in chunk:
                raise ValueError(
                    f"Chunk {chunk.get('chunk_id', '?')} missing required field: {field}"
                )
        if chunk['chunk_id'] in seen_ids:
            raise ValueError(f"Duplicate chunk_id: {chunk['chunk_id']}")
        seen_ids.add(chunk['chunk_id'])
        if not isinstance(chunk['tags'], list):
            raise ValueError(
                f"Chunk {chunk['chunk_id']}: 'tags' must be a list, got {type(chunk['tags']).__name__}"
            )


def load_questions(path: str) -> list[dict]:
    """
    加载评估问题JSONL文件（每行一个JSON对象）。

    参数:
        path: eval_questions.jsonl文件路径

    返回:
        问题字典列表，每个包含question_id、question、split、should_abstain等字段

    异常:
        ValueError: JSON解析错误、缺少必填字段、question_id重复、split值无效
    """
    questions = []
    with open(path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                q = json.loads(line)
            except json.JSONDecodeError as e:
                raise ValueError(f"Line {line_num}: invalid JSON: {e}")
            questions.append(q)

    _validate_questions(questions)
    return questions


def _validate_questions(questions: list[dict]) -> None:
    """
    验证评估问题的结构完整性和一致性。

    检查项：必填字段、question_id唯一性、split值合法性、
    in-domain问题必须有gold_chunk_ids、should_abstain类型正确。

    参数:
        questions: 问题字典列表

    异常:
        ValueError: 结构不符合要求
    """
    required_fields = ['question_id', 'question', 'split', 'should_abstain']
    seen_ids = set()
    for q in questions:
        for field in required_fields:
            if field not in q:
                raise ValueError(
                    f"Question {q.get('question_id', '?')} missing required field: {field}"
                )
        if q['question_id'] in seen_ids:
            raise ValueError(f"Duplicate question_id: {q['question_id']}")
        seen_ids.add(q['question_id'])
        if q['split'] not in ('validation', 'test'):
            raise ValueError(
                f"Question {q['question_id']}: split must be 'validation' or 'test', got '{q['split']}'"
            )
        if not isinstance(q['should_abstain'], bool):
            raise ValueError(
                f"Question {q['question_id']}: should_abstain must be boolean, got {type(q['should_abstain']).__name__}"
            )
        if not q['should_abstain'] and 'gold_chunk_ids' not in q:
            raise ValueError(
                f"Question {q['question_id']}: in-domain question must have 'gold_chunk_ids'"
            )


def validate_corpus_consistency(
    sources: list[dict],
    chunks: list[dict],
) -> list[str]:
    """
    校验knowledge chunks中引用的source_id是否都在sources注册表中存在。

    参数:
        sources: 来源字典列表
        chunks: 知识块字典列表

    返回:
        警告信息列表，如果全部一致则返回空列表
    """
    warnings = []
    source_ids = {s['source_id'] for s in sources}

    for chunk in chunks:
        if chunk['source_id'] not in source_ids:
            warnings.append(
                f"Chunk {chunk['chunk_id']} references unknown source_id: {chunk['source_id']}"
            )

    return warnings
