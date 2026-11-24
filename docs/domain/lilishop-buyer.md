# lilishop 买家端（UI 自动化域知识）

> 环境：`http://127.0.0.1`（前端）/ `http://127.0.0.1/api`（后端）/ 容器 `lilishop-all`（MySQL 3306 root/lilishop）
> 最近实测：2026-11-24。改版后需重测并更新本节。

## 1. 登录态

- UI 登录有滑块，**不自动化**；`AuthHooks` 走 `POST /buyer/passport/member/userLogin` 拿 token 再注入 cookie。
- 注意：即使 cookie 注入成功，顶部仍显示「请登录」（前端未加载 `userInfo`），这是**已知渲染问题**，不能用来判断登录态。
- 依赖 `userInfo` 的交互（顶部「我的订单」「秒杀」「优惠券」等）在该环境下点击无反应，见第 4 节。

## 2. 路由（实测）

| 页面 | 路由 |
|---|---|
| 首页 / 商品列表 / 详情 | `/`、`/goodsList`、`/goodsDetail?goodsId=` |
| 购物车 / 结算 / 收银台 / 支付完成 | `/cart`、`/pay?way=CART|BUY_NOW`、`/payment?orderType=TRADE&sn=T...`、`/payDone` |
| 会员中心 | `/home`，子页 `/home/MyOrder`、`/home/OrderDetail?sn=`、`/home/AfterSale`、`/home/MyAddress`、`/home/addAddress`、`/home/Coupons`、`/home/Point`、`/home/MemberGrade`、`/home/MyGiftCards`、`/home/MoneyManagement` |
| 活动 | `/coupon`（领券中心）、`/seckill`、`/PointMall`、`/pointGoodsDetail?id=` |

## 3. 关键交互事实

| 事实 | 影响 |
|---|---|
| 顶部购物车入口、商品列表条目以**新标签页**打开 | 必须 `page.waitForPopup` 后切页（`support/Tabs`） |
| 加购接口 `POST /buyer/trade/carts?skuId=&num=`（query 传参） | body JSON 形式返回 `code=400 服务器繁忙` |
| 购物车写操作（加购/改数量/删除/清空）有 **6 秒窗口限流** | 超限 `code=1003` 静默失败，必须走 `CartWriteRateLimiter` |
| 数量组件上限 99，输入 0/-1/abc 回退 1，输入超上限时总价按上限计 | 断言用「计费数量 = 总价/单价」而不是输入框值 |
| 结算页/订单页金额文案带千分位与全角￥ | 金额统一去非数字后比较；`hasText` 是精确匹配，包含匹配用 `containsText` |
| 确认弹窗的按钮在 `.ivu-modal-confirm-footer` 内的 `button` | 点 footer 容器无效，必须点 `.ivu-btn-primary` / `.ivu-btn-text` |
| 余额支付无需密码 | `/payment` → 点「余额支付」→ 弹窗点「确定」→ `/payDone`，订单变 `UNDELIVERED + PAID` |
| 游客访问 `/cart` 不跳登录 | 与人工用例「未登录跳登录」不一致 |
| 顶部购物车角标恒为「购物车（0）」 | 加购后不刷新，不能作为断言依据 |

## 4. 环境已知缺陷（2026-11-24）

| 编号 | 现象 | 证据 |
|---|---|---|
| BUG-1 | 顶部「我的订单」无 href、无点击处理，点击不跳转 | `<li><span class="nav-item">我的订单</span></li>` |
| BUG-2 | 顶部「秒杀」「优惠券」无 href，`.colum` 下拉为空，点击不跳转 | `<li class="nav-lis">优惠券<div class="colum"></div></li>` |
| BUG-3 | 购物车角标不随加购刷新 | 加购后回首页仍显示「购物车（0）」 |
| BUG-4 | 游客访问 `/cart` 直接渲染空购物车，不跳登录 | `/cart` 200 且无 `rePath` 跳转 |
| BUG-5 | 商品库存置 0 后购物车不进入失效区：仍勾选、仍计入总价、仍可结算 | 造数 stock=0 后刷新，页面仍显示「已选择2件商品 ￥7,598.00」 |
| BUG-6 | 购物车数量大于库存时不提示、不拦截，可直接进入结算页 | stock=1 且数量 2 时点「去结算」直接跳 `/pay` |
| BUG-7 | 积分不足时点「积分购买」无任何提示，页面无反应 | 10 积分兑换需 500 积分的商品，URL 不变、无 toast、无弹窗 |

## 4.1 购物车前置造数（重要）

- 购物车**不在 MySQL**，存在 Redis 键 `{CART}_<memberId>`（memberId 从 accessToken 的 JWT `userContext.id` 解析）。
- 值不是标准 JSON：是带 `@type`、`0L` 的 Java 序列化文本，只能按文本模板处理。
- 模板：`src/test/resources/fixtures/cart_snapshot.json`（一次真实加购后的快照，单条条目）。
- 造数入口：`support/CartFixture.seed(api, items...)`，克隆条目节点并替换 skuId/goodsId/goodsName/price/num/storeId/storeName/subTotal。
- 为什么必须这么做：`POST/DELETE /buyer/trade/carts` 有限流，实测 6/8/10 秒间隔与 3 次重试仍会 `code=1003`，多场景连跑必然失败。

## 4.2 会员资产页要点

- 券列表：`.cardTabs .cardTabsItem`（未使用/已使用/已过期），条目 `.coupon-item`，按钮 `.c-right`。
- 积分：`剩余积分：N` 与 `累计获得：N` 是**两个独立节点**，必须分别定位。
- 等级：`.gradeInfo` 内含等级名、当前经验值、距下一等级差值。
- 礼品卡：输入框只有 placeholder（`请输入卡号` / `请输入兑换码（卡密）`），`label` 定位不可用。
- 余额：`账户余额：￥33,298.00` 在同一文本节点内。
- 取消订单：列表行「取消订单」→ 弹窗点 `.ivu-modal .ivu-btn-primary`；状态变更异步，断言需刷新轮询。
- 重复提交：连点 3 次「提交订单」只生成 1 笔订单（前端已防重）；新单进入待付款列表是**异步**的，断言要用「基线差集 + 刷新轮询」。
- 余额支付：钱包余额不足时收银台**不弹确认框**（实测 2,906 时点击无反应），支付类用例必须先 `DbFixture.ensureWalletAtLeast(memberId, 50000)`。
- 选券：结算页点券的「立即使用」直接抵扣、无确认弹窗，金额异步重算（用 `awaitPayable` 等刷新）。
- 已知不稳定：连续访问「我的优惠券」偶发列表 0 条（单机脚本稳定 3 条），用例内需重试或硬刷新。

## 4.3 不支持自动化的功能（2026-11-24 结论）

| 功能 | 结论 |
|---|---|
| 批发商品起批限制 | `li_wholesale` 为空表；商品详情不展示起批数量，加购只校验库存，无法复现「低于起批数被拦截」 |
| 新增收货地址地区选择 | 自定义级联组件（非 `.ivu-cascader`、无 label/测试属性），选项文案不稳定；手机号校验在保存时触发，缺地区无法单独验证 |

## 5. 可复用测试数据（实测可售）

| 用途 | goodsId / skuId | 说明 |
|---|---|---|
| 主商品（多 SKU） | `1376373278360207360` / `1387977447483375616` | 紫色 ¥3,799/库存约 1983，黑色 ¥3,899/库存约 1992 |
| 第二店铺商品 | `1411933154009481217` / `1411933154370191362` | 家家乐，¥1.00，库存 24 |
| 第三件商品 | `1415233400433135618` / `1415233400680599553` | 家家乐，¥1.00 |
| 积分商品 | `pointGoodsDetail?id=2115700000004000` | 需 500 积分，活动库存 100，活动至 2026-11-28 |
| 券 | 领券中心 3 张（满 100 减 10 / 满 1000 减 100 / 满 50 打 9 折） | kk1024 已全部持有 |
| 会员资产 | 积分 10、青铜会员经验 0（差 500 升白银）、余额约 ¥33,298、礼品卡 0 张 | |

## 6. 造数口径

- 商品下架/售罄：`li_goods.market_enable='DOWN'` 或 `li_goods_sku.quantity=0`（SKU 还有独立的 `market_enable`/`auth_flag`，必须同时为 `UPPER`/`PASS` 才可加购）。
- 订单状态：`li_order.order_status`（`UNDELIVERED`/`DELIVERED`/`COMPLETED`…），改「待收货」需补物流记录。
- 经验值：`li_member.experience` + `li_member.grade_id`（等级不随经验值自动联动）。
- 积分库存：`li_points_goods.active_stock`。
- 下架造数**必须同时改 goods 与 sku**：只改 `li_goods.market_enable` 时详情页仍正常渲染（实测）；且主商品 `1376373278360207360` 下架后仍可访问（缓存/接口未校验），可稳定复现下架页的是 `1400327593747546112`。
- 造数统一走 `support/DbFixture`（docker exec mysql），`DataHooks` 在场景结束后按登记的还原 SQL 回滚。
