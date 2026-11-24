# 定位器策略与登记表

## 1. 五级策略（不可越级）

| 级别 | 方式 | 适用 | 示例 |
|---|---|---|---|
| L1 | role + accessible name | 按钮、链接、tab、输入框 | `get_by_role("button", name="加入购物车")` |
| L2 | testid / label / placeholder | 前端补了 testid，或表单 label | `get_by_test_id("cart-checkout")`、`get_by_label("收货人")` |
| L3 | 稳定业务属性 | 状态、业务标识 | `[data-order-status="WAIT_PAY"]` |
| L4 | 语义 class（限定父级） | 组件库固定 class 且唯一 | `div.pay`、`.cardTabsItem` |
| L5 | XPath | 仅结构性兜底 | 必须 `# allowed-xpath: 理由` |

**判据**：优先能被屏幕阅读器识别的语义（L1/L2）。class 名含哈希（`_btn_3f2a9`）一律降级。

## 2. 登记表格式

```yaml
# locators/order_list.yaml
- id: order_list.tab.wait_pay
  page: order_list
  primary: {by: css, value: ".cardTabsItem", text: "待付款"}
  fallbacks:
    - {by: text, value: "待付款"}
  verified_at: "2026-11-12"
  verified_env: "local-127.0.0.1"
  source: "probe"
  healed_from: null
```

字段约定：

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | ✅ | `<page>.<元素语义>`，全局唯一 |
| `page` | ✅ | 所属 page object |
| `primary` | ✅ | 首选策略，必须是 L1~L4 |
| `fallbacks` | 建议 | 按稳定性降序，最多 3 条 |
| `text` | — | 与 `by` 组合做文本过滤（用于 class 不唯一时） |
| `verified_at` | ✅ | 最近一次实测日期；超过 30 天告警 |
| `verified_env` | ✅ | 实测环境标识，换环境必须重测 |
| `source` | ✅ | `probe` / `manual` / `healed` |
| `healed_from` | — | 自愈来源，便于人工复核 |

## 3. 解析规则

运行时 `LocatorRegistry.resolve(page, id)` 的逻辑：

1. 按 `primary` 解析，`count() > 0` 则返回；
2. 否则按 `fallbacks` 顺序解析，命中即返回并在报告里标 `healed`；
3. 全部未命中才返回 `primary`，让它以超时报错暴露断链（**不要静默返回空 locator**）。

## 4. 禁止事项

| 禁止 | 原因 |
|---|---|
| page 层内联选择器 | 断了只能改代码，无法统一巡检 |
| 用 `nth(0)` 兜住不唯一 | 页面顺序变化即错位，且掩盖真实问题 |
| 用文案做长句匹配 | 文案微调即断，优先 role + 短 name |
| 用 `xpath=//div[3]/span` | 结构一变即断 |
| 定位器跨页面复用 | 同名元素会串页 |

## 5. 巡检

```bash
python tools/probe_page.py --verify src/test/resources/locators/order_list.yaml
# 阈值可调（默认 30 天）
python tools/probe_page.py --verify src/test/resources/locators/cart.yaml --stale-days 7
```

输出每个 id 的命中数与新鲜度：

| 字段 | 取值 | 含义 |
|---|---|---|
| `primary_matches` | `0` / `1` / `>1` | 断链 / 健康 / 不唯一（需加锚点） |
| `health` | `broken` / `ok` / `duplicate` | 由 `primary_matches` 推导 |
| `verified_at` | 日期 | 登记表里的实测日期，无法解析时为原值 |
| `age_days` | 整数 / 空 | 距今天数；`verified_at` 缺失或格式错时为空 |
| `stale` | `true` / `false` / 空 | 过期 / 未过期 / **无法判定**（缺失或格式错） |

**`stale` 是三态，不要当布尔用**：`null` 表示日期不可解析，必须单独提示，
否则脏数据会被当成「新鲜」。

过期只打到 **stderr** 告警，**不改变退出码**：`health` 表示「这条定位器现在对不对」，
新鲜度表示「这条定位器多久没复测」，两者语义不同。若把过期算进退出码，
CI 会在什么都没坏的情况下变红，最后只能把检查关掉。

```
[probe][WARN] 3 个定位器超过 30 天未复测，需重新探测并更新 verified_at: cart.items(54天)、...
```
