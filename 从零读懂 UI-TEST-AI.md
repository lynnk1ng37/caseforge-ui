# 从零读懂 UI-TEST-AI

> 这份文档回答三个问题：**为什么这么设计**、**每一层在解决什么问题**、**代码该按什么顺序读**。
> 操作层面看 [`使用手册.md`](./使用手册.md)。

---

## 1. 先说清楚 UI 自动化的三个真问题

大多数 UI 自动化项目死在下面三件事上，而不是"不会写 Playwright"：

| 问题 | 典型表现 | 本项目怎么解 |
|---|---|---|
| **登录太贵** | 每条用例 UI 登录，遇滑块直接卡死；登录一次 10 秒 | 接口拿 token 注入 cookie（第 3 章） |
| **定位器散落** | 选择器写在用例里，页面改一个 class 红一片 | 定位器全部收敛为 YAML 资产（第 6 章） |
| **"绿"不可信** | 只断言"元素可见"、偶发时序问题被当通过 | 断言分层 + Web-First 断言 + 3 次连跑 + 独立验证者（第 7、9 章） |

这三件事决定了架构，不是反过来。

---

## 2. 一个贯穿全局的取舍：运行期零 LLM

业界流行"AI 定位"：运行时让模型看截图找元素。本项目**不做**：

| | 运行期 AI 定位 | 本项目 |
|---|---|---|
| 单次成本 | 每次执行都烧 token | 0 |
| 可复现 | 模型输出不稳定，同一条用例两次可能不同 | 确定性 |
| 失败可捕获 | 部分框架点击失败不抛异常 | 超时即失败 |
| 断链处理 | 靠模型猜 | 靠 registry + fallback 链 |

LLM 只在**建设期**（探测定位器、生成代码、疑难归因）和**验证期**（审查）出现。
这条取舍直接决定了：定位器必须是可持久化的资产，而不是"每次现场找"。

---

## 3. 登录：为什么用 API token 注入

### 3.1 原理

lilishop 买家端把 token 存在 **cookie** 里（键名 `accessToken` / `refreshToken`），
前端拦截器从存储读 token 塞进请求头。所以只要把 token 写进同域 cookie，前端就认为你已登录。

```
┌──────────────────┐  ① POST /buyer/passport/member/userLogin
│  ApiClient       │     form: username + md5(password)
│  (AuthHooks)     │     header: clientType=PC, uuid=<随机>
└────────┬─────────┘  ←──────────────────────────────────┐
         │ ② 拿到 accessToken / refreshToken              │
         ▼                                                │
┌──────────────────┐  ③ context.addCookies(...)           │
│ BrowserContext   │     domain = 127.0.0.1               │
│                  │     name   = accessToken              │
└────────┬─────────┘                                       │
         │ ④ navigate /home/MyOrder                        │
         ▼                                                 │
     直接是已登录状态 ──────────────────────────────────────┘
```

### 3.2 为什么不用 UI 登录

| 原因 | 说明 |
|---|---|
| 滑块 | 本地环境关闭了服务端校验，但其他环境没有；一旦遇到就要图像识别，不稳定 |
| 速度 | UI 登录约 5~10 秒；接口登录约 200ms |
| 脆弱 | 登录页改版会连带全部用例失败 |
| 职责 | 登录本身该由**登录用例**覆盖，不该成为所有用例的前置步骤 |

### 3.3 代码

```java
// hooks/AuthHooks.java
@Before(order = 1)
public void loginAndPrepareData() {
    api.login();                       // POST /buyer/passport/member/userLogin
    ctx.setApi(api);

    String host = URI.create(TestConfig.get().baseUrl()).getHost();
    ctx.context().addCookies(List.of(
            new Cookie("accessToken", api.accessToken()).setDomain(host).setPath("/"),
            new Cookie("refreshToken", api.refreshToken()).setDomain(host).setPath("/")
    ));

    api.clearCart();                   // 保证条目数与金额断言确定
}
```

`ApiClient` 用 JDK 自带 `HttpClient`，不引三方 HTTP 库；密码按前端逻辑做 md5 小写 32 位后提交。

### 3.4 换一个被测系统怎么复用这套思路

1. 打开页面，执行 `document.cookie` 与 `Object.keys(localStorage)`，看登录后多了什么；
2. 搜前端源码里的请求拦截器，确认 token 从哪读；
3. 存储位置决定注入方式：

| 存储 | 注入方式 |
|---|---|
| cookie | `context.addCookies(...)` |
| localStorage | `context.addInitScript("localStorage.setItem('k','v')")` |
| sessionStorage | 同上，但每个新标签页需重新注入 |

### 3.5 一个容易误判的现象

顶部导航一直显示「请登录」，但订单页能正常渲染。原因是前端用 `userInfo` cookie 渲染昵称，
缺它只是显示问题。**判断登录态要看业务页面，不要看顶部文案。**

---

## 4. 四层架构与断言边界

```
features/       场景层   中文 Gherkin：业务行为契约，不含实现细节
   ↓
steps/          用例层   步骤定义 + 业务断言（AssertJ）
   ↓
flows/          流程层   多页面编排，返回事实（交易单号 / 页面对象），不写最终断言
   ↓
pages/          页面层   单页面动作 + 就绪等待 + 读值，不写业务断言
   ↓
locators/       定位层   YAML 声明，唯一来源
```

### 4.1 为什么 page 层禁止断言

假设 `OrderListPage` 里写了 `assertThat(firstOrder).contains("未付款")`。
这条方法被 5 个场景复用，某天断言失败，你看到的是：

```
AssertionError: 订单状态不符
```

**看不出是哪个场景、哪个业务预期错了**。断言放在 steps 层，失败信息里带上期望值、实际值和当前 URL，才能定位。

所以 page 层只做三件事：**动作、等待、读值**。

### 4.2 为什么流程层要返回"事实"

```java
public PaymentPage submitOrder(String goodsId, String skuId) {
    addToCart(goodsId, skuId);
    cart.goCheckout();
    checkout.expectLoaded();
    checkout.submit();
    payment.expectSubmitted();
    return payment;          // 返回的是事实载体，不是断言结果
}
```

不吞异常、不返回布尔值、不替用例做判断。用例需要什么（交易单号、金额、订单号），flow 就返回什么。

---

## 5. Cucumber 放在哪里，为什么

Cucumber 有两种常见用法，本项目只取第一种：

| 用法 | 结果 | 本项目 |
|---|---|---|
| feature 描述业务行为，steps 承载实现与断言 | 业务方可读，工程细节不外泄 | ✅ |
| feature 里写"点击某按钮"、steps 里只剩一行转发 | 变成语法糖，维护成本翻倍 | ❌ |

所以 `.feature` 的判据是：**把这份文件给产品经理看，他能判断对错吗？**

```gherkin
@positive @LILI-ORDER-001
Scenario: 提交订单后订单出现在我的订单-待付款列表
  When 买家将商品加入购物车并提交订单
  Then 收银台应提示订单提交成功
  And 收银台交易单号应以 T 开头
```

对应的 steps 才是真正的"用例层"：它受门禁约束（禁裸定位、禁固定等待、必须有断言），
和传统 `@Test` 方法在职责上完全等价。

---

## 6. 定位器资产化

### 6.1 为什么是 YAML 而不是 Java 常量

| | YAML | Java 常量 |
|---|---|---|
| 巡检脚本读取 | 直接解析 | 需编译，受依赖影响 |
| 非开发人员修改 | 可行 | 容易改坏语法 |
| 记录元信息（verified_at 等） | 天然支持 | 需要额外结构 |
| 跨语言复用 | 天然 | 绑定 JVM |

登记表示例：

```yaml
- id: cart.checkout
  page: cart
  primary: {by: css, value: "div.pay", text: "去结算"}
  fallbacks:
    - {by: text, value: "去结算"}
  verified_at: "2026-11-22"
  verified_env: "local-127.0.0.1"
  source: probe
```

### 6.2 五级策略

```
L1 role + accessible name      ← 最稳，等价于用户语义
L2 testid / label / placeholder
L3 稳定业务属性（data-*）
L4 语义 class（限定父级）
L5 XPath                       ← 需注释豁免，默认被门禁拦
```

判据：**能不能被屏幕阅读器识别**。class 名含哈希（`_btn_3f2a9`）一律降级。

### 6.3 解析与自愈

`LocatorRegistry.resolve(page, key)`：

```java
for (int index = 0; index < candidates.size(); index++) {
    Locator locator = build(page, candidates.get(index));
    if (locator.count() > 0) {
        if (index > 0) {
            HealRecorder.record(key + ": primary 未命中，使用 fallback[" + index + "]");
        }
        return locator;
    }
}
return build(page, candidates.get(0));   // 全失败 → 返回 primary，让断言超时暴露
```

关键设计：**全部失败时不要返回空 locator**。返回 primary，让断言以超时失败，
失败信息里带候选链；返回空 locator 只会得到一句莫名其妙的 "element not found"。

自愈命中会写进两处：

- Allure 附件（人看）
- `ui_test_dwp_temp/heals_run<N>.jsonl`（`run_stability.py` 读，决定 PASS 还是 PASS_WITH_WARN）

### 6.4 启动期 fail-fast

定位器表在 `@BeforeAll` 一次性装载，并对以下情况直接抛异常：

- 文件不存在 / 格式不是列表
- 条目缺 `id`
- `id` 跨文件重复

理由：定位器表是全局契约，脏数据必须在第一秒炸掉，而不是在某个用例里随机挂。

---

## 7. Web-First 断言：为什么不用显式等待 + assert

对比三种写法（同一个"金额应为 3799.00"的校验）：

| 写法 | 模型 | 失败时的含义 |
|---|---|---|
| 取值后 `assertEquals` | 一次性快照 | 不知道是"还没渲染"还是"值真错" |
| `WebDriverWait` 等条件 + 再 assert | 等待与断言分离 | 等到了但没断言 → 静默假绿；等错元素 → 假红 |
| `assertThat(locator).hasText(...)` | 断言内建轮询 | 超时即真错，报错带实际值 |

关键差异：Web-First 断言的输入是 **Locator（查询意图）**，不是**已取出的值**。
所以重试的是"重新查 DOM + 重新判定"整个动作，而不是重试一个已经过期的字符串。

```java
// pages/CartPage.java —— 就绪等待
assertThat(items.first().or(emptyTip.first())).isVisible();
```

全局超时在 `BrowserHooks.globalSetup()` 对齐：

```java
PlaywrightAssertions.setDefaultAssertionTimeout(TestConfig.get().timeoutMs());
```

注意：`setDefaultTimeout`（作用于动作）与 `setDefaultAssertionTimeout`（作用于断言）是两套，
必须都设，否则会出现"动作等 15 秒、断言 5 秒就挂"的诡异现象。

---

## 8. 门禁：能机械判定的，绝不靠自觉

`check_ui_gate.py` 是纯文本规则引擎，规则唯一事实源是 `ui_gate_rules.json`。改规则不改代码。

| 编号 | 检查 | 在 Java 项目里的落点 |
|---|---|---|
| G1 | 目标文件不在 UI 目录 → 放行 | `config.json` 的 `paths` |
| G2 | 写用例前必须存在清单 | `ui_test_dwp_temp/design_checklist.md` |
| G3 | 清单已确认且有匹配指纹的口令日志 | `confirm_protocol.py` |
| G4 | 四类场景占比达标（≥4 条时校验） | 清单场景表 |
| G5 | 无`【待确认】` | 同上 |
| G6 | steps 层禁裸定位 / 固定等待 / 硬编码 URL / 自行解析定位器 | `page\.locator\(`、`Thread\.sleep\(`、`page\.navigate\(` |
| G7 | pages / flows 层禁业务断言、禁固定等待 | `import org.assertj`、`import org.junit`、`Assert\.` |
| G8 | 每个步骤方法至少一个断言 | `func_pattern` 匹配 `@Then/@And/public void` |
| G9 | 定位器禁裸 XPath（需豁免注释） | `locators/*.yaml` |
| G10 | 不同 id 的 primary 选择器重复 | 跨文件比对复合键 |

三个设计原则：

1. **fail-open**：脚本自身异常一律放行，门禁不能成为开发阻塞源；
2. **规则可覆盖**：`func_pattern` 放进 JSON，中文注解 `@那么` 与英文 `@Then` 只需改配置；
3. **不可静默**：缺少 PyYAML 这类依赖时，G10 会输出告警而不是假装通过。

G7 有一个必要的例外：pages 层允许 `PlaywrightAssertions.assertThat(locator).isVisible()`
做**就绪等待**，只禁 `org.assertj` / `org.junit` 的**业务断言**。规则用
`(?<!Playwright)Assertions\.assert` 把两者区分开。

---

## 9. 生成者-验证者闭环

写完代码不得自己宣布完成。

| 角色 | 谁 | 权限 | 产出 |
|---|---|---|---|
| 生成者 | 主 Agent（按 skill 七步） | 读写代码 | 场景 + steps + page + 定位器 + 自测结果 |
| 验证者 | `.codebuddy/agents/UI审查与执行Agent.md` | **只读 + 运行** | PASS / PASS_WITH_WARN / REJECT + 整改清单 |

验证者比人工自查多做的三件事：

1. **连跑 3 次**：单次通过不算通过，2/3 即判 flake；
2. **读自愈记录**：JSONL 里出现记录即降级为 `PASS_WITH_WARN`；
3. **反向覆盖定位器**：page 里用到的每个 id 必须在 YAML 里登记。

---

## 10. 线程模型：Playwright 不是线程安全的

`Playwright` / `Browser` 对象不能跨线程共享。本项目用 `ThreadLocal` 按线程持有：

```java
private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
```

启用并行时（`cucumber.execution.parallel.enabled=true`），每个线程各自 `Playwright.create()`，
`ScenarioContext` 由 PicoContainer 按场景隔离，`HealRecorder` 也是 `ThreadLocal`。

当前配置是单线程顺序执行 —— 因为下单链路会互相污染（购物车、限流窗口），
并行前必须先解决数据隔离。

---

## 11. 两个实测踩过的坑

| 现象 | 根因 | 解法 |
|---|---|---|
| 首次 `mvn test` 耗时 355 秒，日志里在下载 firefox/webkit | Playwright Java 首次 `create()` 会安装全套浏览器 | surefire 注入 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`（用系统 Chrome） |
| 加购偶发"点了没反应" | 后端对 `POST /buyer/trade/carts` 有短窗口限流，返回 `code=1003`，前端只弹 toast | `CartWriteRateLimiter.awaitSlot()`，集中管理、可配置、可关闭 |

---

## 12. 读代码顺序

按这个顺序读，每一层都能独立看懂：

1. `src/test/resources/features/order_purchase.feature` —— 先看业务要什么
2. `steps/OrderSteps.java` —— 断言在哪、断言什么
3. `flows/PurchaseFlow.java` —— 链路怎么串
4. `pages/CartPage.java` —— 单页动作与就绪等待
5. `locators/LocatorRegistry.java` —— 定位器怎么解析与自愈
6. `hooks/BrowserHooks.java` + `hooks/AuthHooks.java` —— 前置怎么来
7. `locators/cart.yaml` —— 最后看具体选择器

读完后你应该能回答：

- 为什么 `CartPage` 里没有 `assert`？
- 为什么 `LocatorRegistry` 全部未命中时返回 `primary` 而不是空？
- 为什么 `AuthHooks` 在 `BrowserHooks` 之后执行（`order = 1` / `order = 0`）？
- 为什么中文 Gherkin 生成的 `@那么` 注解也要被门禁识别？
