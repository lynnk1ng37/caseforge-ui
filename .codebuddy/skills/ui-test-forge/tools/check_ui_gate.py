# -*- coding: utf-8 -*-
"""ui-test-forge 机械门禁（物理拦截层）。

职责边界：
- 只做「能客观判定」的检查，不做主观评审；
- 任何自身异常一律放行（fail-open），门禁不能成为开发阻塞源；
- 不修改任何业务文件。

检查项：
  G1  目标文件不在 steps / pages / flows / locators 目录内 → 放行
  G2  写用例代码前必须存在 ui_test_dwp_temp/design_checklist.md
  G3  清单 `确认状态=已确认` 且确认日志存在与当前指纹匹配的口令记录
  G4  四类场景占比达标（场景数 >= case_design.min_scenarios_for_ratio 时校验）
  G5  清单不得遗留【待确认】
  G6  steps 层禁裸定位 / 禁自行解析定位器 / 禁固定等待 / 禁硬编码 URL（读 ui_gate_rules.json）
  G7  pages·flows 层禁业务断言、禁内联选择器、禁固定等待、禁硬编码 URL
  G8  每个 @Given/@When/@Then/@And 方法至少一个断言（assertThat）
  G9  定位器文件禁止裸 XPath（需 `# allowed-xpath: 理由` 豁免）
  G10 不同 id 的 primary 选择器复合键重复（需 `# allowed-dup: 理由` 豁免）

用法：
  python tools/check_ui_gate.py --target src/test/java/com/lili/ui/steps/XxxSteps.java --content-file /tmp/new.java
  python tools/check_ui_gate.py --selftest
"""

import argparse
import json
import os
import re
import sys
from typing import Any, Dict, List, Optional, Tuple

HERE = os.path.dirname(os.path.abspath(__file__))
SKILL_ROOT = os.path.dirname(HERE)
if HERE not in sys.path:
    sys.path.insert(0, HERE)

import confirm_protocol  # noqa: E402

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass

DEFAULT_DIMENSIONS = {
    "positive_max_ratio": 0.5,
    "negative_min_ratio": 0.2,
    "boundary_min_ratio": 0.15,
    "exception_min_ratio": 0.1,
}

DIMENSION_RULES = {
    "正向": ("positive_max_ratio", "max"),
    "负向": ("negative_min_ratio", "min"),
    "边界": ("boundary_min_ratio", "min"),
    "异常": ("exception_min_ratio", "min"),
}

CONFIRM_FIELD = "确认状态"
CONFIRMED_VALUES = ("已确认",)
PENDING_MARK = "【待确认】"
DUP_EXEMPT_MARK = "allowed-dup:"

_TEST_FUNC = re.compile(r"^(\s*)def\s+(test_\w+)\s*\(", re.MULTILINE)
_CONFIRM_FIELD_PATTERN = re.compile(
    r"^\|\s*" + CONFIRM_FIELD + r"\s*\|\s*(?P<value>[^|]*?)\s*\|"
)


def _to_posix(path: str) -> str:
    return str(path).replace("\\", "/")


def load_json(path: str) -> Dict[str, Any]:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def load_config() -> Dict[str, Any]:
    return load_json(os.path.join(SKILL_ROOT, "config.json"))


def load_rules() -> Dict[str, Any]:
    return load_json(os.path.join(SKILL_ROOT, "ui_gate_rules.json"))


def resolve_project_root(config: Dict[str, Any]) -> str:
    env_root = os.environ.get("CODEBUDDY_PROJECT_DIR", "").strip()
    if env_root and os.path.isdir(env_root):
        return os.path.normpath(env_root)
    configured = str(config.get("project_root") or "").strip()
    if configured and os.path.isdir(configured):
        return os.path.normpath(configured)
    return os.getcwd()


def _abs(project_root: str, path: str) -> str:
    path = os.path.expanduser(str(path))
    if not os.path.isabs(path):
        path = os.path.join(project_root, path)
    return os.path.normpath(path)


def _glob_to_regex(pattern: str) -> re.Pattern:
    """支持 ** / * / ? 的简单 glob，避免依赖 Python 版本差异。"""
    out = ["^"]
    i = 0
    while i < len(pattern):
        char = pattern[i]
        if char == "*":
            if pattern[i : i + 2] == "**":
                nxt = pattern[i + 2 : i + 3]
                out.append("(.*/)?" if nxt == "/" else ".*")
                i += 3 if nxt == "/" else 2
                continue
            out.append("[^/]*")
        elif char == "?":
            out.append("[^/]")
        else:
            out.append(re.escape(char))
        i += 1
    out.append("$")
    return re.compile("".join(out))


def _match_scope(rel_path: str, rules: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    for scope in rules.get("scopes") or []:
        for pattern in scope.get("globs") or []:
            if _glob_to_regex(pattern).match(rel_path):
                return scope
    return None


def _is_import_line(line: str) -> bool:
    stripped = line.strip()
    return stripped.startswith(("import ", "from ")) or "import " in stripped[:12]


def find_rule_violations(
    content: str, scope: Dict[str, Any]
) -> List[str]:
    violations: List[str] = []
    ignore_imports = bool((scope.get("exemptions") or {}).get("ignoreImportLines"))
    # 定位器层按整文件检查（YAML 无 import 概念）
    for rule in scope.get("forbidden") or []:
        pattern = rule.get("pattern") or ""
        reason = rule.get("reason") or ""
        try:
            regex = re.compile(pattern, re.MULTILINE)
        except re.error:
            continue
        for match in regex.finditer(content):
            line_no = content[: match.start()].count("\n") + 1
            line = content.splitlines()[line_no - 1] if content.splitlines() else ""
            if ignore_imports and _is_import_line(line):
                continue
            if "allowed-xpath:" in line:
                continue
            violations.append(f"第 {line_no} 行命中禁止规则（{reason}）：{line.strip()[:110]}")
    return violations


def _entry_spans(content: str) -> List[Tuple[int, int]]:
    """按顶层 `- id:` 行切出每个条目的原始文本区间（含注释，0-based 闭区间）。

    豁免注释 `# allowed-dup:` 不在 yaml 解析结果里，必须回看原始行。
    """
    lines = (content or "").splitlines()
    starts = [i for i, line in enumerate(lines) if re.match(r"^\s*- id:", line)]
    spans: List[Tuple[int, int]] = []
    for idx, start in enumerate(starts):
        end = starts[idx + 1] - 1 if idx + 1 < len(starts) else len(lines) - 1
        spans.append((start, end))
    return spans


def _selector_key(candidate: Any) -> Optional[Tuple[Tuple, str]]:
    """归一化 primary 选择器为复合键 (key, 展示文本)。

    复合键 = (by, value 或 role+name, text 过滤, nth, exact)，
    同一 value 但带不同 text/nth 的定位器指向不同元素，不算重复。
    无法识别的 by 返回 None（不参与判重）。
    """
    if not isinstance(candidate, dict):
        return None
    by = str(candidate.get("by") or "").strip()
    if by == "role":
        role = str(candidate.get("role") or "").strip()
        name = str(candidate.get("name") or "").strip()
        base: Tuple = ("role", role, name)
        display = f"role:{role}" + (f"[name={name}]" if name else "")
    elif by in ("label", "placeholder", "testid", "text", "css", "xpath"):
        value = re.sub(r"\s+", " ", str(candidate.get("value") or "").strip())
        base = (by, value)
        display = f"{by}:{value}"
    else:
        return None
    text = str(candidate.get("text") or "").strip()
    nth = candidate.get("nth")
    try:
        nth_value = int(nth) if nth is not None else None
    except (TypeError, ValueError):
        nth_value = None
    exact = bool(candidate.get("exact", True)) if by == "text" else None
    parts = [display]
    if text:
        parts.append(f"text={text}")
    if nth_value is not None:
        parts.append(f"nth={nth_value}")
    if exact is False:
        parts.append("exact=false")
    return base + (text, nth_value, exact), " ".join(parts)


def find_selector_dups(
    new_content: str,
    project_root: str,
    locator_dirs: List[str],
    target_rel: str,
) -> List[str]:
    """G10：不同 id 的 primary 选择器复合键重复检测（跨 locators 全目录）。

    fail-open：yaml 解析失败、依赖缺失等自身异常一律放行。
    """
    try:
        import yaml  # noqa: PLC0415
    except ImportError:
        # fail-open 是门禁的基本约定，但必须可见：缺少 PyYAML 时 G10 会永久假绿
        print(
            "[ui_gate][WARN] 未安装 PyYAML，G10 定位器重复检查已跳过；"
            "请执行 pip install pyyaml",
            file=sys.stderr,
        )
        return []
    try:
        new_items = yaml.safe_load(new_content) or []
    except Exception:
        return []
    if not isinstance(new_items, list):
        return []

    spans = _entry_spans(new_content)
    new_lines = (new_content or "").splitlines()
    new_entries: List[Tuple[Tuple, str, str, bool, int]] = []
    for pos, item in enumerate(new_items):
        if not isinstance(item, dict):
            continue
        parsed = _selector_key(item.get("primary"))
        if parsed is None:
            continue
        key, display = parsed
        locator_id = str(item.get("id") or "?")
        if pos < len(spans):
            start, end = spans[pos]
            raw = "\n".join(new_lines[start : end + 1])
            line_no = start + 1
        else:
            raw, line_no = "", 0
        new_entries.append((key, display, locator_id, DUP_EXEMPT_MARK in raw, line_no))

    # 已登记的其它文件（目标文件自身跳过，避免和磁盘旧内容自比）
    target_abs = _to_posix(os.path.abspath(_abs(project_root, target_rel)))
    seen: Dict[Tuple, List[str]] = {}
    for locator_dir in locator_dirs:
        dir_abs = _abs(project_root, locator_dir)
        if not os.path.isdir(dir_abs):
            continue
        for name in sorted(os.listdir(dir_abs)):
            if not name.endswith((".yaml", ".yml")) or name == "routes.yaml":
                continue
            path = os.path.join(dir_abs, name)
            if _to_posix(os.path.abspath(path)) == target_abs:
                continue
            try:
                with open(path, "r", encoding="utf-8") as handle:
                    items = yaml.safe_load(handle) or []
            except Exception:
                continue
            if not isinstance(items, list):
                continue
            for item in items:
                if not isinstance(item, dict):
                    continue
                parsed = _selector_key(item.get("primary"))
                if parsed is None:
                    continue
                seen.setdefault(parsed[0], []).append(f"{name}#{item.get('id')}")

    violations: List[str] = []
    local_seen: Dict[Tuple, List[str]] = {}
    for key, display, locator_id, exempt, line_no in new_entries:
        if exempt:
            continue
        refs = local_seen.get(key, []) + seen.get(key, [])
        if refs:
            where = "、".join(refs[:3]) + ("等" if len(refs) > 3 else "")
            violations.append(
                f"第 {line_no} 行 定位器 {locator_id} 的 primary（{display}）"
                f"与 {where} 选择器重复；不同 id 不应指向同一元素，"
                f"确需保留时在该条目内加 # {DUP_EXEMPT_MARK} 理由"
            )
        local_seen.setdefault(key, []).append(locator_id)
    return violations


def count_scenarios(checklist_text: str) -> Dict[str, int]:
    counts = {dim: 0 for dim in DIMENSION_RULES}
    for line in (checklist_text or "").splitlines():
        stripped = line.strip()
        if not stripped.startswith("|"):
            continue
        cells = [c.strip() for c in stripped.strip("|").split("|")]
        if len(cells) < 2 or not cells[0].isdigit():
            continue
        for dim in DIMENSION_RULES:
            if dim in cells:
                counts[dim] += 1
                break
    return counts


def read_confirm_state(checklist_text: str) -> Optional[str]:
    for line in (checklist_text or "").splitlines():
        match = _CONFIRM_FIELD_PATTERN.match(line.strip())
        if match:
            return match.group("value").strip()
    return None


def check_ratios(
    counts: Dict[str, int], dimensions: Dict[str, Any]
) -> Tuple[bool, str]:
    total = sum(counts.values())
    if total <= 0:
        return False, "清单中未识别到任何场景行（类型列需为 正向/负向/边界/异常）"
    ratios = {dim: counts[dim] / total for dim in counts}
    problems: List[str] = []
    for dim, (cfg_key, kind) in DIMENSION_RULES.items():
        try:
            limit = float(dimensions.get(cfg_key, DEFAULT_DIMENSIONS[cfg_key]))
        except (TypeError, ValueError):
            limit = DEFAULT_DIMENSIONS[cfg_key]
        actual = ratios[dim]
        if kind == "max" and actual > limit:
            problems.append(f"{dim} {actual:.0%} 超过上限 {limit:.0%}")
        if kind == "min" and actual < limit:
            problems.append(f"{dim} {actual:.0%} 低于下限 {limit:.0%}")
    detail = f"共 {total} 条场景：" + "、".join(
        f"{dim} {counts[dim]} 条({ratios[dim]:.0%})" for dim in counts
    )
    if problems:
        return False, detail + "；" + "；".join(problems)
    return True, detail


def _is_fixture(content: str, def_start: int) -> bool:
    """判断函数是否带 @pytest.fixture 装饰器。

    背景：fixture 常被命名为 `test_xxx`（如 `test_goods` 返回测试数据），
    若不加区分，G8 会把 fixture 当成用例并要求它带断言，直接误伤。
    """
    head = content[:def_start].rstrip("\n")
    tail_lines = head.splitlines()[-5:]
    return any("@pytest.fixture" in line for line in tail_lines)


def _block_name(match) -> str:
    """取分组里第一个非空值，兼容 Python / Java 两套函数识别正则。"""
    for index in range(match.re.groups, 0, -1):
        value = match.group(index)
        if value:
            return str(value)
    return "?"


def find_test_blocks(content: str, pattern: str = "") -> List[Tuple[str, str]]:
    """返回 [(函数名, 函数体)]，用于检查每个用例是否至少一个断言。

    pattern 可由 ui_gate_rules.json 的 checks.test_must_have_assertion.func_pattern
    覆盖原因：用例层是 @Given/@When/@Then 注解方法，没有 def test_。
    """
    regex = re.compile(pattern, re.MULTILINE) if pattern else _TEST_FUNC
    blocks: List[Tuple[str, str]] = []
    matches = list(regex.finditer(content or ""))
    for idx, match in enumerate(matches):
        if _is_fixture(content, match.start()):
            continue
        start = match.end()
        end = matches[idx + 1].start() if idx + 1 < len(matches) else len(content)
        blocks.append((_block_name(match), content[start:end]))
    return blocks


def evaluate(
    target_path: str,
    new_content: str,
    project_root: str,
    config: Optional[Dict[str, Any]] = None,
    rules: Optional[Dict[str, Any]] = None,
) -> Tuple[bool, List[str]]:
    config = config or load_config()
    rules = rules or load_rules()

    if not (config.get("gate") or {}).get("enabled", True):
        return True, ["门禁未启用（config.gate.enabled=false）"]

    paths_cfg = config.get("paths") or {}
    watch_dirs = []
    for key in ("test_case_dirs", "page_dirs", "flow_dirs", "locator_dirs"):
        watch_dirs.extend(paths_cfg.get(key) or [])
    watch_abs = [_to_posix(_abs(project_root, d)).rstrip("/") for d in watch_dirs]
    target = _to_posix(target_path)
    rel = os.path.relpath(target_path, project_root)
    rel = _to_posix(rel)

    in_watch = any(
        target == d or target.startswith(d + "/") for d in watch_abs
    )
    if not in_watch:
        return True, ["目标文件不在 UI 自动化目录内，门禁不介入"]

    scope = _match_scope(rel, rules)
    if scope is None:
        return True, [f"未匹配到门禁作用域（{rel}），放行"]

    messages: List[str] = []

    # G6 / G7 / G9：规则引擎
    violations = find_rule_violations(new_content or "", scope)
    if violations:
        messages.append(
            f"【G6/G7 分层规则违规 · {scope.get('name')}】\n  "
            + "\n  ".join(violations[:8])
        )

    # G9 XPath 豁免标记的额外提示
    if scope.get("name") == "locator_layer" and "xpath:" in (new_content or ""):
        pass  # 已在规则内处理，保留分支便于后续扩展

    # G10 定位器选择器重复（结构性检查，规则开关在 ui_gate_rules.json checks.locator_selector_dup）
    if scope.get("name") == "locator_layer" and (
        (rules.get("checks") or {}).get("locator_selector_dup") or {}
    ).get("enabled", True):
        dup_violations = find_selector_dups(
            new_content or "",
            project_root,
            paths_cfg.get("locator_dirs") or [],
            rel,
        )
        if dup_violations:
            messages.append(
                "【G10 定位器选择器重复】\n  " + "\n  ".join(dup_violations[:8])
            )

    # conftest.py 是测试基础设施（fixture / hook），不是用例：
    # 它经常出现 `test_goods` 这类 fixture 名，不能按用例门禁处理。
    is_conftest = os.path.basename(target) == "conftest.py"

    checks = rules.get("checks") or {}
    assertion_cfg = checks.get("test_must_have_assertion") or {}
    test_func_pattern = str(assertion_cfg.get("func_pattern") or "").strip()
    test_func_regex = (
        re.compile(test_func_pattern, re.MULTILINE)
        if test_func_pattern
        else _TEST_FUNC
    )

    is_test_write = (
        not is_conftest
        and bool(test_func_regex.search(new_content or ""))
        and any(
            target.startswith(_to_posix(_abs(project_root, d)).rstrip("/") + "/")
            for d in (paths_cfg.get("test_case_dirs") or [])
        )
    )

    if not is_test_write:
        return (False, messages) if messages else (True, ["门禁通过（非用例写入）"])

    # 以下为用例层门禁 G2 / G3 / G4 / G5 / G8
    case_design = config.get("case_design") or {}
    checklist_path = _abs(
        project_root,
        case_design.get("checklist_output") or "ui_test_dwp_temp/design_checklist.md",
    )
    checklist_text = ""
    if os.path.isfile(checklist_path):
        try:
            with open(checklist_path, "r", encoding="utf-8") as handle:
                checklist_text = handle.read()
        except Exception as exc:
            return True, [f"清单读取失败({exc})，门禁放行"]

    if checks.get("require_checklist", True) and not checklist_text:
        messages.append(
            f"【G2 缺少用例设计清单】未找到 {checklist_path}。"
            "必须先落盘清单并等用户确认。"
        )
        return False, messages

    if checks.get("require_confirm", True):
        state = read_confirm_state(checklist_text)
        if not state or not state.startswith(CONFIRMED_VALUES):
            messages.append(
                "【G3 清单未确认】`确认状态` 为"
                f"「{state or '未填写'}」。唯一确认口令：{confirm_protocol.CONFIRM_PHRASE}"
            )
        else:
            runtime_dir = paths_cfg.get("runtime_temp_dir") or confirm_protocol.DEFAULT_RUNTIME_DIR
            if confirm_protocol.find_valid_confirm(project_root, checklist_text, runtime_dir) is None:
                messages.append(
                    "【G3 确认无有效来源】`确认状态=已确认`，但确认日志没有与当前清单指纹"
                    "匹配的口令记录（可能是 AI 自行填写，或清单确认后被改动）。"
                )

    if checks.get("check_pending_items", True) and PENDING_MARK in checklist_text:
        messages.append(
            f"【G5 存在待确认项】清单中有 {checklist_text.count(PENDING_MARK)} 处 "
            f"{PENDING_MARK}，必须先向用户提问澄清。"
        )

    if checks.get("check_scenario_ratio", True):
        min_scenarios = int(case_design.get("min_scenarios_for_ratio", 4))
        counts = count_scenarios(checklist_text)
        if sum(counts.values()) >= min_scenarios:
            ok, detail = check_ratios(counts, case_design.get("dimensions") or {})
            if not ok:
                messages.append("【G4 场景占比不达标】" + detail)

    if assertion_cfg.get("enabled", True):
        hints = assertion_cfg.get("assertion_hints") or ["assert ", "expect("]
        for name, body in find_test_blocks(new_content, test_func_pattern):
            if not any(hint in body for hint in hints):
                messages.append(
                    f"【G8 用例无比断言】`{name}` 函数体内没有 assert / expect，"
                    "无法证明结果正确。"
                )

    if messages:
        return False, messages
    return True, ["门禁通过"]


def format_block_message(messages: List[str], target_path: str) -> str:
    header = (
        "UI 用例门禁拦截（ui-test-forge）：此次写入被阻止。\n"
        f"目标文件: {target_path}\n"
    )
    footer = (
        "\n\n修正方式：\n"
        "1. 落盘 ui_test_dwp_temp/design_checklist.md（模板见 assets/templates/checklist.md.tpl）\n"
        "2. 补齐 正向/负向/边界/异常 四类场景并请用户确认（口令：确认，按照清单继续）\n"
        "3. steps 层只调用 page object / flow 方法，禁止 page.locator / Thread.sleep / 硬编码 URL\n"
        "4. 定位器统一放 src/test/resources/locators/*.yaml，page 层不得内联选择器\n"
        "5. 规范原文：.codebuddy/skills/ui-test-forge/doc/team/README.md"
    )
    return header + "\n\n".join(messages) + footer


def _selftest() -> int:
    """内置自检：验证规则引擎在典型违规上确实会拦截。返回 0 表示自检通过。"""
    config = load_config()
    config.setdefault("gate", {})["enabled"] = True
    rules = load_rules()
    # 自检必须用 config 里的 project_root：环境变量 CODEBUDDY_PROJECT_DIR
    # 指向的是「当前打开的工程」，跑自检时可能与 skill 所属工程不一致。
    root = str(config.get("project_root") or "").strip() or resolve_project_root(config)
    # (目标文件, 内容, 是否期望出现拦截, 不应出现的误报标记)
    cases = [
        (
            # G6：steps 层裸定位 → 拦截
            "src/test/java/com/lili/ui/steps/BadSteps.java",
            '@Then("校验")\npublic void bad(Page page) {\n'
            '    page.locator("#a").click();\n}\n',
            True,
            (),
        ),
        (
            # G7：page 层引 JUnit 断言 → 拦截
            "src/test/java/com/lili/ui/pages/BadPage.java",
            "import org.junit.jupiter.api.Assertions;\n\npublic class BadPage {\n"
            "    void check() { Assertions.assertEquals(1, 2); }\n}\n",
            True,
            (),
        ),
        (
            # G10：新条目 primary 与已登记 cart.empty_tip 完全同写法 → 拦截
            "src/test/resources/locators/bad_dup.yaml",
            "- id: cart.empty_tip_copy\n  page: cart\n  primary: {by: css, value: '.cart-empty'}\n",
            True,
            (),
        ),
        (
            # G10：text 过滤不同 → 复合键不同，不应误报
            "src/test/resources/locators/ok_dup.yaml",
            "- id: cart.pay_gray\n  page: cart\n  primary: {by: css, value: 'div.pay', text: '合并支付'}\n"
            "# allowed-dup: 灰度过渡期新旧定位器并存\n"
            "- id: cart.empty_tip_gray\n  page: cart\n  # allowed-dup: 灰度过渡期新旧定位器并存\n"
            "  primary: {by: css, value: '.cart-empty'}\n",
            False,
            ("G10",),
        ),
    ]
    failed = 0
    for target, content, expect_block, false_positive_marks in cases:
        ok, messages = evaluate(
            os.path.join(root, target), content, root, config=config, rules=rules
        )
        joined = "\n".join(messages)
        for mark in false_positive_marks:
            if mark in joined:
                print(f"[selftest][FAIL] {target} 误报 {mark}：{messages}")
                failed += 1
        if expect_block and ok:
            print(f"[selftest][FAIL] {target} 应拦截却放行")
            failed += 1
    if failed:
        return 1
    print("[selftest] OK：违规写入被拦截，合规写入未被分层规则误拦")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="ui-test-forge 机械门禁")
    parser.add_argument("--target", help="待写入文件路径")
    parser.add_argument("--content-file", help="本次写入内容（文件路径）")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出")
    parser.add_argument("--selftest", action="store_true", help="运行内置自检")
    args = parser.parse_args()

    if args.selftest:
        return _selftest()

    if not args.target:
        parser.error("--target 必填")

    content = ""
    if args.content_file:
        with open(args.content_file, "r", encoding="utf-8") as handle:
            content = handle.read()

    config = load_config()
    root = resolve_project_root(config)
    target = args.target if os.path.isabs(args.target) else os.path.join(root, args.target)
    ok, messages = evaluate(target, content, root, config=config)

    if args.json:
        print(json.dumps({"ok": ok, "messages": messages}, ensure_ascii=False, indent=2))
    else:
        print(("PASS: " if ok else "BLOCK: ") + "\n".join(messages))
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
