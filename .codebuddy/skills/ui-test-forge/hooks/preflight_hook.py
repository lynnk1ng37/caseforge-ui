# -*- coding: utf-8 -*-
"""ui-test-forge 入口上下文注入 hook（UserPromptSubmit / PreToolUse(Skill)）。

只注入上下文，永不阻断（退出码恒为 0）。
作用：用户提到 UI 自动化相关关键词时，把工作流骨架与铁律放进上下文，
避免 AI 只凭记忆直接写 Playwright 代码。
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

SKILL_NAME = "ui-test-forge"
PROMPT_HINTS = (
    "ui-test-forge",
    "ui自动化",
    "ui 自动化",
    "playwright",
    "e2e",
    "页面元素",
    "定位器",
    "locator",
)

CONTEXT = """[ui-test-forge] 检测到 UI 自动化相关任务，按以下顺序执行（不要跳步）：
1. 读 .codebuddy/skills/ui-test-forge/SKILL.md，确认任务类型（转写/维护/断言加固）
2. 读 doc/team/README.md 的铁律与分层规范
3. 落盘 ui_test_dwp_temp/design_checklist.md（含页面落点/定位器/场景四维/待确认）
4. 停下等用户回复唯一口令：确认，按照清单继续（其它回复一律不算确认）
5. 先写 src/test/resources/locators/*.yaml，再写 pages，再写 steps / features；steps 层禁止 page.locator / Thread.sleep / 硬编码 URL
6. 运行 mvn test，失败按「环境/登录态/定位断链/数据前置/断言不符」归因，3 轮上限、第 2 轮必须换策略
7. 回写 docs/domain 与 locators/*.yaml 的 verified_at
禁止：未确认清单就写代码；删断言/放宽断言让用例变绿；运行期引入 LLM 定位。"""


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

    tool_name = str(payload.get("tool_name") or "")
    prompt = _extract_prompt(payload)
    lowered = prompt.lower()

    hit = any(hint in lowered for hint in PROMPT_HINTS)
    if tool_name == "Skill":
        args = json.dumps(payload.get("tool_input") or {}, ensure_ascii=False).lower()
        hit = hit or SKILL_NAME in args

    if not hit:
        return 0

    sys.stdout.write(CONTEXT + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
