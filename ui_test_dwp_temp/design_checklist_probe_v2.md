# 探针增强清单（probe_page.py v2）

> 唯一确认口令：`确认，按照清单继续`（必须独立成行，同一行混写其它内容不算）
>
> 本清单**不修改** `ui_test_dwp_temp/design_checklist.md`。那份清单的确认指纹仍在生效中，
> 一旦改动会让已确认的 53 条场景全部失效（G3 会拦），所以探针改造单独用本文件。

## 元信息

| 项 | 内容 |
|---|---|
| 任务类型 | 维护（工具增强，非用例转写、非断言加固） |
| 提出时间 | 2026-11-24 |
| 依据 | 用户指定「按模式 3 给出具体改动，模式 4 也做」 |
| 改动文件 | `.codebuddy/skills/ui-test-forge/tools/probe_page.py`（主）、`config.json`、`SKILL.md`、`使用手册.md`（文档同步） |
| 明确不动 | `src/test/resources/locators/*.yaml`、`src/test/java/**`、`src/test/resources/features/**`、`ui_test_dwp_temp/design_checklist.md` |
| 引入依赖 | 仅 `playwright`（Python，建设期工具用，不进 mvn 依赖树） |
| 不引入 | Stagehand、任何 MCP server、任何运行期 LLM |
| 参考对象 | Playwright MCP / Stagehand 的「a11y 穿透 + 交互后采集」思路，**只借思路不引依赖** |

## 一、现状与缺口（全部有代码/环境证据）

| 序号 | 缺口 | 证据 |
|---|---|---|
| 1 | 无 iframe 递归，iframe 内元素完全采不到 | `probe_page.py` 第 62–107 行 `COLLECT_JS` 只在 `document` 上 `querySelectorAll`；`main()` 第 277 行只有一次 `page.evaluate` |
| 2 | shadow DOM 只标记不穿透 | 第 104 行 `shadow_dom: el.getRootNode() !== document`，读到即止，不进 `shadowRoot` |
| 3 | 采不到「交互后」才出现的元素（弹层、折叠面板、Tab 内容） | `main()` 第 274–279 行只有 `goto → networkidle → evaluate`，没有任何点击入口 |
| 4 | `--verify` 对 iframe 内元素必然误报 `broken` | `_match_count` 第 149–169 行用 `page.get_by_*`，Playwright 的 `page` 级定位不跨 frame |
| 5 | `suggested_level: L1` 可能假阳性 | `_suggest_level` 第 110–119 行只凭 `role`+`name` 字段存在就判 L1，而 `name` 是第 71–78 行的 `innerText` 启发式，与 Playwright 真实 accessible name 算法不完全一致，写进 yaml 后 `get_by_role` 可能命中 0 或 >1 |
| 6 | 可见性判定会漏掉「在视口外但确实可见」的元素 | 第 67 行 `rect.x >= 0` 一刀切，横向滚动容器里的元素会被误杀；该判据本意是过滤 `translateX(-9999px)` 抽屉 |
| 7 | **探针当前在本机跑不起来** | 实测 `.venv/bin/python -c "import playwright"` → `ModuleNotFoundError`；`.venv/lib/*/site-packages` 只有 `yaml`。`probe_page.py` 第 253 行的 `from playwright.sync_api import sync_playwright` 会直接抛错 |

## 二、改动清单（模式 3 主体 + 模式 4 轻量版）

### A. iframe 递归（缺口 1、4）

| 项 | 内容 |
|---|---|
| 位置 | 新增 `_iter_frames(page)`、`_collect(page)`；改 `main()` 第 273–279 行；改 `_match_count` |
| 做法 | 遍历 `page.frames`（`frame.parent_frame` 推层级，深度上限 3），逐 frame `evaluate(COLLECT_JS)`；全局元素预算 200 条按帧分摊（主 frame 优先，保证旧行为不被挤掉） |
| 新增字段 | 每个元素加 `frame`（`top` / `iframe[1]/iframe[2]`）与 `frame_url` |
| 异常处理 | 单帧 `evaluate` 失败（跨域策略、frame 已 detach）只记录 `frame_errors[]` 并继续，不中断整次探测——与现有 `_login_cookies` 失败降级为匿名的 fail-open 口径一致 |
| 连带改动 | `_match_count` 改为跨 `page.frames` 求和，返回 `(total, per_frame)`；`--verify` 输出新增 `frame_hits`，`health` 判定仍只看 total |
| 兼容性 | 现有 11 个 yaml 的定位器全在顶层文档，`total` 不变 → 回归结果应与改动前逐条一致 |

### B. shadow DOM 穿透（缺口 2）

| 项 | 内容 |
|---|---|
| 位置 | 重写 `COLLECT_JS`（保留常量名，内部改递归），新增 `COLLECT_JS_VERSION = 2` 写入输出便于比对 |
| 做法 | `walk(root)`：先 `root.querySelectorAll(sel)` 收候选，再对每个元素 `if (el.shadowRoot) walk(el.shadowRoot)`；用 `depth` 控制最多 3 层，用 `Set` 去重避免同一节点被重复收 |
| 新增字段 | `in_shadow: bool`、`shadow_depth: int`、`shadow_host_chain: [...]`（最多 3 层 host 的 `tag + class` 截断 50 字符） |
| 为什么这样设计 | 落盘 yaml 要能让 AI 判断「这个元素在 shadow 里」，否则写出的 L1 定位器在 Java 侧 `get_by_role` 虽然能穿透（Playwright 的 role engine 默认穿透 open shadow root），但人会误以为是主文档元素，出问题时不知道该往哪儿查 |
| 限制 | 关闭的 shadow root（`attachShadow({mode:'closed'})`）无法进入，只能记 `closed_shadow: true` 提示人工 |

### C. `--steps` 白名单交互（缺口 3）

| 项 | 内容 |
|---|---|
| 参数 | `--steps` 可重复，语法 `click:<by>=<value>`，如 `--steps "click=text:筛选"`；另支持 `wait=networkidle`（只允许这一个等待值，不接受毫秒数） |
| 复用 | 抽出 `_resolve(page, spec) -> Locator`（`_match_count` 与步骤共用同一套 `by` 解析），保证「探到的」和「点的」是同一套语义 |
| 白名单 | 只允许 `click`；目标元素的可访问名必须命中 `probe.safe_hints`（默认：筛选/排序/展开/收起/更多/下一页/上一页/切换/页签/Tab/下拉/查看）；未命中即拒绝 |
| 黑名单 | 命中 `probe.danger_words`（默认：提交/支付/结算/购买/删除/清空/取消订单/确认收货/退出/领取/兑换/保存）直接拒绝，退出码 3，不执行 |
| 命中数校验 | 步骤目标 `count() != 1` 即失败退出码 3——多点一个和点错一个后果一样重 |
| 边界 | 每步执行后只做 `wait_for_load_state("networkidle")`，不接受任意 sleep；最多 5 步 |

### D. `--validate-l1` 回验（模式 4 的轻量实现，缺口 5）

| 项 | 内容 |
|---|---|
| 位置 | 新增 `_validate_l1(page, element)`，在 `_suggest_level` 之后调用 |
| 做法 | 对每个 `role`+`name` 候选，用 Playwright 自己的引擎回验：`page.get_by_role(role, name=name, exact=True).count()`（跨 frame 求和） |
| 判定 | 命中 1 → 保留 `L1` 并写 `l1_verified: true`；命中 0 或 >1 → 降级到 `L2`（有 testid）或 `L4`（有语义 class），写 `l1_rejected: reason`，**不再给出 L1 建议** |
| 为什么不用手写 ARIA 算法 | 要把 accessible name 计算规则（aria-labelledby → aria-label → label → alt → title → value → 文本递归）在 JS 里重写一遍，等于维护第二套引擎；直接用 Playwright 自己的实现回验，结果与 Java 侧 `LocatorRegistry` 的行为天然一致 |
| 成本 | 最多 200 次 `count()`，实测应在秒级；提供 `--no-validate-l1` 关闭 |
| 为什么值得做 | 这是当前最隐蔽的坑：探针给 L1，人信 L1，写进 yaml，`verified_at` 也更新了，但 `primary_matches: 0` |

### E. `--a11y` 语义树旁证（模式 4 补充，可选）

| 项 | 内容 |
|---|---|
| 参数 | `--a11y` |
| 做法 | 采集完成后输出 `page.locator("body").aria_snapshot()` 到 `<out 同目录>/<route>.a11y.yaml` |
| 用途 | 当候选表判不出「两个同名按钮哪个是主按钮」时，用语义树看层级与 role；这是 Playwright 内建能力，不联网、不用模型 |
| 默认 | 关闭（多一个产物文件，不默认打扰） |

### F. 可见性判定修正（缺口 6）

| 项 | 内容 |
|---|---|
| 做法 | 保留 `rect.x >= 0` 作为 `off_canvas` 的判定依据，但**不再直接 filter 掉**：改为 `visible = 基础可见性 && !off_canvas`，且把 `off_canvas: true` 的元素单独归到 `off_canvas_elements[]`（不进主列表、不占 200 预算） |
| 好处 | 横向滚动容器里的元素不再被静默丢弃；`translateX(-9999px)` 抽屉仍然被隔离 |
| 兼容性 | 主 `elements` 列表内容不变（原判据是 `rect.x>=0` 才进，现在仍是） |

### G. 配置与文档

| 文件 | 改动 |
|---|---|
| `config.json` | 新增 `probe` 段：`max_elements: 200`、`max_frames: 8`、`max_frame_depth: 3`、`max_steps: 5`、`safe_hints`、`danger_words`、`validate_l1: true` |
| `SKILL.md` | 第 104–115 行定位器策略下方补一句：探针产出的 L1 必须带 `l1_verified: true` 才可入库 |
| `使用手册.md` | 6.2 探针工具章节补 `--steps` / `--a11y` / `--validate-l1` 三个用法与安全边界 |
| `probe_page.py` docstring | 第 9–15 行的用法块同步 |

## 三、安全边界（硬约束，写进代码注释）

1. `--steps` 只允许 `click`，且必须同时通过白名单与不命中黑名单，否则退出码 3、不执行；
2. 不引入任何网络请求（除被测站点本身），不引入 LLM、不引入 MCP；
3. 输出只含裁剪后的候选字段，不 dump 整页 HTML，不输出 cookie / token；
4. 默认无头；`--headed` 仅用于人工观察；
5. 探针失败一律 fail-open（记录 `frame_errors` / `l1_rejected` 后继续），不能因为一帧或一个元素让整次探测归零。

## 四、验证方式

| 序号 | 验证项 | 通过标准 |
|---|---|---|
| 0 | 安装依赖 `.venv/bin/pip install playwright` | `import playwright` 成功（当前实测失败） |
| 1 | 改动前先跑基线：对 11 个 `locators/*.yaml` 逐个 `--verify`，结果落 `ui_test_dwp_temp/probe/baseline_verify.yaml` | 基线落盘成功（当前无 `probe/` 目录） |
| 2 | 改动后重跑同一批 `--verify` | 每条 `primary_matches` 与基线完全一致；`frame_hits` 为新增字段 |
| 3 | `--route /cart --out` 前后对比 | 旧字段（`index/tag/role/name/...`）逐条一致，仅新增字段 |
| 4 | `--steps "click=text:提交订单"` | 被黑名单拒绝，退出码 3，页面无跳转 |
| 5 | `--steps "click=text:筛选"`（找得到该控件的页面） | 执行成功后再采集，元素数 ≥ 交互前 |
| 6 | iframe 页面实跑 | 若环境无 iframe 页面，标记「未验证」并如实上报，不声称通过 |
| 7 | `--validate-l1` 跑 cart / goods_detail | 记录被降级的 id；与 `--verify` 的 `health` 口径对齐（降级后不应再出现 `suggested_level: L1` 却 `broken`） |

## 五、不做什么

| 不做 | 原因 |
|---|---|
| 改 `ui_test_dwp_temp/design_checklist.md` | 会让 53 条已确认场景的指纹失效 |
| 动任何 `locators/*.yaml` | 本次只改工具；定位器变更必须另起维护流程并发 `verified_at` |
| 动 Java 侧代码 | 探针是建设期工具，与运行期无关 |
| 引入 Stagehand / MCP | 需要模型与云端 key，且产出 XPath（L5，被 G9 拦），与本项目登记表不兼容 |
| 在探针里做断言 | 探针只采集，断言属用例层 |

## 六、待确认事项

| 序号 | 事项 | 需要你定的 |
|---|---|---|
| 1 | 依赖安装 | 允许在 `.venv` 里 `pip install playwright` 吗（仅建设期工具，不进 mvn 依赖树） |
| 2 | 规则配置位置 | 白名单/危险词放 `config.json` 的 `probe` 段（推荐，改规则不改代码），还是内置在脚本里 |
| 3 | `--validate-l1` 默认值 | 默认开启（探针变慢，最多 200 次 count）还是默认关闭、显式加参数 |
| 4 | frame 深度 | 3 层够用吗（超过即停止并记 `frame_truncated: true`） |
| 5 | `--a11y` | 是否要这个可选产物（多一个 `.a11y.yaml`，方便判同名按钮） |
| 6 | 是否同步改 SKILL.md | 改文档会让已确认清单的指纹失效吗——不会（指纹只覆盖 `design_checklist.md`），但会让行为约定变化，需你认可 |

## 用户确认记录

| 项 | 内容 |
|---|---|
| 确认状态 | 已确认 |
| 确认时间 | 2026-11-24 12:42 |
| 用户回复摘要 | 模式4按照 playwright 自己的引擎验证，再加 --a11y；清单第一条允许，第二条按建议（放 config.json 的 probe 段） |
| 确认日志 | `ui_test_dwp_temp/user_prompts.log` 第 3 条，时间戳 `2026-11-24T12:42:03+08:00` |
| 指纹说明 | hook 只对 `design_checklist.md` 计算指纹（`aad1445b9fc8…`），本文件不在 hook 管辖范围。本清单只改探针工具，目标文件在 `.codebuddy/skills/` 下，属 G1「不在 UI 自动化目录 → 放行」，不涉及用例层门禁 |

## 实现回填（2026-11-24）

| # | 项 | 实际结果 |
|---|---|---|
| 0 | 依赖 | `.venv` 安装 playwright 1.65.2 成功（此前 `ModuleNotFoundError`） |
| 0b | 额外修复：登录注入 | 原 `_login_cookies` 依赖不存在的 `api.auth_api`，异常被吞导致**一直是匿名探测**。改为标准库直连 `/buyer/passport/member/userLogin`（md5 密码 + `clientType`/`uuid` 头），实测登录成功；`/home/MyOrder` 带 cookie 有 10 张订单卡，匿名 0 张 |
| A | iframe 递归 | 新增 `_frame_paths` / `_collect`，元素带 `frame` / `frame_url`；单帧失败记 `frame_errors` 不中断 |
| D | 跨 frame 校验 | `_count_all_frames` 替代 `page.get_by_*` 直查；verify 输出新增 `frame_hits` |
| B | shadow 穿透 | `COLLECT_JS` 重写为递归 walk，带 `in_shadow` / `shadow_depth` / `shadow_host_chain`；`collect_version: 2` |
| C | `--steps` | 支持 `click=<by>:<value>` 与 `wait=networkidle`；白名单 + 危险词 + 命中数必须为 1，不满足退出码 3 |
| D2 | L1 回验 | `--validate-l1` 默认开，`_validate_l1` 用 `get_by_role(name, exact=True)` 回验；命中≠1 降级并写 `l1_rejected` |
| E | `--a11y` | `page.locator("body").aria_snapshot()` 写入 `<out>.a11y.yaml`，实测 2254 字节产物正常 |
| F | 可见性 | 保留原 `rect.x>=0` 判据不变；负偏移元素改记 `off_canvas_count`，不再静默丢弃 |
| G | 配置/文档 | `config.json` 新增 `probe` 段；`SKILL.md` 补 L1 回验要求；`使用手册.md` 6.2 重写 |

### 回归证据

| 验证 | 结果 |
|---|---|
| v1/v2 采集等价（11 个路由，比对 `tag+id+cls+text` 多重集） | **11/11 完全一致**，无元素增删 |
| 计数语义等价（`page.get_by_*` vs 跨 frame 求和，覆盖 11 个 yaml 全部条目） | **全部一致**，无一条 count 变化 |
| `--verify` 落盘 | `ui_test_dwp_temp/probe/verify_*.yaml` 11 份 |
| 危险词拦截 | `click=text:清空购物车` → `命中危险词「清空」`，退出码 3 |
| 白名单外拦截 | `click=css:.ivu-btn` → 命中 2 个，退出码 3 |
| 非法 wait | `wait=3000` → 拒绝，退出码 3 |
| 合法交互 | `--route /goodsList --steps "click=text:价格"` → 成功，采到 12 个元素 |
| L1 回验生效 | goodsList 上 `a '购物车（0）'` 被判定 `get_by_role(name, exact=True) 命中 0` → 降级 L5。该元素正是原来会误报 L1 的典型 |

### 夹具补验（2026-11-24，静态页 `ui_test_dwp_temp/probe_fixture/`，`python -m http.server 8899`）

被测站点没有 iframe 也没有 shadow DOM（11 个路由全 `frames=1`、`shadow_hosts=0`），
所以另造静态夹具补验，覆盖：主文档 / 一级 iframe / 二级 iframe / 开放 shadow 深度 1、2 /
closed shadow 反例 / 离屏抽屉 / `display:none` 隐藏元素。

| 验证 | 结果 |
|---|---|
| frame 递归 | `frames = ['top', 'iframe[0]', 'iframe[0]/iframe[0]']`，深层按钮带 `frame: iframe[0]/iframe[0]` |
| shadow 穿透 | `shadow_depth=1` 的「影子按钮」、`shadow_depth=2` 的「深层影子按钮」均采到，`shadow_host_chain` 为 `['x-outer.','x-inner.']` |
| closed shadow | 「关闭影子按钮」按设计采不到（`ShadowRoot.host` 不可达），不是缺陷 |
| 离屏抽屉 | `translateX(-9999px)` 的按钮不计入候选，`off_canvas_count=1` |
| 隐藏元素 | `display:none` 按钮被正确排除 |
| 跨 frame 校验 | `#frame-btn` → `frame_hits: {iframe[0]: 1}`；`#frame2-btn` → `{iframe[0]/iframe[0]: 1}`；role 定位同样命中 iframe |
| 旧写法对照 | 同一元素 `page.locator("#frame2-btn").count() = 0`，跨 frame 求和 `= 1`（证明 D 项修复的是真实缺陷） |
| closed shadow 反例 | `role button name 关闭影子按钮` → `primary_matches: 0`、`health: broken`（符合预期） |
| 跨 shadow 点击 | `--steps "click=text:展开影子内容"` 成功，`steps` 记录 `frame: top` |
| 跨 iframe 点击 | `--steps "click=text:展开详情"` 成功，`steps` 记录 `frame: iframe[0]` |
| shadow 内非白名单 | `--steps "click=css:#shadow-btn"` → `未命中交互白名单`，退出码 3 |
| L1 回验 | 夹具 9 个元素全为 L1 且 `l1_verified: true`（`get_by_role` 能穿透 open shadow，与 Java 侧行为一致） |

### 遗留

| 项 | 说明 |
|---|---|
| `--verify` 大量 broken | 属既有状态（`/goodsDetail`、`/home/OrderDetail`、`/payment` 需要业务参数，直接开路由本就拿不到元素），与本次改动无关——等价性测试已证明计数语义未变 |
| 夹具页 | `ui_test_dwp_temp/probe_fixture/` 为临时产物，可随时删除；删了不影响探针 |
