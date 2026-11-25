---
name: ui-test-forge
description: 面向 Java + Playwright Java + Cucumber BDD 的 UI 自动化编写 skill，物理位置 .codebuddy/skills/ui-test-forge/。用于把人工编写的 P0/P1 测试用例转写为可维护的 UI 自动化用例（新增/维护/断言加固），也可按 URL 与页面语义探测定位器、生成 Page Object、通过 Maven 运行并按真实报错归因修复。触发词：UI 自动化、Playwright、Cucumber、BDD、页面元素探测、定位器、E2E、把测试用例转自动化。
---

# ui-test-forge

面向 `Java 17 + Playwright Java + Cucumber 7 + JUnit5 Platform` 的 UI 自动化编写 skill。核心主张：

1. **skill 是「人工用例 → 可执行契约」的编译器**，产物是定位器登记表 + Page Object + 场景步骤，不是裸脚本；
2. **LLM 只在建设期用**：探测定位器、生成代码、疑难归因；运行期零 LLM、零探测；
3. **门禁机械判定**：分层、禁用固定等待、禁裸定位、断言非空，由 `tools/check_ui_gate.py` 拦截。

## 🚨 前置门禁（硬性）

- 进入任务先读 `doc/preflight_gates.md`，确认任务类型：**转写 / 维护 / 断言加固**
- 写任何步骤代码前，必须先读 `doc/team/README.md` 的铁律与分层规范
- 清单确认口令：`确认，按照清单继续` 必须**独立成行**才能写代码；同一行带其它内容不算，只说「继续」「写吧」也不算（前后可以有其它说明行）
- 口令由 `hooks/confirm_log_hook.py` 连同清单指纹落盘到 `ui_test_dwp_temp/user_prompts.log`；
  `check_ui_gate.py` 的 G3 机械校验，AI 自行把 `确认状态` 写成「已确认」无效

## 任务类型分流

| 类型 | 适用 | 读取顺序 |
|---|---|---|
| 转写 | 人工 P0/P1 用例 → 自动化 | `doc/preflight_gates.md` → `doc/team/README.md` → `doc/mode_manual_convert.md` |
| 维护 | 定位断链、页面改版、断言失效 | `doc/preflight_gates.md` → `doc/team/README.md` → `doc/mode_maintenance_heal.md` |
| 断言加固 | 假绿、只断言元素可见 | `doc/team/assertion_layers.md` → `doc/mode_manual_convert.md` 第 5 节 |

## 七步流水线

```
0 入口门禁：识别任务类型 + 检查输入完整性
1 输入归一化：人工用例 → ui_test_dwp_temp/cases.yaml（只翻译，不补充）
2 可执行性尽调：环境可达 / 路由实测 / 登录态 / 数据前置 / 期望可观测
3 清单落盘：页面落点 + 定位器清单 + 场景四维 → 等用户确认
4 先定位器后代码：locators/*.yaml → pages → flows → steps → features
5 运行：mvn test，失败 3 轮上限，轮次≥2 必须换策略
6 归因：环境 / 登录态 / 定位断链 / 数据前置 / 断言不符
7 回写：locators 的 verified_at、docs/domain（业务规则 / 交互事实 / 已知缺陷）
```

## 工程路径（Java）

| 层 | 路径 |
|---|---|
| 场景 | `src/test/resources/features/*.feature`（**英文关键字** `Feature:`/`Scenario:`/`Background:`/`Given`/`When`/`Then`/`And`；步骤文本用中文。不要用 `# language: zh-CN`——IDE 插件只按英文关键字解析） |
| 定位器 | `src/test/resources/locators/*.yaml` |
| 路由 | `src/test/resources/routes.yaml` |
| 配置 | `src/test/resources/config.yaml` |
| Page | `src/test/java/com/lili/ui/pages/` |
| Flow | `src/test/java/com/lili/ui/flows/` |
| 用例层 | `src/test/java/com/lili/ui/steps/` |
| 基础设施 | `src/test/java/com/lili/ui/{locators,hooks,support,config}/` |

## 生成者-验证者闭环（硬性）

写完代码**不得自己宣布完成**，必须交给独立的 `UI审查与执行Agent` 审查并运行。验证者只读、不改代码，产出
`PASS / PASS_WITH_WARN / REJECT + 整改清单`；REJECT 则按清单回写，最多 3 轮。

| 改动类型 | 是否必须走验证者 |
|---|---|
| 新增场景 / 改断言 / 动分层 | **必须** |
| 只改 `locators/*.yaml` | 否：`probe --verify` + 该场景跑 1 次 |
| 只跑回归看红绿 | 否：直接 `mvn test` |

验证者的核心判据：

1. **连跑 3 次**识别 flake，2/3 通过也算 REJECT；
2. **自愈命中 = PASS_WITH_WARN**，不得当 PASS 上报（定位器已降级）；
3. 断言强度按 `doc/team/assertion_layers.md` 判，只有「元素可见」视为不合格。

统一稳定性命令（Agent 与人工共用）：

```bash
.venv/bin/python .codebuddy/skills/ui-test-forge/tools/run_stability.py --runs 3
# 退出码：0 PASS / 3 PASS_WITH_WARN / 2 REJECT_FLAKE / 1 REJECT_FAIL
```

## 如何执行

默认**无头模式**（`config.yaml` 的 `ui.headless` + `-Dui.headless`），所以看不到窗口。

| 目的 | 命令 |
|---|---|
| 默认无头（最快，CI 用） | `mvn -q test` |
| **看浏览器窗口** | `mvn -q test -Dui.headless=false` |
| 看窗口 + 慢放 | `mvn -q test -Dui.headless=false -Dui.slowmo=300` |
| 只跑某条场景 | `mvn -q test -Dcucumber.filter.tags="@LILI-ORDER-001"` |
| 只跑 P0 | `mvn -q test -Dcucumber.filter.tags="@p0"` |
| 只校验步骤是否齐全 | `mvn -q test -Dcucumber.execution.dry-run=true` |
| 生成报告 | `mvn test` 然后 `allure serve target/allure-results` |
| 回看失败过程 | `npx playwright show-trace ui_test_dwp_temp/artifacts/<场景名>.zip` |

说明：

- 浏览器用系统 Chrome（`config.yaml` 的 `ui.channel: chrome`）；`pom.xml` 已注入
  `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`，避免首次运行下载全套浏览器（实测会多花 5 分钟）。
- 想改用 Playwright 自带浏览器：删掉该校环境变量，执行
  `mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`，
  再把 `ui.channel` 置空。
- 场景结束会关闭窗口；想留时间观察用 `-Dui.slowmo`，不要加 `Thread.sleep`。

## 定位器策略（五级，不可越级）

| 级别 | 方式 | 示例 |
|---|---|---|
| L1 | role + accessible name | `{by: role, role: button, name: "加入购物车"}` |
| L2 | testid / label / placeholder | `{by: testid, value: "cart-checkout"}` |
| L3 | 稳定业务属性 | `{by: css, value: "[data-order-status='WAIT_PAY']"}` |
| L4 | 语义 class（限定父级） | `{by: css, value: "div.pay", text: "去结算"}` |
| L5 | XPath | 必须 `# allowed-xpath: 理由`，默认被 G9 拦截 |

定位器唯一来源是 `src/test/resources/locators/*.yaml`，page 层不得内联选择器，统一走
`LocatorRegistry.resolve(page, "<id>")`。

**探针产出的 L1 必须带 `l1_verified: true` 才能入库**：`--route` 采集时会对每个
`role`+`name` 候选做 `get_by_role(name, exact=True)` 回验，命中不为 1 的会被降级并在
`l1_rejected` 写明原因。没有回验的 L1 是「看起来对」，写进 yaml 后 `--verify` 会报
`primary_matches: 0`。回验由 `config.json` 的 `probe.validate_l1` 控制（默认开）。

## 代码模板（必须先读再写）

| 写什么 | 先读 |
|---|---|
| 定位器 | `doc/team/locator_policy.md` + `assets/templates/locators.yaml.tpl` |
| Page Object / Flow | `doc/team/pom_layers.md` + `assets/templates/page.java.tpl` |
| Cucumber 步骤 | `doc/team/assertion_layers.md` + `assets/templates/steps.java.tpl` |
| 场景描述 | `assets/templates/feature.tpl` |
| 清单 | `assets/templates/checklist.md.tpl` |

## 机械门禁（G1~G10）

| 编号 | 检查 | 实现 |
|---|---|---|
| G1 | 目标文件不在 UI 目录 → 放行 | `check_ui_gate.py` |
| G2 | 写用例前必须存在 `design_checklist.md` | 同上 |
| G3 | `确认状态=已确认` 且有匹配指纹的口令日志 | 同上 |
| G4 | 四类场景占比达标（≥4 条时校验） | 同上 |
| G5 | 无 `【待确认】` | 同上 |
| G6 | steps 层禁裸定位 / 禁自行解析定位器 / 禁固定等待 / 禁硬编码 URL | `ui_gate_rules.json` |
| G7 | pages·flows 层禁业务断言、禁内联选择器、禁固定等待 | 同上 |
| G8 | 每个步骤方法至少一个 `assertThat(` | 同上（`func_pattern` 支持中英注解） |
| G9 | 定位器文件禁裸 XPath（需豁免注释） | 同上 |
| G10 | 不同 id 的 primary 选择器复合键重复（`# allowed-dup:` 豁免） | 同上 |

规则唯一事实源：`ui_gate_rules.json`。改规则不改代码。

## 硬性铁律

1. 清单未确认不写步骤代码
2. 用例层（`steps/`）禁止 `page.locator` / `page.click` / `page.fill` / `page.navigate`，只能调 page object 或 flow
3. 禁止 `Thread.sleep` / `page.waitForTimeout`，用 Web-First 断言（自动重试）
4. 禁止硬编码 URL，统一走 `routes.yaml` + `TestConfig.baseUrl()`
5. 定位器断链只改 `locators/*.yaml`，**不得改断言**让它变绿
6. 业务断言只在 steps 层；pages / flows 层只能等待与取值
7. 同一场景调试 3 轮不通过必须停下，输出最后报错与已尝试策略

## 失败排查优先级

1. 环境不可达 / 前端 404 / `net::ERR_CONNECTION_REFUSED`
2. 登录态失效（跳 `/login`、接口 401）
3. 定位断链（`TimeoutError` on locator）
4. 数据前置不满足（空列表、无地址、无库存、加购限流 `code=1003`）
5. 断言不符（元素在但值不对，疑似 BUG，禁止放宽断言）

## 第三方依赖

| 依赖 | 用途 |
|---|---|
| `playwright`（Java） | 浏览器驱动；用系统 Chrome，无需额外下载 |
| 被测系统接口 | **UI 登录走接口取 token + 注入 cookie**，不处理滑块 |
| `allure-cucumber7-jvm` | 报告，结果输出到 `target/allure-results` |
| PyYAML | 门禁 / 探针脚本依赖；缺失时 G10 降级为告警 |

## 输出模板

```text
【转写/维护场景】(N 个)
场景名：中文说明 → 对应人工用例编号

【新增/维护 Page 方法】(N 个)
方法名：说明（复用 / 扩展 / 新建 / ⚠️修改）

【新增/修改步骤定义】(N 个)
方法名：对应 feature 步骤文案

Maven 执行命令
...

关键日志与失败归因
...

定位器变更（若有）
...
```
