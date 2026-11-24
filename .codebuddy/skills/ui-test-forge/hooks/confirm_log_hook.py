# -*- coding: utf-8 -*-
"""UI 清单确认口令落盘 hook（UserPromptSubmit 触发，永不阻断）。

职责：
    用户回复口令「确认，按照清单继续」时，把
    `时间戳 \\t 当前清单指纹 \\t 用户原话` 追加到 `ui_test_dwp_temp/user_prompts.log`，
    供 check_ui_gate.py 的 G3 机械校验。

为什么由 hook 落盘：AI 可以把清单里的 `确认状态` 写成「已确认」，
但无法代替用户发出这条口令；口令落盘时带清单指纹，清单之后被改动则确认自动失效。
"""

import json
import os
import sys

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass

HOOK_DIR = os.path.dirname(os.path.abspath(__file__))
SKILL_ROOT = os.path.dirname(HOOK_DIR)
TOOLS_DIR = os.path.join(SKILL_ROOT, "tools")
for _path in (SKILL_ROOT, TOOLS_DIR):
    if _path not in sys.path:
        sys.path.insert(0, _path)

import confirm_protocol  # noqa: E402

DEFAULT_CHECKLIST_REL = "ui_test_dwp_temp/design_checklist.md"


def _load_config():
    path = os.path.join(SKILL_ROOT, "config.json")
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def _resolve_settings(payload):
    config = _load_config()
    paths_cfg = config.get("paths") or {}
    case_design = config.get("case_design") or {}

    project_root = os.environ.get("CODEBUDDY_PROJECT_DIR", "").strip()
    if not project_root or not os.path.isdir(project_root):
        configured = str(config.get("project_root") or "").strip()
        project_root = (
            configured
            if configured and os.path.isdir(configured)
            else (payload.get("cwd") or os.getcwd())
        )
    project_root = os.path.normpath(project_root)

    runtime_dir = paths_cfg.get("runtime_temp_dir") or confirm_protocol.DEFAULT_RUNTIME_DIR
    checklist_rel = case_design.get("checklist_output") or DEFAULT_CHECKLIST_REL
    checklist_path = checklist_rel
    if not os.path.isabs(checklist_path):
        checklist_path = os.path.join(project_root, checklist_path)
    return project_root, runtime_dir, os.path.normpath(checklist_path)


def _extract_prompt(payload):
    for key in ("prompt", "user_prompt", "message"):
        value = payload.get(key)
        if isinstance(value, str) and value.strip():
            return value
    return ""


def main():
    try:
        raw = sys.stdin.read()
    except Exception:
        return 0
    if not raw or not raw.strip():
        return 0
    try:
        payload = json.loads(raw)
    except Exception:
        return 0
    if not isinstance(payload, dict):
        return 0

    prompt = _extract_prompt(payload)
    if not confirm_protocol.is_confirm_prompt(prompt):
        return 0

    project_root, runtime_dir, checklist_path = _resolve_settings(payload)
    try:
        with open(checklist_path, "r", encoding="utf-8") as handle:
            checklist_text = handle.read()
    except Exception as exc:
        sys.stdout.write(f"[confirm_log] 命中口令，但读取清单失败({exc})，未记录。\n")
        return 0

    try:
        log_path = confirm_protocol.append_confirm(
            project_root, prompt, checklist_text, runtime_dir
        )
    except Exception as exc:
        sys.stdout.write(f"[confirm_log] 写入确认日志失败({exc})，未记录。\n")
        return 0

    sys.stdout.write(
        "[confirm_log] 已记录 UI 清单确认口令，指纹 = "
        f"{confirm_protocol.checklist_fingerprint(checklist_text)[:12]}…，日志 = {log_path}\n"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
