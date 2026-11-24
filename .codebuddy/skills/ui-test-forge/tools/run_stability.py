# -*- coding: utf-8 -*-
"""UI 用例稳定性运行器：连跑 N 次，识别 flake 与自愈命中。

为什么需要：
    UI 用例单次通过不代表稳定。定位器降级、时序问题、限流都只在
    偶发轮次里暴露。验证 Agent 必须拿到「多轮结果 + 自愈记录」才能判定。

用法：
    python tools/run_stability.py --runs 3
    python tools/run_stability.py --target "RunCucumberTest" --runs 2 --json

命令来源：
    config.json 的 run.command_template，占位符 {target} / {tags} / {alluredir}。
    Java 版示例：
        mvn -q test -Dcucumber.filter.tags={tags} -Dui.headless=true
    未配置时回落到 pytest（仅供无命令模板的环境使用）。

退出码：
    0 PASS / 3 PASS_WITH_WARN / 2 REJECT_FLAKE / 1 REJECT_FAIL
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import subprocess
import sys
import time
from typing import Any, Dict, List

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass

HERE = os.path.dirname(os.path.abspath(__file__))
SKILL_ROOT = os.path.dirname(HERE)


def _load_config() -> Dict[str, Any]:
    try:
        with open(os.path.join(SKILL_ROOT, "config.json"), "r", encoding="utf-8") as handle:
            return json.load(handle) or {}
    except Exception:
        return {}


def _project_root(config: Dict[str, Any]) -> str:
    env_root = os.environ.get("CODEBUDDY_PROJECT_DIR", "").strip()
    if env_root and os.path.isdir(env_root):
        return os.path.normpath(env_root)
    configured = str(config.get("project_root") or "").strip()
    if configured and os.path.isdir(configured):
        return os.path.normpath(configured)
    return os.getcwd()


_COUNT_PATTERN = re.compile(r"(\d+) (passed|failed|error|errors|skipped)")

# Maven Surefire 汇总行：Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
_MAVEN_COUNT_PATTERN = re.compile(
    r"Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)"
)


def _parse_counts(output: str) -> Dict[str, int]:
    counts = {"passed": 0, "failed": 0, "error": 0, "skipped": 0}

    for match in _MAVEN_COUNT_PATTERN.finditer(output):
        total, failures, errors, skipped = (int(group) for group in match.groups())
        passed = max(total - failures - errors - skipped, 0)
        counts["passed"] = max(counts["passed"], passed)
        counts["failed"] = max(counts["failed"], failures)
        counts["error"] = max(counts["error"], errors)
        counts["skipped"] = max(counts["skipped"], skipped)

    for line in output.splitlines():
        if " passed" not in line and " failed" not in line and " error" not in line:
            continue
        for number, label in _COUNT_PATTERN.findall(line):
            key = "error" if label.startswith("error") else label
            counts[key] = max(counts.get(key, 0), int(number))
    return counts


def _read_heals(path: str) -> List[Dict[str, Any]]:
    if not os.path.isfile(path):
        return []
    records: List[Dict[str, Any]] = []
    with open(path, "r", encoding="utf-8") as handle:
        for raw in handle:
            raw = raw.strip()
            if not raw:
                continue
            try:
                records.append(json.loads(raw))
            except Exception:
                continue
    return records


def _failure_tail(output: str, lines: int = 12) -> List[str]:
    interesting = [
        line for line in output.splitlines()
        if "FAILED" in line
        or "ERROR]" in line
        or "Error:" in line
        or "assert" in line.lower()
    ]
    return interesting[-lines:]


def _build_command(
    config: Dict[str, Any], args: argparse.Namespace, index: int
) -> List[str]:
    template = str((config.get("run") or {}).get("command_template") or "").strip()
    if template:
        rendered = template.format(
            target=args.target,
            tags=args.marker,
            alluredir=f"{args.runs_dir}/run{index}",
        )
        return shlex.split(rendered)

    cmd = [
        args.python, "-m", "pytest", args.target, "-q", "--tb=short",
        f"--alluredir={args.runs_dir}/run{index}",
    ]
    if args.marker:
        cmd += ["-m", args.marker]
    if args.keyword:
        cmd += ["-k", args.keyword]
    return cmd


def main() -> int:
    parser = argparse.ArgumentParser(description="UI 用例稳定性运行器")
    parser.add_argument("--target", default="", help="运行目标（Java 版可留空，由 command_template 决定）")
    parser.add_argument("--runs", type=int, default=3, help="运行轮次，默认 3")
    parser.add_argument("-m", "--marker", default="", help="标签/marker 表达式，如 @p0")
    parser.add_argument("-k", "--keyword", default="", help="pytest -k 表达式（Java 版忽略）")
    parser.add_argument("--runs-dir", default="allure-results-ui", help="allure 结果根目录")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出")
    parser.add_argument("--python", default=sys.executable, help="解释器路径（pytest 模式）")
    args = parser.parse_args()

    config = _load_config()
    project_root = _project_root(config)
    runtime_dir = (config.get("paths") or {}).get("runtime_temp_dir") or "ui_test_dwp_temp"
    heal_dir = os.path.join(project_root, runtime_dir)

    results: List[Dict[str, Any]] = []
    heals: List[Dict[str, Any]] = []

    for index in range(1, args.runs + 1):
        heal_log = os.path.join(heal_dir, f"heals_run{index}.jsonl")
        if os.path.isfile(heal_log):
            os.remove(heal_log)

        cmd = _build_command(config, args, index)

        env = dict(os.environ)
        env["UI_HEAL_LOG"] = heal_log

        started = time.monotonic()
        proc = subprocess.run(
            cmd, cwd=project_root, env=env,
            capture_output=True, text=True, encoding="utf-8", errors="replace",
        )
        duration = round(time.monotonic() - started, 2)
        output = (proc.stdout or "") + (proc.stderr or "")
        counts = _parse_counts(output)

        entry = {
            "run": index,
            "returncode": proc.returncode,
            "duration_s": duration,
            **counts,
            "alluredir": f"{args.runs_dir}/run{index}",
        }
        if proc.returncode != 0:
            entry["failure_tail"] = _failure_tail(output)
        results.append(entry)
        heals.extend(_read_heals(heal_log))

    passed_runs = [r for r in results if r["returncode"] == 0]
    failed_runs = [r for r in results if r["returncode"] != 0]

    if not failed_runs:
        verdict = "PASS_WITH_WARN" if heals else "PASS"
        exit_code = 3 if heals else 0
        reason = "全部轮次通过，但存在定位器自愈" if heals else "全部轮次通过"
    elif passed_runs:
        verdict = "REJECT_FLAKE"
        exit_code = 2
        reason = f"{len(failed_runs)}/{len(results)} 轮失败，属不稳定用例"
    else:
        verdict = "REJECT_FAIL"
        exit_code = 1
        reason = "全部轮次失败"

    summary = {
        "verdict": verdict,
        "reason": reason,
        "runs": results,
        "heals": heals,
    }

    if args.json:
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return exit_code

    print("轮次  结果    通过  失败  错误  耗时")
    for item in results:
        status = "OK" if item["returncode"] == 0 else "FAIL"
        print(
            f"{item['run']:<5} {status:<6} {item['passed']:<5} "
            f"{item['failed']:<5} {item['error']:<5} {item['duration_s']}s"
        )

    if heals:
        print("\n自愈命中（需人工复核，定位器已降级）：")
        for record in heals:
            print(f"  - {record.get('test')}: {record.get('detail')}")

    if failed_runs:
        print("\n失败轮次摘要：")
        for item in failed_runs:
            print(f"  run{item['run']}:")
            for line in item.get("failure_tail") or []:
                print(f"    {line.strip()}")

    print(f"\n结论: {verdict} —— {reason}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
