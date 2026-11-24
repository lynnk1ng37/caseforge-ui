# 维护模式：定位断链与页面改版

## 1. 触发信号

- `TimeoutError: Locator.click` / `expect ... to_be_visible` 超时
- 页面改版后元素 class 或结构变化
- 用例通过但断言取到的文本为空

## 2. 处理顺序（严格）

```
1 看 trace / 截图，确认失败发生在哪一步
2 用 probe --verify 跑一遍该页面的定位器 → 得到 0/1/>1
3 0 或 >1 才修定位器；1 说明是数据或时序问题，不要动定位器
4 重新探测该页面 → 更新 locators/*.yaml（verified_at 同步）
5 只重跑受影响用例，再跑该模块全量
```

## 3. 定位器修复规则

| 情况 | 动作 |
|---|---|
| 命中 0 | 重新探测，替换 primary；旧值进 `fallbacks` 末尾 |
| 命中 >1 | 加文本/父级锚点；禁止用 `nth(0)` |
| 命中 1 但超时 | 检查遮挡、iframe、渲染时序，不是选择器问题 |
| 自愈命中 | 把命中的 fallback 提升为 primary，`healed_from` 记来源 |

## 4. 禁止

- 为了让用例过而**删除或放宽断言**
- 加 `Thread.sleep` 掩盖时序问题
- 直接把 `@Disabled` 加在失败用例上
- 改 page 方法签名（会波及其他用例），除非已获批准

## 5. 自愈边界（运行期）

`LocatorRegistry` 的 fallback 轮询仅做启发式：
1. primary 失败 → 按 fallbacks 顺序尝试；
2. 仍失败 → 在同页面候选里按 role+name 相似度匹配（不跨页面）；
3. 全部失败 → 抛错，**不返回空 locator**。

自愈命中必须：
- 在 allure 附件里记录 `healed: <id> -> <strategy>`
- 在报告中标记为 `healed`，供每周人工复核
- 复核后回写 YAML，`source: healed`

LLM 自愈默认关闭（`config.ui.heal.llm=false`），且只允许对候选排序，不允许生成代码。
