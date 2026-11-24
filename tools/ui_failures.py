#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""失败清单：把一次测试运行的失败用例，整理成「场景 / 失败步骤 / 报错 / 上下文 url / trace」四列表。

背景：在 IDE 里跑 `mvn test`，终端是流水日志、JUnit 面板只到场景粒度，
定位「哪一步失败」要靠翻堆栈。本脚本直接读 surefire 的 XML 报告，
把 Cucumber 抛出的 `at ✽.步骤文本(file:...#行号)` 抽出来，一次列清。

用法：
    # 最近一次运行（默认）
    python3 tools/ui_failures.py

    # 指定报告目录 / 只看某个场景
    python3 tools/ui_failures.py --reports target/surefire-reports --grep 购物车

    # 输出机器可读
    python3 tools/ui_failures.py --json

数据来源：
    target/surefire-reports/TEST-*.xml   （每轮运行的场景级结果与堆栈）
    ui_test_dwp_temp/artifacts/<场景名>.zip（Playwright trace，可直接回放）
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from typing import Any, Dict, List

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TRACE_DIR = os.path.join(PROJECT_ROOT, "ui_test_dwp_temp", "artifacts")

# Cucumber 在堆栈里用 `✽.步骤文本(file:...#行号)` 标注失败步骤
STEP_PATTERN = re.compile(r"✽\.(?P<step>.+?)\s*\((?P<file>[^()]+?):(?P<line>\d+)\)")
URL_PATTERN = re.compile(r"url=(?P<url>\S+)")


def _latest_reports(reports_dir: str) -> List[str]:
    """取最新一次运行里「场景粒度」的报告。

    `runners/*FeatureTest` 是 IDE 单跑入口，聚合结果里没有步骤信息，必须排除；
    真正带堆栈的是 Cucumber 引擎的 XML。
    """
    files = glob.glob(os.path.join(reports_dir, "TEST-*.xml"))
    # 排除 runners/*FeatureTest 聚合报告（只有一条 runFeature，没有步骤信息）
    files = [f for f in files if ".runners." not in os.path.basename(f)]
    if not files:
        return []
    # 以「用例条数最多」的那份为准：全量跑时 RunCucumberTest 才包含所有场景
    def case_count(path: str) -> int:
        try:
            return len(list(ET.parse(path).getroot().iter("testcase")))
        except Exception:
            return 0

    best = max(files, key=lambda f: (case_count(f), os.path.getmtime(f)))
    return [best]
    newest = max(files, key=os.path.getmtime)
    return [newest]


def _case_payload(case: ET.Element) -> Dict[str, Any]:
    failure = case.find("failure")
    if failure is None:
        failure = case.find("error")
    if failure is None:
        return {}

    message = (failure.get("message") or "").strip()
    detail = (failure.text or "").strip()
    blob = message + "\n" + detail

    step_hit = STEP_PATTERN.search(blob)
    url_hit = URL_PATTERN.search(blob)

    first_line = ""
    for line in (message.splitlines() or detail.splitlines()):
        text = line.strip()
        if text and not text.startswith("org.") and not text.startswith("java."):
            first_line = text
            break

    scenario = case.get("name") or ""
    trace = os.path.join(TRACE_DIR, re.sub(r"[\\/:*?\"<>|\s]+", "_", scenario) + ".zip")

    return {
        "场景": scenario,
        "类型": "断言失败" if failure.tag == "failure" else "执行错误",
        "失败步骤": step_hit.group("step").strip() if step_hit else "（未捕获到步骤，见报错）",
        "步骤位置": f"{os.path.basename(step_hit.group('file'))}:{step_hit.group('line')}" if step_hit else "",
        "报错": first_line[:180],
        "url": _clean_url(url_hit.group("url")) if url_hit else "",
        "trace": os.path.relpath(trace, PROJECT_ROOT) if os.path.isfile(trace) else "",
    }


def _clean_url(raw: str) -> str:
    """断言消息里的 url 末尾常带 ], ) 等标点，去掉。"""
    return raw.rstrip("])}>,.;:")


def collect(reports_dir: str, keyword: str = "") -> List[Dict[str, Any]]:
    rows: List[Dict[str, Any]] = []
    for path in _latest_reports(reports_dir):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            if (case.get("name") or "") == "runFeature":
                continue
            payload = _case_payload(case)
            if not payload:
                continue
            if keyword and keyword not in payload["场景"]:
                continue
            rows.append(payload)
    return rows


def print_table(rows: List[Dict[str, Any]]) -> None:
    if not rows:
        print("没有失败用例（或报告目录里没有最新一轮的 XML）。")
        return
    print(f"失败用例 {len(rows)} 条\n")
    for index, row in enumerate(rows, 1):
        print(f"[{index}] {row['场景']}")
        print(f"    类型    : {row['类型']}")
        print(f"    失败步骤: {row['失败步骤']}" + (f"   ({row['步骤位置']})" if row["步骤位置"] else ""))
        print(f"    报错    : {row['报错']}")
        if row["url"]:
            print(f"    当时页面: {row['url']}")
        if row["trace"]:
            print(f"    回放    : npx playwright show-trace \"{row['trace']}\"")
        print()


def main() -> int:
    parser = argparse.ArgumentParser(description="UI 失败用例清单（场景 / 失败步骤 / 报错 / trace）")
    parser.add_argument("--reports", default=os.path.join(PROJECT_ROOT, "target", "surefire-reports"))
    parser.add_argument("--grep", default="", help="只列包含该关键字的场景")
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    args = parser.parse_args()

    rows = collect(args.reports, args.grep)
    if args.json:
        print(json.dumps(rows, ensure_ascii=False, indent=2))
    else:
        print_table(rows)
    return 0 if not rows else 2


if __name__ == "__main__":
    sys.exit(main())
