# UI 断言分层

UI 断言比接口断言更容易「假绿」，因为元素可见 ≠ 业务正确。
本文件规定每条用例最少断言什么、禁止什么。

## 1. 四个断言层次

| 层次 | 断言对象 | 强度 | 用途 |
|---|---|---|---|
| L1 页面态 | URL、标题、元素可见/隐藏 | 最弱 | 只证明「跳到了这个页面」 |
| L2 数据态 | 列表条数、单元格文本、金额、状态标签 | 中 | **默认要求**，证明页面展示了正确数据 |
| L3 状态机 | 订单状态、tab 选中态、按钮可用性 | 中强 | 证明业务状态正确 |
| L4 一致性回查 | 写操作后回查列表/详情/接口 | 最强 | 写操作**强制** |

**每条用例至少 L2 + 一个 L3 或 L4。** 只写 L1（仅 `assertThat(locator).isVisible()`）视为不合格。

断言写在 `steps/` 层，用 AssertJ；`pages/` 层只允许 `PlaywrightAssertions` 做就绪等待。

## 2. 各类型用例的最低断言

### 2.1 列表 / 筛选页

```java
// ✅ 选中态 + 条数 + 每条数据符合条件 + 空态未出现
assertThat(orderList.activeTabText())
        .as("待付款标签未选中, url=%s", ctx.currentUrl())
        .isEqualTo("待付款");

assertThat(orderList.orderCount())
        .as("待付款列表为空, url=%s", ctx.currentUrl())
        .isGreaterThanOrEqualTo(1);

assertThat(orderList.orderTexts())
        .as("存在状态不符的订单, url=%s", ctx.currentUrl())
        .allMatch(text -> text.contains("未付款"));
```

### 2.2 表单 / 提交页

```java
// ✅ 提交后：成功文案 + 落库回查
assertThat(payment.successTipText())
        .as("收银台成功文案不符, url=%s", ctx.currentUrl())
        .contains("订单提交成功");

String orderSn = ctx.createdOrderSns().get(0);
List<JsonNode> records = ctx.api().pageOrders("WAIT_PAY");
assertThat(records)
        .as("订单未出现在待付款, sn=%s", orderSn)
        .anyMatch(record -> orderSn.equals(record.path("sn").asText()));
```

### 2.3 负向 / 校验

```java
// ✅ 断言具体提示文案，不是「没有跳转」
assertThat(toastText)
        .as("未出现收货地址校验提示, url=%s", ctx.currentUrl())
        .contains("请选择收货地址");
```

## 3. 禁止写法

| 写法 | 为什么禁止 |
|---|---|
| `assertTrue(true)` | 恒真 |
| `assertThat(x).isNotNull()` 单独使用 | 只证明非空 |
| 只 `assertThat(locator).isVisible()` | 元素在不等于是对的 |
| `try { ... } catch (Exception ignored) { }` 包住断言 | 吞掉失败 |
| `@Disabled("环境问题")` 代替断言 | 应上报，不能掩盖 |
| `assertThat(count).isGreaterThanOrEqualTo(0)` | 恒真 |
| `.contains(<超长段落>)` | 文案包含即通过，掩盖错误 |

## 4. 断言消息格式

```java
assertThat(orderList.orderCount())
        .as("待付款列表为空, url=%s", ctx.currentUrl())
        .isGreaterThanOrEqualTo(1);

assertThat(actualStatus)
        .as("订单状态不符, 期望%s, 实际%s, url=%s", expected, actualStatus, ctx.currentUrl())
        .isEqualTo(expected);
```

要求：**中文说明 + 期望/实际值 + 当前 URL**。UI 失败时没有 URL 基本无法定位。

## 5. 稳定性要求

- 等待与轻断言统一用 `PlaywrightAssertions`（Web-First，自动重试）；
  断言超时由 `BrowserHooks.globalSetup()` 的 `setDefaultAssertionTimeout` 统一为 15s
- 列表类断言先等「首行出现」再取 `count()`，否则会读到渲染中的 0
- 金额断言统一去千分位后比较，避免 `￥11,397.00` vs `11397.00`
- 不使用 `Thread.sleep` / `page.waitForTimeout` 换稳定
