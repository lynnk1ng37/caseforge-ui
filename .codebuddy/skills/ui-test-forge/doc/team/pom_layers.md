# 分层职责与断言边界

```
steps/     用例层    只调 flows / pages，写业务断言
flows/     流程层    多页面编排，中间态 fail-fast，不写最终业务断言
pages/     页面层    单页面动作 + 元素状态取值，等待元素就绪
locators/  定位层    定位器唯一来源（YAML），无逻辑
```

## 1. 各层允许 / 禁止

| 层 | 允许 | 禁止 |
|---|---|---|
| `locators` | YAML 声明、fallbacks | 任何业务逻辑 |
| `pages` | `PlaywrightAssertions` 就绪等待；返回值（文本、条数、URL） | 业务断言（`org.assertj` / `org.junit`）；`Thread.sleep`；硬编码 URL |
| `flows` | 串联 page、传状态、中间步 fail-fast | 最终业务断言；直接定位 |
| `steps` | `assertThat(...)` 业务断言、`Allure.step` | 裸定位、`page.navigate`、固定等待 |

**为什么 page 层禁业务断言**：断言写在 page 里，同一条 page 方法被 10 条用例复用时，
失败信息只剩「page 内部断言失败」，无法定位是哪条用例的哪个业务预期错了。

## 2. Page Object 约定

```java
public class CartPage {
    private final Page page;
    private final Locator rows;
    private final Locator checkout;

    public CartPage(Page page) {
        this.page = page;
        this.rows = LocatorRegistry.resolve(page, "cart.items");
        this.checkout = LocatorRegistry.resolve(page, "cart.checkout");
    }

    public CartPage open() { ... }            // 打开页面，返回 this 便于链式
    public CartPage goCheckout() { ... }
    public int itemCount() { ... }
    public double totalAmount() { ... }
}
```

规则：
- 方法名用业务动作（`submitOrder`），不用实现（`clickDivPay`）
- 返回业务值而不是 Locator，避免定位器泄漏到用例层
- 动作后必须等「下一步需要的前置元素」出现，而不是固定等待

## 3. Flow 约定

```java
public class PurchaseFlow {
    public PaymentPage submitOrder(String goodsId, String skuId) {
        // 返回事实载体（页面对象 / 交易单号），供 steps 层断言
    }
}
```

- flow 只返回「下一步要用的事实」（单号、金额、条数），不返回断言结果
- 中间步骤失败立即抛错，错误消息带步骤名

## 4. 修改保护

| 标记 | 含义 | 是否需要用户批准 |
|---|---|---|
| 复用 | 已有方法完全覆盖 | 否 |
| 扩展 | 新增方法，不改已有方法 | 否 |
| 新建 | 新建文件 | 否 |
| ⚠️ 修改 | 改已有方法 | **是，逐项批准** |

被拒绝的修改必须改用「新增带后缀的方法 + 旧方法保留」的替代策略，
避免为了让自己那条用例过，改坏 20 条复用同一 page 方法的用例。
