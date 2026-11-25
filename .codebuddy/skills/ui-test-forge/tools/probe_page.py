# -*- coding: utf-8 -*-
"""页面元素探针（固定 CLI，供 AI 读取结构化结果后写定位器）。

与「AI 自由逛页面」的区别：
- 只读、限步、可复现；默认不点击任何写操作按钮；
- 输出裁剪后的候选元素表，避免整页 HTML 占用上下文；
- 可见性判定用 rect.x >= 0 + computedStyle（对 translateX(-9999px) 的弹层有效）。

重要：**探针不录屏、不生成 trace、不调用任何模型**。
采集只依赖一次 `frame.evaluate(COLLECT_JS)` 的结构化提取，产出 YAML 候选表；
「AI 分析」发生在拿到这张表之后（写定位器），而不是分析一段录像。

用法：
    python tools/probe_page.py --route /cart --out ui_test_dwp_temp/probe/cart.yaml
    python tools/probe_page.py --route /home/MyOrder --verify src/test/resources/locators/order_list.yaml
    python tools/probe_page.py --route /cart --explain "去结算"
    python tools/probe_page.py --route /goodsList --steps "click=text:筛选" --out ui_test_dwp_temp/probe/goods_list.yaml
    python tools/probe_page.py --route /cart --a11y --out ui_test_dwp_temp/probe/cart.yaml

巡检时 --verify 会额外输出 verified_at / age_days / stale 三个字段，超过阈值（默认 30 天）
的定位器打到 stderr 告警，但不改变退出码（过期是「该复测」，不是「用例坏了」）。

退出码：
    0  正常（--verify 时表示全部 health=ok）
    1  参数/环境错误
    2  --verify 存在 broken / duplicate
    3  --steps 被安全策略拒绝（危险操作、多点命中、不在白名单）
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from datetime import date
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
SKILL_ROOT = os.path.dirname(HERE)

# 定位器新鲜度阈值（天）。超过即视为过期，需重新探测并更新 verified_at。
# 与 doc/team/locator_policy.md 的字段约定保持一致；可用 --stale-days 覆盖。
STALE_DAYS_DEFAULT = 30

# 采集脚本版本。写进输出便于比对「这份 yaml 是哪版探针出的」。
COLLECT_VERSION = 2

EXIT_STEP_REJECTED = 3

# 默认安全策略。以 config.json 的 probe 段为准，这里只是「配置缺失时的保守默认」。
DEFAULT_SAFE_HINTS = [
    "筛选", "排序", "展开", "收起", "更多", "下一页", "上一页",
    "切换", "页签", "下拉", "查看", "全部", "价格", "销量", "综合",
]
DEFAULT_DANGER_WORDS = [
    "提交", "支付", "结算", "购买", "删除", "清空", "取消订单",
    "确认收货", "退出", "领取", "兑换", "保存", "加入购物车", "立即购买",
    "确定", "确认",
]

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass


def _load_config() -> Dict[str, Any]:
    path = os.path.join(SKILL_ROOT, "config.json")
    try:
        with open(path, "r", encoding="utf-8") as handle:
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


def _probe_conf(config: Dict[str, Any]) -> Dict[str, Any]:
    """探针自身的运行参数。缺项一律回落保守默认，避免旧 config.json 直接炸。"""
    conf = config.get("probe") or {}
    if not isinstance(conf, dict):
        conf = {}
    return {
        "max_elements": int(conf.get("max_elements") or 200),
        "max_frames": int(conf.get("max_frames") or 8),
        "max_frame_depth": int(conf.get("max_frame_depth") or 3),
        "max_shadow_depth": int(conf.get("max_shadow_depth") or 3),
        "max_visited": int(conf.get("max_visited") or 6000),
        "max_steps": int(conf.get("max_steps") or 5),
        "validate_l1": bool(conf.get("validate_l1", True)),
        "a11y": bool(conf.get("a11y", False)),
        "safe_hints": list(conf.get("safe_hints") or DEFAULT_SAFE_HINTS),
        "danger_words": list(conf.get("danger_words") or DEFAULT_DANGER_WORDS),
    }


# ---------------------------------------------------------------------------
# 采集脚本：一次 evaluate 拿到结构化候选表
# ---------------------------------------------------------------------------
# 相比 v1 的三点变化：
# 1. 递归 shadowRoot（open shadow DOM 才进得去，closed 只能标记）；
# 2. role / accessible name 按 ARIA 常见规则推导，让 L1 建议更接近 Playwright 引擎口径
#    （真正的 L1 有效性由 Python 侧 get_by_role 回验，不在这里赌）；
# 3. 把 translateX 负偏移的元素从「不可见」里拆出来，记为 off_canvas 单列计数，
#    避免横向滚动容器里的元素被静默丢弃。
COLLECT_JS = r"""
(opts) => {
  const conf = opts || {};
  const maxElements = conf.maxElements || 200;
  const maxShadowDepth = conf.maxShadowDepth || 3;
  const maxVisited = conf.maxVisited || 6000;
  const SEL = 'a,button,input,select,textarea,[role],[data-testid],[data-test],[onclick]';

  const textOf = (el) => (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();

  const svgSafe = (value) => String(value == null ? '' : value);

  const explicitRole = (el) => (el.getAttribute('role') || '').trim().toLowerCase();

  const implicitRole = (el) => {
    const tag = el.tagName.toLowerCase();
    const type = (el.getAttribute('type') || '').toLowerCase();
    if (tag === 'a') return el.hasAttribute('href') ? 'link' : '';
    if (tag === 'button') return 'button';
    if (tag === 'select') return 'combobox';
    if (tag === 'textarea') return 'textbox';
    if (tag === 'input') {
      if (type === 'hidden') return '';
      if (type === 'checkbox') return 'checkbox';
      if (type === 'radio') return 'radio';
      if (type === 'submit' || type === 'button' || type === 'reset') return 'button';
      return 'textbox';
    }
    if (/^h[1-6]$/.test(tag)) return 'heading';
    if (tag === 'img') return 'img';
    if (tag === 'li') return 'listitem';
    return '';
  };

  // accessible name 的常见来源，优先级按 ARIA 规范的主干：
  // aria-labelledby > aria-label > label[for] / 包裹 label > form-item label
  // > alt / title > 文本 > placeholder > value
  const accessibleName = (el) => {
    const labelledby = el.getAttribute('aria-labelledby');
    if (labelledby) {
      const parts = labelledby.split(/\s+/)
        .map((id) => { try { return document.getElementById(id); } catch (e) { return null; } })
        .filter(Boolean)
        .map((node) => textOf(node))
        .filter(Boolean);
      if (parts.length) return parts.join(' ');
    }
    const aria = (el.getAttribute('aria-label') || '').trim();
    if (aria) return aria;
    if (el.id) {
      try {
        const forLabel = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
        if (forLabel && textOf(forLabel)) return textOf(forLabel);
      } catch (e) { /* CSS.escape 不可用时忽略 */ }
    }
    const wrapLabel = el.closest('label');
    if (wrapLabel && textOf(wrapLabel)) return textOf(wrapLabel);
    const formItem = el.closest('[class*=form-item]');
    if (formItem) {
      const label = formItem.querySelector('[class*=label], label');
      if (label && textOf(label)) return textOf(label);
    }
    const alt = (el.getAttribute('alt') || '').trim();
    if (alt) return alt;
    const title = (el.getAttribute('title') || '').trim();
    if (title) return title;
    const text = textOf(el);
    if (text) return text;
    const placeholder = (el.getAttribute('placeholder') || '').trim();
    if (placeholder) return placeholder;
    const value = svgSafe(el.getAttribute('value')).trim();
    if (value) return value;
    return '';
  };

  // parentElement 不跨 shadow 边界，出界时跳到 host 继续往上，保证父链完整。
  const chain = (el) => {
    const out = [];
    let cur = el;
    for (let i = 0; i < 6 && cur; i += 1) {
      out.push(cur.tagName + '.' + String(cur.className || '').slice(0, 50));
      let next = cur.parentElement;
      if (!next) {
        const root = cur.getRootNode && cur.getRootNode();
        if (root && root.host) next = root.host;
      }
      cur = next;
    }
    return out;
  };

  const geom = (el) => {
    const rect = el.getBoundingClientRect();
    return {
      x: Math.round(rect.x),
      y: Math.round(rect.y),
      w: Math.round(rect.width),
      h: Math.round(rect.height),
    };
  };

  // 与 v1 完全一致，避免「顺手改进可见性」把基线打乱（checkVisibility 会过滤更多元素）。
  const visible = (el, box) => {
    const style = getComputedStyle(el);
    return box.w > 0 && box.h > 0
      && style.visibility !== 'hidden' && style.display !== 'none'
      && parseFloat(style.opacity || '1') > 0;
  };

  const describe = (el, shadowDepth, hostChain) => {
    const box = geom(el);
    const cls = String(el.className || '').slice(0, 80);
    const role = explicitRole(el) || implicitRole(el);
    const name = (accessibleName(el) || '').slice(0, 60);
    const inShadow = shadowDepth > 0;
    return {
      tag: el.tagName.toLowerCase(),
      role: role || null,
      name: name,
      aria_label: (el.getAttribute('aria-label') || '').slice(0, 60) || null,
      placeholder: el.getAttribute('placeholder'),
      testid: el.getAttribute('data-testid') || el.getAttribute('data-test'),
      id: el.id || null,
      cls: cls,
      class_has_hash: /[a-f0-9]{6,}/i.test(cls),
      text: textOf(el).slice(0, 60),
      disabled: el.disabled === true || el.getAttribute('aria-disabled') === 'true',
      visible: visible(el, box),
      off_canvas: box.x < 0,
      rect: {x: box.x, y: box.y, w: box.w},
      parent_chain: chain(el),
      shadow_dom: inShadow,
      in_shadow: inShadow,
      shadow_depth: shadowDepth,
      shadow_host_chain: hostChain.slice(0, 3),
    };
  };

  const result = {elements: [], off_canvas_count: 0, truncated: false, shadow_hosts: 0};
  const seen = new Set();
  let visited = 0;

  const walk = (root, depth, hostChain) => {
    if (visited >= maxVisited) { result.truncated = true; return; }
    let nodes = [];
    try { nodes = root.querySelectorAll(SEL); } catch (e) { nodes = []; }
    for (const el of nodes) {
      if (result.elements.length >= maxElements) { result.truncated = true; break; }
      if (seen.has(el)) continue;
      seen.add(el);
      const item = describe(el, depth, hostChain);
      if (!item.visible) continue;
      if (item.off_canvas) { result.off_canvas_count += 1; continue; }
      result.elements.push(item);
    }
    if (depth >= maxShadowDepth) return;
    // shadow host 不一定命中 SEL（自定义元素），必须扫全量元素才能发现
    let all = [];
    try { all = root.querySelectorAll('*'); } catch (e) { all = []; }
    for (const el of all) {
      visited += 1;
      if (visited >= maxVisited) { result.truncated = true; break; }
      if (el.shadowRoot) {
        result.shadow_hosts += 1;
        const label = el.tagName.toLowerCase() + '.' + String(el.className || '').slice(0, 40);
        walk(el.shadowRoot, depth + 1, hostChain.concat([label]));
      }
    }
  };

  walk(document, 0, []);
  result.elements.forEach((item, i) => { item.index = i; });
  return result;
}
"""


def _suggest_level(element: Dict[str, Any]) -> str:
    if element.get("testid"):
        return "L2"
    if element.get("role") and element.get("name"):
        return "L1"
    if element.get("class_has_hash"):
        return "L5"
    if element.get("cls"):
        return "L4"
    return "L5"


def _safe(text: Any, limit: int = 120) -> str:
    return str(text or "")[:limit]


# ---------------------------------------------------------------------------
# 登录态：接口取 token 后注入 cookie（不处理滑块）
# ---------------------------------------------------------------------------
def _md5_hex(raw: str) -> str:
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def _login_cookies(project_root: str) -> List[Dict[str, str]]:
    """接口登录拿 token，返回待注入的 cookie。

    v1 依赖 `from api import auth_api`，但本项目没有 `api` 包，异常被上层 except 吞掉，
    结果是「一直在匿名探测」而没有任何提示。这里改成用标准库直连登录接口，
    端点与参数和 Java 侧 `ApiClient.login()` 保持一致：
    `POST <api_base>/buyer/passport/member/userLogin`，form 传 username + md5(password)，
    `uuid` 与 `clientType` 放请求头（缺失会被网关 400）。
    """
    api_base = os.getenv("LILISHOP_BASE_URL", "http://127.0.0.1/api").rstrip("/")
    ui_host = os.getenv("LILISHOP_UI_HOST") or "127.0.0.1"
    username = os.getenv("LILISHOP_USERNAME", "kk1024")
    password = os.getenv("LILISHOP_PASSWORD")
    if not password:
        raise SystemExit(
            "请先设置 LILISHOP_PASSWORD 环境变量（lilishop 演示账号密码）；"
            "缺省时会以匿名身份探测，结果不可信"
        )

    body = urllib.parse.urlencode(
        {"username": username, "password": _md5_hex(password)}
    ).encode("utf-8")
    request = urllib.request.Request(
        f"{api_base}/buyer/passport/member/userLogin",
        data=body,
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "clientType": "PC",
            "uuid": "probe-page",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if payload.get("code") != 200:
        raise RuntimeError(f"API 登录失败，无法探测需要登录的页面: {payload}")
    tokens = payload.get("result") or {}
    return [
        {"name": "accessToken", "value": tokens["accessToken"], "domain": ui_host, "path": "/"},
        {"name": "refreshToken", "value": tokens["refreshToken"], "domain": ui_host, "path": "/"},
    ]


# ---------------------------------------------------------------------------
# 定位解析：page 与 frame 共用一套 by 语义
# ---------------------------------------------------------------------------
def _resolve(scope, spec: Dict[str, Any]):
    """按登记表的 `by` 语义在 page 或 frame 上解析定位器。

    scope 既可以是 Page 也可以是 Frame——两者都提供 get_by_role / get_by_* 方法，
    这样 --verify 与 --steps 用的是同一套解析规则，不会出现「探到的」和「点的」不一致。
    """
    by = spec.get("by")
    if by == "role":
        kwargs: Dict[str, Any] = {}
        if spec.get("name"):
            kwargs["name"] = spec["name"]
        if "exact" in spec:
            kwargs["exact"] = bool(spec["exact"])
        return scope.get_by_role(spec["role"], **kwargs)
    if by == "label":
        return scope.get_by_label(spec["value"])
    if by == "placeholder":
        return scope.get_by_placeholder(spec["value"])
    if by == "testid":
        return scope.get_by_test_id(spec["value"])
    if by == "text":
        return scope.get_by_text(spec["value"], exact=spec.get("exact", True))
    if by == "css":
        locator = scope.locator(spec["value"])
    elif by == "xpath":
        locator = scope.locator(f"xpath={spec['value']}")
    else:
        raise ValueError(f"未知的定位方式: {by}")
    if spec.get("text") and by in ("css", "xpath"):
        locator = locator.filter(has_text=spec["text"])
    return locator


def _match_count(scope, spec: Dict[str, Any]) -> int:
    """返回命中数。口径说明：

    `nth` **不参与**计数——`locator_policy` 明确禁止用 `nth(0)` 掩盖不唯一，
    所以 count>1 一律报 duplicate，哪怕登记表里写了 nth。
    """
    return _resolve(scope, spec).count()


# ---------------------------------------------------------------------------
# frame 遍历
# ---------------------------------------------------------------------------
def _frame_paths(page, max_frames: int, max_depth: int) -> List[Tuple[Any, str, int]]:
    """返回 [(frame, path, depth)]，主文档在最前。

    path 形如 `top` / `iframe[0]` / `iframe[0]/iframe[1]`，直接写进候选表，
    让看 yaml 的人知道元素在哪个框架里，而不是误以为是主文档元素。
    """
    result: List[Tuple[Any, str, int]] = []
    try:
        main = page.main_frame
    except Exception:
        return result
    result.append((main, "top", 0))
    queue: List[Tuple[Any, str, int]] = [(main, "top", 0)]
    while queue and len(result) < max_frames:
        parent, parent_path, depth = queue.pop(0)
        if depth >= max_depth:
            continue
        try:
            children = list(parent.child_frames)
        except Exception:
            children = []
        for index, child in enumerate(children):
            if len(result) >= max_frames:
                break
            path = f"iframe[{index}]" if parent_path == "top" else f"{parent_path}/iframe[{index}]"
            result.append((child, path, depth + 1))
            queue.append((child, path, depth + 1))
    return result


def _count_all_frames(page, frames: Sequence[Tuple[Any, str, int]], spec: Dict[str, Any]) -> Tuple[int, Dict[str, int]]:
    """跨 frame 求和。Playwright 的 page.get_by_* 只查主文档，不跨 iframe。"""
    total = 0
    per_frame: Dict[str, int] = {}
    for frame, path, _depth in frames:
        try:
            count = _match_count(frame, spec)
        except Exception:
            continue
        if count:
            per_frame[path] = count
        total += count
    return total, per_frame


def _collect(page, conf: Dict[str, Any]) -> Dict[str, Any]:
    """逐 frame 采集，全局元素预算按「主文档优先」分摊。"""
    frames = _frame_paths(page, conf["max_frames"], conf["max_frame_depth"])
    budget = conf["max_elements"]
    elements: List[Dict[str, Any]] = []
    errors: List[Dict[str, str]] = []
    off_canvas = 0
    shadow_hosts = 0
    truncated = False

    for frame, path, _depth in frames:
        if budget <= 0:
            truncated = True
            break
        opts = {
            "maxElements": budget,
            "maxShadowDepth": conf["max_shadow_depth"],
            "maxVisited": conf["max_visited"],
        }
        try:
            payload = frame.evaluate(COLLECT_JS, opts) or {}
        except Exception as exc:
            # 单帧失败（跨域策略、frame 已 detach）只记录，不中断整次探测
            errors.append({"frame": path, "error": _safe(exc)})
            continue
        if not isinstance(payload, dict):
            continue
        got = payload.get("elements") or []
        for element in got:
            element["frame"] = path
            element["frame_url"] = _safe(getattr(frame, "url", ""))
        elements.extend(got)
        budget -= len(got)
        off_canvas += int(payload.get("off_canvas_count") or 0)
        shadow_hosts += int(payload.get("shadow_hosts") or 0)
        if payload.get("truncated"):
            truncated = True

    for index, element in enumerate(elements):
        element["index"] = index

    return {
        "elements": elements,
        "frame_errors": errors,
        "frames": [{"path": path, "url": _safe(getattr(frame, "url", ""))} for frame, path, _ in frames],
        "frame_truncated": len(frames) >= conf["max_frames"],
        "off_canvas_count": off_canvas,
        "shadow_hosts": shadow_hosts,
        "truncated": truncated,
    }


# ---------------------------------------------------------------------------
# L1 回验：用 Playwright 自己的 a11y 引擎，而不是在 JS 里重写 ARIA 算法
# ---------------------------------------------------------------------------
def _degrade_level(element: Dict[str, Any]) -> str:
    if element.get("testid"):
        return "L2"
    if element.get("cls") and not element.get("class_has_hash"):
        return "L4"
    return "L5"


def _validate_l1(page, frames, element: Dict[str, Any]) -> Optional[str]:
    """回验 role+name 是否真能唯一定位；返回 None 表示通过，否则返回降级原因。

    为什么必须回验：JS 侧的 name 是启发式推导，和 Playwright 的 accessible name
    算法不完全一致。不回验就会出现「探针说 L1 → 写进 yaml → verified_at 更新 →
    --verify 报 primary_matches: 0」这种最难查的假阳性。
    """
    role = element.get("role")
    name = element.get("name")
    if not role or not name:
        return None
    spec = {"by": "role", "role": role, "name": name, "exact": True}
    total, per_frame = _count_all_frames(page, frames, spec)
    if total == 1:
        element["l1_verified"] = True
        return None
    element["l1_verified"] = False
    element["l1_hits"] = {"total": total, "per_frame": per_frame}
    if total == 0:
        return "get_by_role(name, exact=True) 命中 0"
    return f"get_by_role(name, exact=True) 命中 {total}，不唯一"


# ---------------------------------------------------------------------------
# --steps：白名单交互后再采集
# ---------------------------------------------------------------------------
class StepRejected(Exception):
    """步骤被安全策略拒绝（危险词、未在白名单、命中数不为 1）。"""


def _parse_step(raw: str) -> Tuple[str, Any]:
    text = (raw or "").strip()
    if "=" not in text:
        raise StepRejected(f"步骤格式应为 `click=<by>:<value>` 或 `wait=networkidle`：{raw}")
    kind, payload = text.split("=", 1)
    kind = kind.strip().lower()
    payload = payload.strip()
    if kind == "wait":
        if payload not in ("networkidle", "domcontentloaded", "load"):
            raise StepRejected(f"wait 只接受 networkidle / domcontentloaded / load，不接受毫秒数：{payload}")
        return "wait", payload
    if kind != "click":
        raise StepRejected(f"只允许 click / wait 两种步骤，收到：{kind}")
    if not payload:
        raise StepRejected("click 步骤缺少定位描述")
    if payload.startswith("role:"):
        rest = payload[len("role:"):]
        if "@" not in rest:
            raise StepRejected("role 定位需写成 `click=role:button@筛选`")
        role, name = rest.split("@", 1)
        return "click", {"by": "role", "role": role.strip(), "name": name.strip(), "exact": True}
    if ":" not in payload:
        raise StepRejected("定位描述需写成 `<by>:<value>`，如 `text:筛选`")
    by, value = payload.split(":", 1)
    by = by.strip()
    if by not in ("text", "css", "testid", "label", "placeholder"):
        raise StepRejected(f"步骤定位不支持 by={by}（xpath 默认禁用，避免结构耦合）")
    spec: Dict[str, Any] = {"by": by, "value": value.strip()}
    if by == "text":
        spec["exact"] = True
    return "click", spec


def _locator_label(locator) -> str:
    """尽量拿到元素自身的可读文案，用于白名单/黑名单判定。"""
    parts: List[str] = []
    try:
        aria = locator.get_attribute("aria-label", timeout=1000)
        if aria:
            parts.append(aria)
    except Exception:
        pass
    try:
        inner = locator.inner_text(timeout=1000)
        if inner:
            parts.append(inner)
    except Exception:
        pass
    return " ".join(parts).strip()


def _assert_safe(label: str, element_label: str, conf: Dict[str, Any]) -> None:
    joined = f"{label} {element_label}"
    for word in conf["danger_words"]:
        if word and word in joined:
            raise StepRejected(f"命中危险词「{word}」，拒绝点击：{joined[:80]}")
    hints = conf["safe_hints"]
    if hints and not any(hint and hint in joined for hint in hints):
        raise StepRejected(
            f"未命中交互白名单（允许 {'/'.join(hints[:6])} 等展开类控件）：{joined[:80]}"
        )


def _run_steps(page, steps: Sequence[str], conf: Dict[str, Any]) -> List[Dict[str, Any]]:
    """执行白名单交互。任何一步不合规都抛 StepRejected，整次探测不产出候选表。"""
    if len(steps) > conf["max_steps"]:
        raise StepRejected(f"步骤数 {len(steps)} 超过上限 {conf['max_steps']}")
    done: List[Dict[str, Any]] = []
    for raw in steps:
        kind, payload = _parse_step(raw)
        if kind == "wait":
            page.wait_for_load_state(payload)
            done.append({"step": raw, "ok": True})
            continue
        frames = _frame_paths(page, conf["max_frames"], conf["max_frame_depth"])
        total, per_frame = _count_all_frames(page, frames, payload)
        if total != 1:
            raise StepRejected(
                f"步骤 {raw} 命中 {total} 个元素（必须恰好 1 个），per_frame={per_frame}"
            )
        frame_path = next(iter(per_frame))
        target_frame = next(f for f, p, _ in frames if p == frame_path)
        locator = _resolve(target_frame, payload)
        label = f"{payload.get('value') or payload.get('name') or ''}"
        _assert_safe(label, _locator_label(locator), conf)
        locator.click()
        page.wait_for_load_state("networkidle")
        done.append({"step": raw, "ok": True, "frame": frame_path})
    return done


# ---------------------------------------------------------------------------
# 可选产物：a11y 语义树
# ---------------------------------------------------------------------------
def _write_a11y(page, out_path: str) -> Optional[str]:
    last_error = ""
    for selector in ("body", "html"):
        try:
            snapshot = page.locator(selector).aria_snapshot(timeout=5000)
        except Exception as exc:
            last_error = _safe(exc)
            continue
        if snapshot:
            os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
            with open(out_path, "w", encoding="utf-8") as handle:
                handle.write(snapshot if snapshot.endswith("\n") else snapshot + "\n")
            return out_path
    print(f"[probe][WARN] aria_snapshot 失败，未生成语义树: {last_error}", file=sys.stderr)
    return None


# ---------------------------------------------------------------------------
# 新鲜度
# ---------------------------------------------------------------------------
def _parse_verified_at(raw: Any) -> Optional[date]:
    """解析 verified_at；缺失或格式不合法一律返回 None（不抛异常）。

    为什么吞掉异常：巡检工具要在任何脏数据下都能跑完并给出全量结果，
    单个条目的日期写错不应该让整次巡检失败（fail-open，与门禁同口径）。
    """
    if raw is None:
        return None
    text = str(raw).strip()
    if not text:
        return None
    try:
        return date.fromisoformat(text)
    except ValueError:
        return None


def _staleness(spec: Dict[str, Any], today: date, stale_days: int) -> Dict[str, Any]:
    """计算单条定位器的新鲜度。

    stale 是三态，不要合并成一个布尔：
    - True  已过期，需要重新探测
    - False 未过期
    - None  无法判定（verified_at 缺失/格式错），要单独提示，否则脏数据会被当成新鲜
    """
    verified = _parse_verified_at(spec.get("verified_at"))
    if verified is None:
        return {"verified_at": spec.get("verified_at"), "age_days": None, "stale": None}
    age_days = (today - verified).days
    return {"verified_at": verified.isoformat(), "age_days": age_days, "stale": age_days > stale_days}


def _report_staleness(rows: List[Dict[str, Any]], stale_days: int) -> None:
    """把过期/无法判定的定位器打到 stderr。

    只告警、不改变退出码：`health` 表示「这条定位器现在对不对」，
    新鲜度表示「这条定位器多久没复测」，两者语义不同。
    若把过期算进退出码，CI 会在什么都没坏的情况下变红，最后只能把检查关掉。
    """
    stale = [row for row in rows if row.get("stale") is True]
    unknown = [row for row in rows if row.get("stale") is None]

    if stale:
        detail = "、".join(f"{row.get('id')}({row.get('age_days')}天)" for row in stale[:5])
        suffix = f" 等共 {len(stale)} 条" if len(stale) > 5 else ""
        print(
            f"[probe][WARN] {len(stale)} 个定位器超过 {stale_days} 天未复测，"
            f"需重新探测并更新 verified_at: {detail}{suffix}",
            file=sys.stderr,
        )

    if unknown:
        detail = "、".join(str(row.get("id")) for row in unknown[:5])
        suffix = f" 等共 {len(unknown)} 条" if len(unknown) > 5 else ""
        print(
            f"[probe][WARN] {len(unknown)} 个定位器缺少可解析的 verified_at"
            f"（格式应为 YYYY-MM-DD），无法判断新鲜度: {detail}{suffix}",
            file=sys.stderr,
        )


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
def _route_slug(route: str) -> str:
    slug = re.sub(r"[^0-9A-Za-z]+", "_", route).strip("_")
    return slug or "root"


class _ArgParser(argparse.ArgumentParser):
    """参数错误返回 1，而不是 argparse 默认的 2。

    原因：2 在本工具里表示「verify 有 broken/duplicate」，两者混用会让 CI 分不清
    「调用写错了」和「定位器坏了」。
    """

    def error(self, message: str) -> None:  # type: ignore[override]
        self.print_usage(sys.stderr)
        print(f"[probe] 参数错误: {message}", file=sys.stderr)
        raise SystemExit(1)


def main() -> int:
    parser = _ArgParser(description="ui-test-forge 页面探针")
    parser.add_argument("--route", required=True, help="页面路由，如 /cart")
    parser.add_argument("--out", help="输出 YAML 路径")
    parser.add_argument("--verify", help="校验定位器 YAML 文件，输出命中数")
    parser.add_argument("--explain", help="只输出文本匹配该关键词的元素与父链")
    parser.add_argument("--headed", action="store_true", help="有头模式（人工观察）")
    parser.add_argument(
        "--steps",
        action="append",
        default=[],
        metavar="STEP",
        help="采集前的白名单交互，如 --steps \"click=text:筛选\"，可重复",
    )
    parser.add_argument(
        "--a11y",
        action="store_true",
        help="额外输出 Playwright a11y 语义树（<out>.a11y.yaml）",
    )
    parser.add_argument(
        "--no-validate-l1",
        action="store_true",
        help="关闭 L1 回验（探针更快，但可能给出不可用的 L1 建议）",
    )
    parser.add_argument(
        "--stale-days",
        type=int,
        default=STALE_DAYS_DEFAULT,
        help=f"verified_at 超过多少天视为过期（默认 {STALE_DAYS_DEFAULT}）",
    )
    args = parser.parse_args()

    config = _load_config()
    probe_conf = _probe_conf(config)
    project_root = _project_root(config)
    ui_conf = config.get("ui") or {}
    browser_conf = ui_conf.get("browser") or {}

    validate_l1 = probe_conf["validate_l1"] and not args.no_validate_l1

    from playwright.sync_api import sync_playwright

    base_url = os.getenv(
        config.get("base_url_key") or "LILISHOP_UI_BASE_URL", "http://127.0.0.1"
    ).rstrip("/")
    route = args.route if args.route.startswith("/") else f"/{args.route}"

    with sync_playwright() as instance:
        browser = instance.chromium.launch(
            channel=browser_conf.get("channel") or None,
            headless=not args.headed,
        )
        context = browser.new_context(
            viewport=browser_conf.get("viewport") or {"width": 1440, "height": 900}
        )
        try:
            context.add_cookies(_login_cookies(project_root))
        except Exception as exc:
            print(f"[probe] 登录态注入失败，将以匿名身份探测: {exc}", file=sys.stderr)

        page = context.new_page()
        try:
            page.goto(f"{base_url}{route}", wait_until="domcontentloaded")
            page.wait_for_load_state("networkidle")

            steps_done: List[Dict[str, Any]] = []
            if args.steps:
                try:
                    steps_done = _run_steps(page, args.steps, probe_conf)
                except StepRejected as exc:
                    print(f"[probe][REJECT] {exc}", file=sys.stderr)
                    return EXIT_STEP_REJECTED

            frames = _frame_paths(page, probe_conf["max_frames"], probe_conf["max_frame_depth"])
            collected = _collect(page, probe_conf)
            elements = collected["elements"]

            rejected_l1 = 0
            for element in elements:
                element["suggested_level"] = _suggest_level(element)
                if validate_l1 and element["suggested_level"] == "L1":
                    reason = _validate_l1(page, frames, element)
                    if reason:
                        element["suggested_level"] = _degrade_level(element)
                        element["l1_rejected"] = reason
                        rejected_l1 += 1

            if args.explain:
                keyword = args.explain
                hits = [
                    e for e in elements
                    if keyword in (e.get("name") or "")
                    or keyword in (e.get("text") or "")
                    or keyword in (e.get("aria_label") or "")
                ]
                print(json.dumps(hits, ensure_ascii=False, indent=2))
                return 0

            if args.verify:
                with open(args.verify, "r", encoding="utf-8") as handle:
                    specs = yaml.safe_load(handle) or []
                today = date.today()
                rows = []
                for spec in specs:
                    candidates = [spec["primary"]] + list(spec.get("fallbacks") or [])
                    counts = []
                    frame_hits = []
                    for candidate in candidates:
                        total, per_frame = _count_all_frames(page, frames, candidate)
                        counts.append(total)
                        frame_hits.append(per_frame)
                    health = (
                        "ok" if counts and counts[0] == 1
                        else ("duplicate" if counts and counts[0] > 1 else "broken")
                    )
                    rows.append(
                        {
                            "id": spec.get("id"),
                            "primary_matches": counts[0] if counts else -1,
                            "fallback_matches": counts[1:],
                            "frame_hits": frame_hits[0] if frame_hits else {},
                            "health": health,
                            **_staleness(spec, today, args.stale_days),
                        }
                    )
                print(yaml.safe_dump(rows, allow_unicode=True, sort_keys=False))
                _report_staleness(rows, args.stale_days)
                return 0 if all(r["health"] == "ok" for r in rows) else 2

            payload = {
                "route": route,
                "url": page.url,
                "title": page.title(),
                "collected_at": date.today().isoformat(),
                "collect_version": COLLECT_VERSION,
                "steps": steps_done,
                "frames": collected["frames"],
                "frame_errors": collected["frame_errors"],
                "frame_truncated": collected["frame_truncated"],
                "truncated": collected["truncated"],
                "off_canvas_count": collected["off_canvas_count"],
                "shadow_hosts": collected["shadow_hosts"],
                "l1_rejected_count": rejected_l1,
                "elements": elements,
            }
            text = yaml.safe_dump(payload, allow_unicode=True, sort_keys=False)
            if args.out:
                out_path = args.out if os.path.isabs(args.out) else os.path.join(project_root, args.out)
                os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
                with open(out_path, "w", encoding="utf-8") as handle:
                    handle.write(text)
                print(
                    f"[probe] 已写入 {out_path}（{len(elements)} 个可见候选元素，"
                    f"{len(collected['frames'])} 个 frame，L1 降级 {rejected_l1} 条）"
                )
                if args.a11y:
                    a11y_path = os.path.splitext(out_path)[0] + ".a11y.yaml"
                    written = _write_a11y(page, a11y_path)
                    if written:
                        print(f"[probe] a11y 语义树已写入 {written}")
            else:
                print(text)
                if args.a11y:
                    a11y_path = os.path.join(
                        project_root, "ui_test_dwp_temp", "probe",
                        f"{_route_slug(route)}.a11y.yaml",
                    )
                    written = _write_a11y(page, a11y_path)
                    if written:
                        print(f"[probe] a11y 语义树已写入 {written}", file=sys.stderr)
            return 0
        finally:
            context.close()
            browser.close()


if __name__ == "__main__":
    sys.exit(main())
