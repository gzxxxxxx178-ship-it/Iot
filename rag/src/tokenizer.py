"""
中文bigram与英文/数字词项分词器。
使用Python 3标准库实现，不依赖第三方NLP库。
"""

import re


def tokenize(text: str) -> list[str]:
    """
    对输入文本进行分词。

    中文部分：生成连续字符bigram（相邻两个字符为一组），同时保留单字作为unigram。
    英文/数字部分：按空白和标点分割为词项，统一转为小写。
    混合文本：先按脚本类型分段，分别处理。

    参数:
        text: 待分词的原始文本字符串

    返回:
        分词后的token列表，中文bigram在前，unigram在后，英文词项在对应位置
    """
    if not text:
        return []

    tokens = []
    segments = _segment_by_script(text)

    for seg, is_cjk in segments:
        if not seg:
            continue
        if is_cjk:
            chars = list(seg)
            # 添加bigram
            for i in range(len(chars) - 1):
                tokens.append(chars[i] + chars[i + 1])
            # 同时保留单字（unigram），确保单字查询也能匹配
            tokens.extend(chars)
        else:
            # 英文/数字词项：匹配字母和数字序列
            words = re.findall(r'[a-zA-Z0-9]+', seg.lower())
            tokens.extend(words)

    return tokens


def _segment_by_script(text: str) -> list[tuple[str, bool]]:
    """
    将文本按中文字符和非中文字符分段。

    参数:
        text: 原始文本

    返回:
        (片段, 是否为CJK) 的列表
    """
    segments = []
    current = []
    current_is_cjk = None

    for ch in text:
        is_cjk = _is_cjk(ch)
        if current_is_cjk is None:
            current_is_cjk = is_cjk
        elif is_cjk != current_is_cjk:
            segments.append((''.join(current), current_is_cjk))
            current = []
            current_is_cjk = is_cjk
        current.append(ch)

    if current:
        segments.append((''.join(current), current_is_cjk))

    return segments


def _is_cjk(ch: str) -> bool:
    """
    判断字符是否为CJK（中日韩）统一表意文字或中文标点。

    覆盖范围：
    - CJK统一表意文字：U+4E00–U+9FFF
    - CJK扩展A：U+3400–U+4DBF
    - CJK兼容表意文字：U+F900–U+FAFF
    - 全角标点（中文引号、逗号等）：U+3000–U+303F, U+FF00–U+FFEF

    参数:
        ch: 单个字符

    返回:
        是否为CJK相关字符
    """
    cp = ord(ch)
    return (0x4E00 <= cp <= 0x9FFF or
            0x3400 <= cp <= 0x4DBF or
            0xF900 <= cp <= 0xFAFF or
            0x3000 <= cp <= 0x303F or
            0xFF00 <= cp <= 0xFFEF)
