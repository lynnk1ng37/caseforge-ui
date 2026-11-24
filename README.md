# UI-TEST-AI-FOR-JAVA

面向 lilishop 买家端的 **UI 自动化框架**：`Java 17 + Playwright Java + Cucumber BDD + JUnit5 Platform`。

核心主张：

1. **定位器是资产，不是代码细节** —— 全部收敛到 `src/test/resources/locators/*.yaml`，页面层不写选择器；
2. **登录不走 UI** —— 接口拿 token 注入 cookie，彻底绕开滑块；
3. **场景可读、断言可查** —— 中文 Gherkin 描述业务行为，断言全部落在 steps 层并带上下文；
4. **写用例有门禁，写完有验证** —— 清单未确认不能写代码；`UI审查与执行Agent` 连跑 3 次判 flake，自愈命中不算通过。

---

## 快速开始（5 分钟）

```bash
cd /Users/lynnking/CodeBuddy/UI-TEST-AI-FOR-JAVA

# 1. 门禁工具链依赖（Python 仅用于静态门禁，不参与测试执行）
python3 -m venv .venv && .venv/bin/pip install pyyaml

# 2. 编译
mvn -q -DskipTests test-compile

# 3. 跑全量（默认无头，用系统 Chrome）
mvn -q test
```

预期：

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

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
| 测试账号 | 默认 `kk1024 / 111111`（需有默认收货地址） |
| Python + PyYAML | 仅门禁/巡检脚本需要；缺失时 G10 会降级为告警 |

环境变量（优先级高于 `config.yaml`）：

| 变量 | 作用 |
|---|---|
| `LILISHOP_UI_BASE_URL` | 前端地址 |
| `LILISHOP_BASE_URL` | 接口地址 |
| `UI_HEADLESS` | `0` 显示窗口 |
| `UI_TIMEOUT_MS` | 默认等待超时 |
| `UI_TEST_GOODS_ID` / `UI_TEST_SKU_ID` | 可下单商品 |
| `UI_CART_WRITE_INTERVAL` | 加购限流窗口，秒；`0` 关闭 |

---

## 目录结构

```
UI-TEST-AI-FOR-JAVA/
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
| 生成报告 | `mvn test` 后 `allure serve target/allure-results` |

`run_stability.py` 退出码：`0 PASS` / `3 PASS_WITH_WARN` / `2 REJECT_FLAKE` / `1 REJECT_FAIL`。

---

## 现有用例

| 用例编号 | tag | 场景 | 验证点 |
|---|---|---|---|
| LILI-ORDER-001 | `@p0` | 提交订单后订单出现在我的订单-待付款列表 | 收银台成功文案、交易单号 T 开头、待付款标签选中、首条订单含商品名与「未付款」、接口回查命中 |

---

## 下一步

- 跑起来、修用例：读 [`使用手册.md`](./使用手册.md)
- 理解设计取舍：读 [`从零读懂 UI-TEST-AI.md`](./从零读懂%20UI-TEST-AI.md)
- 新增用例：走 skill 的七步流程（清单 → 确认口令 → 定位器 → page → steps）
