# 转写模式：人工用例 → UI 自动化

## 1. 全流程

```
读人工用例 → cases.yaml → 尽调 → 清单 → 确认 → 定位器 → page → flow → test → 运行 → 回写
```

## 2. 登录态：API 取 token + 注入 cookie（不处理滑块）

**这是本项目唯一允许的登录方式。不要尝试用图像识别或拖拽过滑块。**

已验证可行（2026-11-12 实测）：

1. 调用 `ApiClient.login()` 拿到 `accessToken` / `refreshToken`；
2. 打开浏览器上下文后，把 token 作为 **cookie** 注入 `127.0.0.1` 域；
3. 直接访问 `/home/MyOrder` 不再跳登录，订单数据正常渲染。

```java
ctx.context().addCookies(List.of(
        new Cookie("accessToken", api.accessToken()).setDomain(host).setPath("/"),
        new Cookie("refreshToken", api.refreshToken()).setDomain(host).setPath("/")
));
```

要点：
- lilishop 买家端把 token 存在 cookie（不是 localStorage），注入 `accessToken` + `refreshToken` 即可；
- 前端 axios 拦截器从存储读 token 并塞进请求头，因此 cookie 必须与页面同域；
- 登录用例本身（验证登录表单）可以走 UI，但同样建议先确认滑块已关闭；
- 演示站若未关闭滑块：用 `LILISHOP_FIXED_UUID` 复用人手过滑块后的 uuid，**不要自动化解滑块**。

## 3. 页面探测

```bash
# 单路由候选元素
python tools/probe_page.py --route /cart --out ui_test_dwp_temp/probe/cart.yaml
# 校验已有定位器
python tools/probe_page.py --verify locators/cart.yaml
```

探测规则（借自 dom-inspector 的实战经验）：

1. **在目标 UI 状态出现后抓取**，不是 goto 后立刻抓；
2. 可见性三档判定：`rect.x >= 0` + `computedStyle.display/visibility` + `opacity > 0`
   （`offsetParent` 对 `translateX(-9999px)` 的弹层无效）；
3. 表单控件优先取 `form-item` 内的 label，很多组件库 `placeholder` 为空；
4. 不唯一时输出父链（向上 6 层）找唯一锚点；
5. `input[type=file]` 常是 `sr-only`，不能按可见性过滤掉。

## 4. 数据前置三级策略

| 级别 | 方式 | 适用 |
|---|---|---|
| ① | 管理端/买家端 API 造数 | 首选，最快且可清理 |
| ② | DB 直改 | 无接口可造时（如订单状态） |
| ③ | 固定账号数据池 | 前两者都不行时，需在清单标注 |

**买家侧不可自造的前置**（如「待收货」需要商家发货）必须在清单里写清来源，
不可得则标 `【待确认】`，禁止用 `skip` 掩盖。

## 5. 生成顺序（不能反）

```
features/<x>.feature  →  locators/<page>.yaml  →  pages/<Page>.java  →  flows/<Flow>.java  →  steps/<X>Steps.java
```

先生成 page 会导致选择器内联，G7/G9 会拦，但返工成本更高。

## 6. 用例结构

一条人工用例 → 一个 `.feature` 场景 → 若干个 `@Then` 步骤方法（与人工步骤一一对应）。
人工用例的「期望」逐条映射成断言；映射不了的标 `【待确认】`。

## 7. 运行与归因

```bash
# 无头（默认，最快）
mvn -q test

# 需要看浏览器操作过程时
mvn -q test -Dui.headless=false -Dui.slowmo=300
```

`-Dui.headless` 与 `-Dui.slowmo` 由 `TestConfig` 读取；默认无头来自 `config.yaml`，
CI 不需要改代码，直接不加 `-Dui.headless=false` 即可。

| 归因 | 判据 | 修复 |
|---|---|---|
| 环境 | 连接失败 / 5xx / 静态资源 404 | 上报，不改代码 |
| 登录态 | 跳 `/login` | 重新 API 登录注入 cookie |
| 定位断链 | `TimeoutError` on locator | 只改 `locators` |
| 数据前置 | 列表空 / 无地址 / 无库存 | 修前置造数（`ApiClient` / `hooks`） |
| 断言不符 | 元素在但值不对 | 疑似 BUG，上报 |

**3 轮上限；第 2 轮起必须换策略**（改等待方式 / 换定位级别 / 拆步骤），
不允许同一策略重复试。
