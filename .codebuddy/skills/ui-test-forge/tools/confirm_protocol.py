# -*- coding: utf-8 -*-
"""UI 用例清单确认协议：口令 + 清单指纹 + 机械校验。

与其他 skill 的同名模块保持同一套协议，仅把运行时目录换成
`ui_test_dwp_temp`，避免两套 skill 共用一份清单互相放行。

协议：
1. 只有用户回复「确认，按照清单继续」才算确认；
2. UserPromptSubmit hook 命中口令时，把 `时间戳 \\t 清单指纹 \\t 用户原话`
   追加到 `<project_root>/ui_test_dwp_temp/user_prompts.log`；
3. 门禁要求 `确认状态 = 已确认` 且日志中存在与当前清单指纹一致的记录；
4. 指纹只覆盖「## 用户确认记录」之前的内容，且忽略表格末尾状态列，
   因此回填实现状态不会让确认失效，而任何场景增删改都会让确认作废。
"""

import hashlib
import os
import re
from datetime import datetime
from typing import List, Optional, Tuple

CONFIRM_PHRASE = "确认，按照清单继续"
CONFIRM_SECTION = "## 用户确认记录"
LOG_FILENAME = "user_prompts.log"
DEFAULT_RUNTIME_DIR = "ui_test_dwp_temp"

STATUS_TOKENS = frozenset({"待确认", "已确认", "已实现", "已取消", "待实现"})

_PUNCT_PATTERN = re.compile(
    r"[\s，,。.、；;：:！!？?“”\"'（）()【】\[\]《》<>~`\-—]+"
)


def _strip_status_column(line: str) -> str:
    """去掉表格行末尾的状态列，避免状态流转改变清单指纹。"""
    stripped = line.strip()
    if not stripped.startswith("|"):
        return stripped
    cells = [cell.strip() for cell in stripped.strip("|").split("|")]
    if len(cells) >= 2 and cells[-1] in STATUS_TOKENS:
        cells = cells[:-1]
    return "| " + " | ".join(cells) + " |"


def normalize_prompt(text: str) -> str:
    return _PUNCT_PATTERN.sub("", text or "")


def is_confirm_prompt(text: str) -> bool:
    """口令必须**独立成行**，同一行带其它内容不算；允许前后有其它说明行。

    为什么按行而不是按整条消息：
        最初的实现要求整条消息归一化后严格相等，结果「口令 + 一句补充说明」
        这种最自然的回复会被判成无效，用户连发两次都没落盘。
        口令的防伪性质来自「只有用户能发出这条消息」，与是否附带上下文无关；
        但必须防止「继续」「写吧」被当成确认，所以仍要求口令独立成行。
    """
    target = normalize_prompt(CONFIRM_PHRASE)
    if not target:
        return False
    for line in (text or "").splitlines():
        if line.strip() and normalize_prompt(line) == target:
            return True
    return False


def checklist_fingerprint(checklist_text: str) -> str:
    head = (checklist_text or "").split(CONFIRM_SECTION)[0]
    normalized = "\n".join(
        _strip_status_column(line) for line in head.strip().splitlines()
    )
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def resolve_log_path(project_root: str, runtime_dir: str = DEFAULT_RUNTIME_DIR) -> str:
    return os.path.join(project_root, runtime_dir, LOG_FILENAME)


def append_confirm(
    project_root: str,
    prompt: str,
    checklist_text: str,
    runtime_dir: str = DEFAULT_RUNTIME_DIR,
) -> str:
    path = resolve_log_path(project_root, runtime_dir)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    stamp = datetime.now().astimezone().isoformat(timespec="seconds")
    safe_prompt = (prompt or "").replace("\t", " ").replace("\n", " ")
    line = f"{stamp}\t{checklist_fingerprint(checklist_text)}\t{safe_prompt}\n"
    with open(path, "a", encoding="utf-8") as handle:
        handle.write(line)
    return path


def read_confirms(
    project_root: str, runtime_dir: str = DEFAULT_RUNTIME_DIR
) -> List[Tuple[str, str, str]]:
    path = resolve_log_path(project_root, runtime_dir)
    if not os.path.isfile(path):
        return []
    records: List[Tuple[str, str, str]] = []
    with open(path, "r", encoding="utf-8") as handle:
        for raw in handle:
            parts = raw.rstrip("\n").split("\t")
            if len(parts) >= 2:
                records.append(
                    (parts[0], parts[1], parts[2] if len(parts) > 2 else "")
                )
    return records


def find_valid_confirm(
    project_root: str,
    checklist_text: str,
    runtime_dir: str = DEFAULT_RUNTIME_DIR,
) -> Optional[Tuple[str, str]]:
    fingerprint = checklist_fingerprint(checklist_text)
    for stamp, recorded_fp, prompt in reversed(read_confirms(project_root, runtime_dir)):
        if recorded_fp == fingerprint:
            return stamp, prompt
    return None
