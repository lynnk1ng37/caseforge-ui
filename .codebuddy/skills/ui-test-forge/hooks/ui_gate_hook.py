# -*- coding: utf-8 -*-
"""ui-test-forge 写入门禁 hook（PreToolUse: Write/Edit）。

与 confirm_log_hook / preflight_hook 的分工：
- preflight_hook：UserPromptSubmit 触发，只注入上下文，永不阻断；
- confirm_log_hook：UserPromptSubmit 触发，记录确认口令，永不阻断；
- 本 hook：PreToolUse(Write|Edit) 触发，**会阻断**违规写入（退出码 2）。

Hook I/O 协议（CodeBuddy）：
  stdin  : {hook_event_name, session_id, cwd, tool_name, tool_input, ...}
  stdout : 退出码 0 时可输出 additionalContext 注入上下文
  stderr : 退出码 2 时内容回传 AI，作为拦截原因

设计原则：fail-open。任何自身异常都放行。
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

WRITE_TOOL_NAMES = (
    "Write",
    "write_to_file",
    "Edit",
    "replace_in_file",
    "MultiEdit",
    "multi_edit",
)

_SUSPECT_WRITE_HINTS = (
    "write",
    "edit",
    "patch",
    "apply",
    "create",
    "save",
    "insert",
    "append",
    "replace",
    "modify",
    "update",
)


def _tool_target_path(tool_input):
    for key in ("filePath", "file_path", "path", "target_file", "targetFile"):
        value = tool_input.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def _tool_new_content(tool_input):
    for key in ("content", "new_str", "new_string", "new_content", "text"):
        value = tool_input.get(key)
        if isinstance(value, str):
            return value
    return ""


def _resolve_project_root(payload_cwd):
    env_root = os.environ.get("CODEBUDDY_PROJECT_DIR", "").strip()
    if env_root and os.path.isdir(env_root):
        return os.path.normpath(env_root)

    config_path = os.path.join(SKILL_ROOT, "config.json")
    try:
        if os.path.isfile(config_path):
            with open(config_path, "r", encoding="utf-8") as handle:
                data = json.load(handle)
            configured = str(data.get("project_root") or "").strip()
            if configured and os.path.isdir(configured):
                return os.path.normpath(configured)
    except Exception:
        pass

    return os.path.normpath(payload_cwd or os.getcwd())


def _emit_diag(message):
    sys.stdout.write(f"[ui_gate] {message}\n")


def _looks_like_write_tool(tool_name):
    lowered = str(tool_name or "").lower()
    if not lowered:
        return False
    return any(hint in lowered for hint in _SUSPECT_WRITE_HINTS)


def main():
    try:
        raw = sys.stdin.read()
    except Exception:
        return 0
    if not raw or not raw.strip():
        return 0

    try:
        payload = json.loads(raw)
    except Exception as exc:
        _emit_diag(f"payload 不是合法 JSON（{exc}），门禁本次未生效，已放行。")
        return 0
    if not isinstance(payload, dict):
        _emit_diag("payload 顶层不是对象，门禁本次未生效，已放行。")
        return 0

    tool_name = str(payload.get("tool_name") or "").strip()
    if tool_name not in WRITE_TOOL_NAMES:
        if _looks_like_write_tool(tool_name):
            _emit_diag(
                f"疑似写工具 `{tool_name}` 不在白名单中，本次跳过检查。"
                "请更新 hooks/ui_gate_hook.py 的 WRITE_TOOL_NAMES。"
            )
        return 0

    tool_input = payload.get("tool_input") or {}
    if not isinstance(tool_input, dict):
        _emit_diag("tool_input 不是对象，门禁本次未生效，已放行。")
        return 0

    target_path = _tool_target_path(tool_input)
    if not target_path:
        _emit_diag("入参中取不到文件路径，本次跳过检查。")
        return 0

    if not os.path.isabs(target_path):
        target_path = os.path.join(payload.get("cwd") or os.getcwd(), target_path)
    target_path = os.path.normpath(target_path)

    project_root = _resolve_project_root(payload.get("cwd"))

    try:
        import check_ui_gate

        ok, messages = check_ui_gate.evaluate(
            target_path=target_path,
            new_content=_tool_new_content(tool_input),
            project_root=project_root,
        )
    except Exception as exc:
        _emit_diag(f"门禁检查异常，已放行: {exc}")
        return 0

    if ok:
        return 0

    sys.stderr.write(check_ui_gate.format_block_message(messages, target_path) + "\n")
    return 2


if __name__ == "__main__":
    sys.exit(main())
