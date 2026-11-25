# caseforge-ui

把「人工用例 → 可执行 UI 自动化」做成一条**带门禁的流水线**：输入人工用例，输出
**定位器 YAML / Page Object / BDD 场景**三层产物；写码时有机械门禁拦截违规写入，
写完后由**只读**验证 Agent 连跑三次判 flake。
技术栈：`Java 17 + Playwright Java + Cucumber BDD + JUnit5 Platform`。

> 🎬 **3 分钟演示**：【待补：视频链接】 ｜ 📊 **最近一次执行报告**：【待补：gh-pages 链接】
> 📖 **设计取舍**：[从零读懂 UI-TEST-AI.md](从零读懂%20UI-TEST-AI.md) ｜ 上手：[使用手册.md](使用手册.md)
> ⚠️ **边界**：LLM 只在**建设期**参与（探测、给候选、生成草案），**运行期零 LLM、零探测**——
> 回归每天都要跑，不能有随机性，也不能每次烧 token。

## 它解决什么问题

| 痛点 | 做法 |
|---|---|
| 用例转写是纯体力 | 用例 → 三层产物，机械的部分交给生成流程 |
| 定位器一改版就断 | 选择器全部收敛到 YAML，用例层看不到选择器，改版只改一处 |
| 断言只断元素存在＝假绿 | 业务断言强制落在 steps 层，页面层不许写业务断言 |

## 设计决策（含被否决过的方案）

| 决策 | 否决了什么 | 为什么 |
|---|---|---|
| 定位器全部收敛到 YAML | 直接写在 Page 里 | 改版时改 1 处 YAML，而不是改 30 处代码 |
| 业务断言只在 steps 层 | Page 层写业务断言 | 同一个 Page 方法被多条用例复用，断言写在里面，挂了不知道是谁的预期错 |
| 登录走接口注入 cookie | UI 登录 | 滑块不可自动化；单条用例登录从约 10s 降到约 200ms |
| 验证 Agent 只读、无写权限 | 生成者自检 | 生成者自己宣布完成，等于没有验证 |
| 门禁 fail-open | 门禁异常即阻断 | 护栏坏了不能把人锁在门外；代价是门禁自身故障时漏放，由只读验证者兜底 |

> 作者：lynnk1ng（[github.com/lynnk1ng37](https://github.com/lynnk1ng37)） · 2026.07 – 至今 · 基于 lilishop 开源电商项目实践

---

## 快速开始（5 分钟）

```bash
cd caseforge-ui

# 1. 门禁工具链依赖（Python 仅用于静态门禁，不参与测试执行）
python3 -m venv .venv && .venv/bin/pip install pyyaml

# 2. 编译
mvn -q -DskipTests test-compile

# 3. 跑全量（默认无头，用系统 Chrome）
mvn -q test
```

预期（默认排除 5 条已知缺陷）：

```
[INFO] Tests run: 47, Failures: 0, Errors: 0, Skipped: 5
[INFO] BUILD SUCCESS
```

> **47 条里为什么跳过 5 条**：当前 lilishop 构建上有 4 个 UI 缺陷，对应 5 条原始断言故意保持红色，
> 默认用 `@known-issue` 标签跳过，避免红着的流水线让人最后把门禁关掉。
> 单独跑这 5 条看它们怎么红：`mvn test -Dcucumber.filter.tags="@known-issue"`
> 详见 [已知问题](#已知问题5-条红色的原因)。

看浏览器实际操作过程：

```bash
mvn -q test -Dui.headless=false -Dui.slowmo=300
```

> 不用系统 Chrome 时：删掉 `pom.xml` 里 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD`，
> 执行 `mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`，
> 再把 `config.yaml` 的 `ui.channel` 置空。

---

## 环境要求

| 项 | 说明 |
|---|---|
| JDK | 17 及以上（`maven.compiler.release=17`） |
| Maven | 3.9+ |
| 浏览器 | 系统 Chrome（`config.yaml` 的 `ui.channel: chrome`） |
| 被测前端 | lilishop 买家端，默认 `http://127.0.0.1` |
| 被测接口 | 默认 `http://127.0.0.1/api` |
| 测试账号 | lilishop 演示账号 `kk1024`（密码通过 `LILISHOP_PASSWORD` 传入，需有默认收货地址） |
| Python + PyYAML | 仅门禁/巡检脚本需要；缺失时 G10 会降级为告警 |

环境变量（优先级高于 `config.yaml`）：

| 变量 | 作用 |
|---|---|
| `LILISHOP_UI_BASE_URL` | 前端地址 |
| `LILISHOP_USERNAME` / `LILISHOP_PASSWORD` | 演示账号；`LILISHOP_PASSWORD` **必填**，探针与门禁都依赖它 |
| `LILISHOP_BASE_URL` | 接口地址 |
| `UI_HEADLESS` | `0` 显示窗口 |
| `UI_TIMEOUT_MS` | 默认等待超时 |
| `UI_TEST_GOODS_ID` / `UI_TEST_SKU_ID` | 可下单商品 |
| `UI_CART_WRITE_INTERVAL` | 加购限流窗口，秒；`0` 关闭 |

---

## 目录结构

```
caseforge-ui/
├── src/test/
│   ├── java/com/lili/ui/
│   │   ├── RunCucumberTest.java        Cucumber 执行入口（@Suite）
│   │   ├── config/TestConfig.java      配置单一事实源 + 环境变量覆盖
│   │   ├── locators/                   LocatorRegistry / RouteRegistry / HealRecorder
│   │   ├── pages/                      页面对象：动作 + 就绪等待 + 读值
│   │   ├── flows/                      流程编排：加购 → 结算 → 提交
│   │   ├── steps/                      用例层：步骤定义 + 业务断言
│   │   ├── hooks/                      BrowserHooks（trace/自愈）/ AuthHooks（登录注入）
│   │   └── support/                    ApiClient / ScenarioContext / 限流护栏
│   └── resources/
│       ├── features/                   中文 Gherkin 场景
│       ├── locators/                   定位器登记表（YAML）
│       ├── routes.yaml                 路由唯一来源
│       └── config.yaml                 运行期配置
├── .codebuddy/
│   ├── skills/ui-test-forge/           七步流程 + 机械门禁 + 探针
│   ├── agents/UI审查与执行Agent.md     只读 + 运行的独立验证者
│   └── settings.json                   三个 hook 的注册
├── ui_test_dwp_temp/                   清单、trace、自愈日志
├── pom.xml
├── 使用手册.md
└── 从零读懂 UI-TEST-AI.md
```

---

## 常用命令

| 目的 | 命令 |
|---|---|
| 全量 | `mvn -q test` |
| P0 冒烟 | `mvn -q test -Dcucumber.filter.tags="@p0"` |
| 只跑某条用例 | `mvn -q test -Dcucumber.filter.tags="@LILI-ORDER-001"` |
| 看窗口 | 追加 `-Dui.headless=false` |
| 慢放 | 追加 `-Dui.slowmo=300` |
| 只校验步骤齐全 | `mvn -q test -Dcucumber.execution.dry-run=true` |
| 稳定性（3 次） | `.venv/bin/python .codebuddy/skills/ui-test-forge/tools/run_stability.py --runs 3` |
| 门禁自检 | `.venv/bin/python .codebuddy/skills/ui-test-forge/tools/check_ui_gate.py --selftest` |
| 定位器巡检 | `.venv/bin/python .codebuddy/skills/ui-test-forge/tools/probe_page.py --verify src/test/resources/locators/cart.yaml` |
| 回看失败 | `npx playwright show-trace ui_test_dwp_temp/artifacts/<场景名>.zip` |
| 看报告 | `mvn test` 后直接双击 `target/cucumber-report.html` |

`run_stability.py` 退出码：`0 PASS` / `3 PASS_WITH_WARN` / `2 REJECT_FLAKE` / `1 REJECT_FAIL`。

---

## 怎么看报告（失败用例定位）

跑完 `mvn test`，用浏览器**直接打开 `target/cucumber-report.html`**——单文件，不用装任何东西。

```bash
mvn -q test -Dcucumber.filter.tags="@known-issue"   # 跑那 5 条失败用例
open target/cucumber-report.html                    # macOS
```

展开任意一条失败用例，结构是这样：

```
场景: 加购单商品后列表与角标正确
├── Given 买家已通过接口登录并注入登录态      passed
├── Given 买家购物车为空                      passed
├── When 买家从商品详情页加购测试商品 1 件    passed
└── Then 购物车角标应显示 1 件商品            failed   ← 红色的就是断点
      Hook:
        ├── 失败截图.png                  失败瞬间的浏览器视口
        ├── 失败时 DOM.html               失败瞬间的完整 DOM
        ├── 失败现场.txt                  场景 / 状态 / URL / 标题
        └── playwright trace 路径.txt     回放命令
```

**附件为什么在 Hook 分组里，而不是红色步骤下面**：Cucumber 把 `@AfterStep` 里 attach 的内容
记在该 hook 行下。要精确挂到 Gherkin 步骤本身，只能在每个 step 定义内部 attach，
代价是每个 step 包一层 try/catch——收益（少缩进一级）不值这个成本，所以接受 Hook 行的落点。

**为什么用自带报告而不是 Allure**：Cucumber 的 HTML formatter 本身就支持步骤级状态与附件内嵌，
一个文件、零依赖、双击就能看。Allure 多出来的是趋势与重跑对比，那部分用 `target/cucumber.json`
算一下就够了，不值得为它引入插件和 CLI。

**trace 不内嵌**：单个 trace 1~3 MB，塞进 HTML 会把报告撑到几十兆，所以报告里只写回放路径：

```bash
npx playwright show-trace ui_test_dwp_temp/artifacts/<场景名>.zip
```

---

## Jenkins / CI

`Jenkinsfile` 的 `post { always }` 已经包含三件事：

| 步骤 | 作用 |
|---|---|
| `junit 'target/surefire-reports/*.xml'` | 把 Cucumber 的 JUnit XML 喂给 Jenkins，画出历史趋势 |
| `archiveArtifacts 'target/cucumber-report.html'` | 每次构建归档单文件报告，在 Jenkins 里点开就能看失败现场 |
| `archiveArtifacts`（trace / 自愈日志） | 保留失败回放与自愈记录 |

> ⚠️ **Jenkinsfile 未在 CI 实例上实机验证。** 本地验证到的范围只有：
> `mvn test` 能产出 `target/cucumber-report.html`，且报告里能看到步骤与失败附件。
> `jenkins/` 下的容器与流水线**没有**在真实 Jenkins 上完整跑过一次。
> 首次接入请按 [`jenkins/README.md`](jenkins/README.md) 起容器自查，不要默认它是对的。

### 发布报告到 gh-pages（可选）

报告就是一个 HTML 文件，直接推到 `gh-pages` 分支即可：

```bash
mvn -q test
mkdir -p /tmp/report && cp target/cucumber-report.html /tmp/report/index.html
touch /tmp/report/.nojekyll
cd /tmp/report && git init -q && git add -A && git commit -qm "report" \
  && git push -f git@github.com:lynnk1ng37/caseforge-ui.git HEAD:gh-pages
```

之后在 README 顶部挂上 `https://lynnk1ng37.github.io/caseforge-ui/` 即可。
这一步**没在本仓库执行过**（需要凭据），首次使用请先确认自己有 push 权限。

## 现有用例

全量 47 条，覆盖首页搜索、商品详情、购物车、订单中心、下单支付、会员资产 6 个模块。
其中 42 条绿、5 条红（原因见下节）。稳定性连跑 3 轮，失败集完全一致——**是确定性红，不是 flake**。

## 已知问题（5 条红色的原因）

当前 lilishop 构建上有 4 个 UI 缺陷，对应 5 条原始断言**故意保持红色**，按 BUG 编号记录在
[`ui_test_dwp_temp/design_checklist.md`](ui_test_dwp_temp/design_checklist.md)：

| 现象 | 受影响用例 |
|---|---|
| 加购后顶部角标不刷新（显示「购物车（0）」） | LLS-CART-001 |
| 游客访问购物车不跳登录，停在 /cart | LLS-CART-010 |
| 详情页加入购物车 / 立即购买入口不可用 | LLS-GOODS-006 |
| 顶部入口点击后未跳转（停在 /） | LLS-HOME-002 |
| 秒杀页不展示「限时秒杀」文案 | LLS-HOME-010 |

处理方式：

1. 这 5 条一律标 `@known-issue`，默认执行排除，避免红着的流水线让人把门禁关掉；
2. **不删断言、不改断言**让它变绿；
3. 另写 `bug_blocked_cart_home.feature` 锁定「缺陷存在时仍应成立」的行为，避免回归时完全失去覆盖。

---

## 下一步

- 跑起来、修用例：读 [`使用手册.md`](./使用手册.md)
- 理解设计取舍：读 [`从零读懂 UI-TEST-AI.md`](./从零读懂%20UI-TEST-AI.md)
- 新增用例：走 skill 的七步流程（清单 → 确认口令 → 定位器 → page → steps）
