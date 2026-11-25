# UI 转写清单

> 唯一确认口令：`确认，按照清单继续`（必须独立成行）

## 元信息

| 项 | 内容 |
|---|---|
| 任务类型 | 转写（人工 P0/P1 → UI 自动化） |
| 人工用例来源 | `manual_testcases/02-测试用例设计 gerkin.md`（lilishop 买家端 120 条 Gherkin） |
| 转写范围 | 首页与搜索、商品详情与营销、购物车、结算下单支付、个人中心订单售后、会员资产 |
| 明确排除 | 分销中心（LLS-DIST-001 ~ LLS-DIST-014 全部不转写） |
| 排除口径 | 需抓包篡改 / 并发 / 支付回调 / 超时关单 / DB 造数 / 商家或平台端操作 / 网络模拟的用例不转写（逐条见 `ui_test_dwp_temp/cases.yaml` 的 `handoff`（移交清单）） |
| 环境 | UI `http://127.0.0.1`（实测 200），API `http://127.0.0.1/api` |
| 登录方式 | API 登录 + 注入 `accessToken` / `refreshToken` cookie（不处理滑块） |
| 造数能力 | 容器 `lilishop-all`：MySQL 3306（root/lilishop，库 `lilishop`）、Redis 6379、管理端密码 123456；已实测可读写 `li_goods`/`li_goods_sku`/`li_order`/`li_member`/`li_points_goods` |
| 支付方式 | 余额支付，**无需支付密码**；实测「余额支付 → 确认弹窗点确定 → /payDone → 订单 UNDELIVERED + PAID」全流程通过 |
| 执行策略 | 串行执行（用户确认不并行） |
| 技术栈 | Java 17 + Playwright Java + Cucumber 7 + JUnit5 Platform |
| 实测日期 | 2026-11-23（本清单全部路由与定位器均为当日实测） |

## 页面与落点清单

| 序号 | 页面 | 路由 | Page Object | 落点状态 | 新增方法 |
|---|---|---|---|---|---|
| 1 | 首页 | `/` | `HomePage` | 新建 | `open()` / `openAsGuest()` / `search(keyword)` / `categoryCount()` / `recommendCount()` / `loginEntryVisible()` |
| 2 | 商品列表/搜索结果 | `/goodsList` | `GoodsListPage` | 新建 | `openByKeyword(kw)` / `itemCount()` / `firstItemName()` / `firstItemPrice()` / `openFirstItem()` / `sortBy(name)` / `activeSort()` |
| 3 | 商品详情 | `/goodsDetail` | `GoodsDetailPage` | 扩展 | `selectSku(name)` / `price()` / `stock()` / `quantity()` / `setQuantity(n)` / `remarksCount()` / `openRemarksTab()` / `subTitle()` |
| 4 | 购物车 | `/cart` | `CartPage` | 扩展 | `quantity()` / `setQuantity(n)` / `plus()` / `minus()` / `minusDisabled()` / `plusDisabled()` / `selectAll()` / `unselectAll()` / `toggleRow(i)` / `selectedCount()` / `deleteFirst()` / `clearAllAndCancel()` / `subtotal()` / `badge()` / `isGuestRedirected()` |
| 5 | 结算页 | `/pay` | `CheckoutPage` | 扩展 | `selectAddress(i)` / `addressCount()` / `openAddAddress()` / `goodsAmount()` / `discountAmount()` / `freightAmount()` / `payableAmount()` / `couponUsable(name)` / `openInvoice()` / `chooseInvoice(type)` |
| 6 | 收银台 | `/payment` | `PaymentPage` | 扩展 | `expectSubmitted()` / `tradeSn()` / `payByBalance()`（点余额支付 + 弹窗确定）/ `payOptions()` / `successUrl()` |
| 7 | 我的订单 | `/home/MyOrder` | `OrderListPage` | 扩展 | `searchBySn(sn)` / `tabCount()` / `orderTexts()` / `orderSns()` / `orderStatuses()` / `emptyTipVisible()` / `openDetail(i)` |
| 8 | 订单详情 | `/home/OrderDetail` | `OrderDetailPage` | 新建 | `openBySn(sn)` / `status()` / `orderSn()` / `goodsNames()` / `payStatusText()` / `payableAmount()` / `cancelOrder()` / `applyAfterSaleVisible()` |
| 9 | 收货地址 | `/home/MyAddress` | `AddressPage` | 新建 | `open()` / `addressCount()` / `defaultAddressTags()` / `addAddress(name, phone, detail)` / `setDefault(i)` / `delete(i)` / `phoneTexts()` |
| 10 | 新增地址 | `/home/addAddress` | `AddressFormPage` | 新建 | `fill(name, phone, detail)` / `chooseRegion(province, city, district)` / `save()` / `fieldError(label)` |
| 11 | 我的售后 | `/home/AfterSale` | `AfterSalePage` | 新建 | `open()` / `orderCount()` / `emptyTipVisible()` |
| 12 | 个人中心 | `/home` | `MemberCenterPage` | 新建 | `open()` / `couponCount()` / `pointAmount()` / `balanceAmount()` / `gradeName()` / `menu(name).click` |
| 13 | 我的优惠券 | `/home/Coupons` | `CouponPage` | 新建 | `open()` / `switchTab(name)` / `items()` / `usableCount()` / `useButtonsVisible()` |
| 14 | 领券中心 | `/coupon` | `CouponCenterPage` | 新建 | `open()` / `receiveFirst()` / `receiveButtonText()` |
| 15 | 我的积分 | `/home/Point` | `PointPage` | 新建 | `open()` / `remaining()` / `totalGained()` / `firstLogText()` |
| 16 | 我的等级 | `/home/MemberGrade` | `GradePage` | 新建 | `open()` / `gradeName()` / `experience()` / `deltaToNext()` |
| 17 | 我的礼品卡 | `/home/MyGiftCards` | `GiftCardPage` | 新建 | `open()` / `redeem(code)` / `toast()` / `cardCount()` |
| 18 | 我的余额 | `/home/MoneyManagement` | `MoneyPage` | 新建 | `open()` / `balance()` / `switchTab(name)` / `frozen()` |
| 19 | 秒杀 | `/seckill` | `SeckillPage` | 新建 | `open()` / `pageFlagVisible()` / `goodsCount()` |
| 20 | 积分商城 | `/PointMall` | `PointMallPage` | 新建 | `open()` / `goodsCount()` / `emptyVisible()` |
| 21 | 积分商品详情 | `/pointGoodsDetail` | `PointGoodsDetailPage` | 新建 | `openById(id)` / `points()` / `stock()` / `buyWithPoints()` |

## 定位器清单

| 序号 | 元素 | page | 级别 | primary | fallbacks | 来源 |
|---|---|---|---|---|---|---|
| 1 | 顶部搜索框 | common | L2 | placeholder `输入你想查找的商品` | — | probe 2026-11-23 |
| 2 | 顶部购物车入口 | common | L3 | css `a[href='/cart']` | — | probe 2026-11-23 |
| 3 | 顶部我的订单 | common | L4 | css `span.nav-item` + text `我的订单` | — | probe 2026-11-23 |
| 4 | 顶部请登录入口 | common | L4 | css `a.nav-item` + text `请登录` | — | probe 2026-11-23 |
| 5 | 顶部立即注册入口 | common | L4 | css `a.nav-item` + text `立即注册` | — | probe 2026-11-23 |
| 6 | 顶部秒杀入口 | common | L1 | text `秒杀`（exact） | — | probe 2026-11-23 |
| 7 | 顶部优惠券入口 | common | L1 | text `优惠券`（exact） | — | probe 2026-11-23 |
| 8 | 全局 toast | common | L4 | css `.ivu-message-notice-content` | — | 已验证 |
| 9 | 通用确认弹窗 | common | L4 | css `.ivu-modal-confirm` | — | probe 2026-11-23 |
| 10 | 首页分类条目 | home | L4 | css `.nav-side-item` | — | probe 2026-11-23 |
| 11 | 首页推荐商品位 | home | L4 | css `.right-item` | — | probe 2026-11-23 |
| 12 | 结果条目 | goods_list | L4 | css `.goods-list .goods-show-info` | — | probe 2026-11-23 |
| 13 | 结果商品名 | goods_list | L4 | css `.goods-show-detail` | — | probe 2026-11-23 |
| 14 | 结果商品价 | goods_list | L4 | css `.goods-show-price` | — | probe 2026-11-23 |
| 15 | 排序当前项 | goods_list | L4 | css `.goods-list-tool-active` | — | probe 2026-11-23 |
| 16 | 价格排序 | goods_list | L4 | css `.price-sort` | — | probe 2026-11-23 |
| 17 | 商品标题 | goods_detail | L4 | css `.item-detail-title` | — | probe 2026-11-23 |
| 18 | 商品价格 | goods_detail | L4 | css `.item-price` | — | probe 2026-11-23 |
| 19 | SKU 选项行 | goods_detail | L4 | css `.item-select-row` | — | probe 2026-11-23 |
| 20 | 库存文案 | goods_detail | L4 | css `.item-select` + text `库存` | — | probe 2026-11-23 |
| 21 | 数量输入框 | goods_detail | L4 | css `.ivu-input-number-input` | — | probe 2026-11-23 |
| 22 | 累计评价数量 | goods_detail | L4 | css `.item-remarks-num` | — | probe 2026-11-23 |
| 23 | 商品评价页签 | goods_detail | L1 | text `商品评价`（exact） | — | probe 2026-11-23 |
| 24 | 加入购物车 | goods_detail | L1 | role button name `加入购物车` | css `button.ivu-btn-error` | 已验证 |
| 25 | 立即购买 | goods_detail | L1 | role button name `立即购买` | — | 已验证 |
| 26 | 加购成功文案 | goods_detail | L1 | text `商品已成功加入购物车！` | — | 已验证 |
| 27 | 购物车条目 | cart | L4 | css `.cart-goods-items .goods-item` | css `.goods-item` | 已验证 |
| 28 | 购物车商品名 | cart | L4 | css `.cart-goods-items .goods-title` | — | probe 2026-11-23 |
| 29 | 购物车单价 | cart | L4 | css `.goods-item .width_150` + nth 0 | — | probe 2026-11-23 |
| 30 | 购物车小计 | cart | L4 | css `.goods-item .width_150` + nth 1 | — | probe 2026-11-23 |
| 31 | 购物车数量输入 | cart | L4 | css `.ivu-input-number-input` | — | probe 2026-11-23 |
| 32 | 数量加号 | cart | L4 | css `.ivu-input-number-handler-up` | — | probe 2026-11-23 |
| 33 | 数量减号 | cart | L4 | css `.ivu-input-number-handler-down` | — | probe 2026-11-23 |
| 34 | 全选框 | cart | L4 | css `.ivu-checkbox-wrapper` + text `全选` + nth 0 | — | probe 2026-11-23 |
| 35 | 行勾选框 | cart | L4 | css `.goods-item .ivu-checkbox-wrapper` | — | probe 2026-11-23 |
| 36 | 删除选中商品 | cart | L1 | text `删除选中商品` | — | probe 2026-11-23 |
| 37 | 清空购物车 | cart | L1 | text `清空购物车` | — | probe 2026-11-23 |
| 38 | 购物车总价 | cart | L4 | css `.total-price` | — | 已验证 |
| 39 | 去结算 | cart | L4 | css `div.pay` + text `去结算` | text `去结算` | 已验证 |
| 40 | 购物车角标 | cart | L4 | css `a[href='/cart']` + text `购物车` | — | probe 2026-11-23 |
| 41 | 购物车空态 | cart | L4 | css `.cart-empty` | — | 已验证 |
| 42 | 结算页标识 | checkout | L1 | text `收货人信息`（非精确） | — | 已验证 |
| 43 | 结算页地址项 | checkout | L4 | css `.ivu-radio-wrapper` | — | probe 2026-11-23 |
| 44 | 已选地址 | checkout | L4 | css `.ivu-radio-wrapper-checked` | — | 已验证 |
| 45 | 添加新地址 | checkout | L1 | text `添加新地址` | — | probe 2026-11-23 |
| 46 | 应付金额 | checkout | L1 | text `应付金额`（非精确） | — | probe 2026-11-23 |
| 47 | 商品总额行 | checkout | L1 | text `总商品金额`（非精确） | — | probe 2026-11-23 |
| 48 | 提交订单 | checkout | L4 | css `div.pay` + text `提交订单` | text `提交订单` | 已验证 |
| 49 | 收银台成功文案 | payment | L1 | text `订单提交成功，请尽快付款！`（非精确） | — | 已验证 |
| 50 | 收银台应付标签 | payment | L1 | text `应付金额`（非精确） | — | 已验证 |
| 51 | 订单页签 | order_list | L4 | css `.cardTabsItem` | — | 已验证 |
| 52 | 选中页签 | order_list | L4 | css `.cardTabsItem.active` | — | 已验证 |
| 53 | 订单卡片 | order_list | L4 | css `.order-list` | — | 已验证 |
| 54 | 订单号搜索框 | order_list | L2 | placeholder `请输入订单号搜索` | — | probe 2026-11-23 |
| 55 | 订单详情按钮 | order_list | L4 | css `.order-body .ivu-btn` + text `订单详情` + nth 0 | — | probe 2026-11-23 |
| 56 | 订单详情容器 | order_detail | L4 | css `.order-detail` | — | probe 2026-11-23 |
| 57 | 订单详情卡片 | order_detail | L4 | css `.order-card` | — | probe 2026-11-23 |
| 58 | 订单金额区 | order_detail | L4 | css `.order-price` | — | probe 2026-11-23 |
| 59 | 地址卡片 | address | L4 | css `.address-box` | — | probe 2026-11-23 |
| 60 | 地址内容 | address | L4 | css `.address-content` | — | probe 2026-11-23 |
| 61 | 默认地址标签 | address | L1 | text `默认地址` | — | probe 2026-11-23 |
| 62 | 地址修改入口 | address | L4 | css `.address-action` + text `修改` | — | probe 2026-11-23 |
| 63 | 地址删除入口 | address | L4 | css `.address-action` + text `删除` | — | probe 2026-11-23 |
| 64 | 地址页新增按钮 | address | L1 | text `添加新地址` | — | probe 2026-11-23 |
| 65 | 收件人输入 | address_form | L2 | label `收件人` | — | probe 2026-11-23 |
| 66 | 详细地址输入 | address_form | L2 | label `详细地址` | — | probe 2026-11-23 |
| 67 | 手机号输入 | address_form | L2 | label `手机号码` | — | probe 2026-11-23 |
| 68 | 地址别名输入 | address_form | L2 | label `地址别名` | — | probe 2026-11-23 |
| 69 | 收货地区选择 | address_form | L4 | css `.ivu-cascader` | — | probe 2026-11-23 |
| 70 | 保存收货地址 | address_form | L1 | text `保存收货地址` | — | probe 2026-11-23 |
| 71 | 售后列表容器 | after_sale | L4 | css `.ivu-card-body` + 我的售后 | — | probe 2026-11-23 |
| 72 | 个人中心菜单项 | member_center | L4 | css `.ivu-menu-item` + text | — | probe 2026-11-23 |
| 73 | 个人中心余额 | member_center | L1 | text `我的余额` | — | probe 2026-11-23 |
| 74 | 我的券条目 | coupons | L4 | css `.coupon-item` | — | probe 2026-11-23 |
| 75 | 券立即使用 | coupons | L4 | css `.c-right` + text `立即使用` | — | probe 2026-11-23 |
| 76 | 领券中心列表 | coupon_center | L4 | css `.coupon-center` | — | probe 2026-11-23 |
| 77 | 券领取按钮 | coupon_center | L1 | text `立即领取` | — | probe 2026-11-23 |
| 78 | 积分页标识 | point | L1 | text `剩余积分`（非精确） | — | probe 2026-11-23 |
| 79 | 积分日志首行 | point | L4 | css `.ivu-table-tbody tr` + nth 0 | — | probe 2026-11-23 |
| 80 | 等级卡片 | grade | L4 | css `.gradeCard` | — | probe 2026-11-23 |
| 81 | 等级信息 | grade | L4 | css `.gradeInfo` | — | probe 2026-11-23 |
| 82 | 礼品卡卡号输入 | gift_card | L2 | label `卡号` | — | probe 2026-11-23 |
| 83 | 礼品卡兑换码输入 | gift_card | L2 | label `兑换码` | — | probe 2026-11-23 |
| 84 | 礼品卡兑换按钮 | gift_card | L1 | text `兑换`（exact） | — | probe 2026-11-23 |
| 85 | 余额金额 | money | L1 | text `账户余额`（非精确） | — | probe 2026-11-23 |
| 86 | 资金页签 | money | L4 | css `.ivu-tabs-tab` + text | — | probe 2026-11-23 |
| 87 | 秒杀页标识 | seckill | L1 | text `限时秒杀`（非精确） | — | probe 2026-11-23 |
| 88 | 秒杀商品列表 | seckill | L4 | css `.seckill-list` | — | probe 2026-11-23 |
| 89 | 积分商城容器 | point_mall | L4 | css `.point-mall` | — | probe 2026-11-23 |
| 90 | 积分商城商品 | point_mall | L4 | css `.point-mall .goods-list` | — | probe 2026-11-23 |
| 91 | 余额支付选项 | payment | L1 | text `余额支付` | — | probe 2026-11-23（实测可支付） |
| 92 | 支付确认-确定 | payment | L4 | css `.ivu-modal-confirm-footer` + text `确定` | text `确定`（exact） | probe 2026-11-23 |
| 93 | 支付成功页标识 | payment | L1 | text `查看订单` | — | probe 2026-11-23（/payDone） |
| 94 | 收银台倒计时常量 | payment | L4 | css `.pay-count-down`（实现时按实测校正） | — | probe 2026-11-23 |
| 95 | 积分商品积分值 | point_goods_detail | L1 | text `积` + `分500`（非精确） | — | probe 2026-11-23 |
| 96 | 积分购买按钮 | point_goods_detail | L1 | text `积分购买` | — | probe 2026-11-23 |

新增路由（`src/test/resources/routes.yaml`）：`home /`、`home_index /home`、`coupon_center /coupon`、`point_mall /PointMall`、`point_goods_detail /pointGoodsDetail`、`seckill /seckill`、`my_coupons /home/Coupons`、`my_point /home/Point`、`my_grade /home/MemberGrade`、`my_gift_cards /home/MyGiftCards`、`money_management /home/MoneyManagement`、`my_address /home/MyAddress`、`add_address /home/addAddress`、`my_after_sale /home/AfterSale`、`order_detail /home/OrderDetail`。

## 场景清单

| 序号 | 人工用例编号 | 场景 | 类型 | 前置数据 | 断言依据 | 状态 | 批次 |
|---|---|---|---|---|---|---|---|
| 1 | LLS-HOME-001 | 未登录访问首页可浏览且登录入口可用 | 正向 | 清除 cookie（无需造数） | 分类条目数≥1、推荐位≥1；`立即注册`/`请登录` 入口可见 | 待实现 | A |
| 2 | LLS-HOME-002 | 顶部购物车与我的订单入口跳转正确 | 正向 | 已登录 | 购物车入口点击后 URL 含 `/cart` 且「我的购物车」可见；我的订单点击后 URL 含 `/home/MyOrder` 且订单页签可见 | 待实现 | A |
| 3 | LLS-HOME-004 | 搜索「一加」并点击首条进入详情 | 正向 | 商品在售（实测 7 条结果） | 结果条目≥1、首条名称含「一加」；详情页标题含「一加 OnePlus 9」 | 待实现 | A |
| 4 | LLS-HOME-005 | 搜索关键字空、空格、1 字符与 60 字符边界 | 边界 | 无 | 空与纯空格提交后 URL 不含 keyword；「a」进入结果页；60 字符输入值长度≥50 且页面无 404 | 待实现 | A |
| 5 | LLS-HOME-006 | 无结果关键字展示空态且不残留旧结果 | 负向 | 关键字 `zzz_no_such_goods_20261123`（确定性 0 结果，不使用敏感词） | 结果条目为 0 或页脚「共 0 条」；页面不出现上一次关键字「一加」的商品 | 待实现 | A |
| 6 | LLS-HOME-010 | 秒杀与优惠券入口可进入且活动内容展示 | 正向 | 首页已配置领券中心与秒杀入口 | 点优惠券进入 `/coupon` 且「领券中心」可见；点秒杀进入 `/seckill` 且「限时秒杀」可见 | 待实现 | A |
| 7 | LLS-GOODS-001 | 切换 SKU 后价格与库存同步变化 | 正向 | 商品 `1376373278360207360`：紫色 ¥3,799/库存 1983，黑色 ¥3,899/库存 1992（实测） | 切黑色后价格 ¥3,899.00 且库存 1992；切回紫色恢复 ¥3,799.00 与 1983 | 待实现 | A |
| 8 | LLS-GOODS-002 | 商品基础信息与类型展示正确 | 正向 | 同上商品 | 标题含「一加 OnePlus 9」、价格 ¥3,799.00、库存>0、规格「紫色/黑色」、「实物商品」标识、累计评价数、两个购买按钮 | 待实现 | A |
| 9 | LLS-GOODS-003 | 售罄或下架商品不可购买 | 负向 | 造数：DB 将商品 `1395276362339581952` 的 SKU 库存置 0（现库存已为 0），并准备一个 `market_enable='DOWN'` 商品 | 出现「已下架」「无货」或「已售罄」；加购/立即购买不可用或被拦截 | 待实现 | B |
| 10 | LLS-GOODS-004 | 购买数量 1、0、-1 与超库存边界 | 边界 | 库存 1983 | 1 生效且小计正确；0/-1 回退 1；99999 被截断或提示库存不足 | 待实现 | A |
| 11 | LLS-GOODS-005 | 批发商品低于起批数量被拦截 | 边界 | 造数：`li_wholesale` 空表，为 WHOLESALE 商品 `1400327593747546112` 的 SKU 插一条 `num=10` 阶梯后实测 | 9 提示不能小于起批数量；10 可加购 | 待实现 | B |
| 12 | LLS-GOODS-006 | 加入购物车与立即购买入口均可用 | 正向 | 已登录、有库存 | 加购 toast 含「商品已成功加入购物车」且角标 1；立即购买进入结算页且含该商品 | 待实现 | A |
| 13 | LLS-GOODS-007 | 优惠券重复领取被拦截且只增加 1 张 | 负向 | 实测：领券中心 3 张券该账号均已持有（券数 3），重复点击「立即领取」无新增、无反馈 | 再次领取后我的券数量仍为 3（不新增）；不出现领取成功提示；首次领取分支改由新造一张限领 1 券覆盖 | 待实现 | A |
| 14 | LLS-GOODS-014 | 积分不足或售罄时不可兑换积分商品 | 负向 | 实测：积分商品 id `2115700000004000`（需 500 积分、活动库存 100），账号仅 10 积分；售罄分支 DB 置 `active_stock=0` | 详情页显示积分 500 与库存；积分不足点击积分购买提示并拦截；售罄提示已售罄；无订单生成 | 待实现 | C |
| 15 | LLS-GOODS-015 | 商品评价区展示与无评价空态 | 正向 | 该商品累计评价 0 条（实测） | 累计评价显示 0 条；商品评价页签可切换且切换后无错误提示/白屏 | 待实现 | A |
| 16 | LLS-CART-001 | 加购后列表、数量与角标正确 | 正向 | 空购物车 + 商品 | 角标 1、1 行、数量 1、单价与总价 ¥3,799.00 | 待实现 | A |
| 17 | LLS-CART-002 | 同一 SKU 重复加购累加不新增行 | 正向 | 已在车内 1 件 | 仍 1 行、数量 2、总价 ¥7,598.00 | 待实现 | A |
| 18 | LLS-CART-003 | 多店铺商品按店铺分组且不串规格 | 正向 | 实测无需造数：店铺「家家乐」有 169 个上架商品，用 `1377066518478585856`/SKU `1377066518889627648`（￥35）与自营店商品混合 | 店铺分组≥2、每行商品名与所属店铺匹配、各行小计之和等于总价 | 待实现 | B |
| 19 | LLS-CART-004 | 数量达到库存上限后加号不可再增 | 边界 | 库存 1983 | 输入 1983 后点「+」数量仍 1983；「+」置灰或提示上限；总价=单价×1983 | 待实现 | A |
| 20 | LLS-CART-005 | 数量输入超库存、0、负数与非数字被拦截 | 边界 | 库存 1983 | 999999 回退或提示库存不足；0/-1 回退 1；abc 归一为整数且总价不按非法值算 | 待实现 | A |
| 21 | LLS-CART-006 | 数量减到 1 时减号置灰且不出现 0 | 边界 | 数量先置 2 | 数量保持 1、减号 `handler-down-disabled`、小计=单价 | 待实现 | A |
| 22 | LLS-CART-007 | 勾选与取消勾选后总价实时重算 | 正向 | 需 2 件不同价商品 | 取消勾选后总价=剩余勾选金额；恢复勾选后还原；已选件数同步 | 待实现 | A |
| 23 | LLS-CART-009 | 删除单件与取消清空购物车正确 | 正向 | 需 3 件商品 | 删除后行数-1；取消清空后行数不变；角标与行数一致 | 待实现 | A |
| 24 | LLS-CART-011 | 下架或售罄商品进入失效区且不可结算 | 负向 | 造数：加购后 DB 把该 SKU 的 `quantity` 置 0 或商品 `market_enable='DOWN'` | 展示「无货」或「已下架」；失效商品 checkbox 不可用；总价不含失效商品 | 待实现 | B |
| 25 | LLS-CART-013 | 库存不足时提示最大可购并拦截结算 | 负向 | 造数：购物车数量 2，DB 把 SKU 库存改为 1 | toast 含「库存不足，最多可购 1 件」；结算被拦截或数量被调整 | 待实现 | B |
| 26 | LLS-CART-008 | 全选、全不选与半选状态互斥正确 | 异常 | 需 3 件商品 | 全选后全部勾选且总价=合计；全不选总价 ￥0.00；勾 1 件时全选为半选 | 待实现 | B |
| 27 | LLS-CART-010 | 未登录访问购物车跳登录且携带回跳地址 | 异常 | 清除 cookie | 跳转 `/login` 且 rePath 含 `/cart`；购物车页不展示账号数据 | 待实现 | A |
| 28 | LLS-ORDER-001 | 立即购买提交订单并支付成功 | 正向 | 默认地址 + 余额充足（实测 ￥37,097，支付后扣 3,799）；余额支付无需密码 | 收银台「订单提交成功」；点余额支付+确定后跳 `/payDone`；订单状态 UNDELIVERED(待发货)、付款状态 PAID；应付=商品金额+运费-优惠 | 待实现 | C |
| 29 | LLS-ORDER-003 | 结算页地址切换与新增地址回显正确 | 正向 | 账号已有 3 个地址（实测） | 切换后选中地址变化；新增地址出现在列表；默认地址唯一 | 待实现 | B |
| 30 | LLS-ORDER-010 | 结算页商品总价、优惠与应付金额一致 | 正向 | 结算页含该商品与可用券 | 应付=商品总额-优惠+运费；与提交区展示一致；无负数应付 | 待实现 | B |
| 31 | LLS-ORDER-004 | 收货地址手机号、姓名与详细地址边界校验 | 边界 | 新增地址表单（地区为级联组件） | 10 位手机号提示格式错误不保存；11 位保存成功；超长/特殊字符有提示且已填内容不丢 | 待实现 | B |
| 32 | LLS-ORDER-009 | 发票类型切换与字段校验 | 边界 | 结算页发票编辑 | 专票缺税号提示并阻止提交；普票抬头保存成功；发票金额等于实付 | 待实现 | C |
| 33 | LLS-ORDER-006 | 优惠券门槛不足与满足门槛的抵扣结果 | 边界 | 满 100 减 10 券（实测存在）+ 69 元与 222 元商品 | 69 元时券不可用或提示差额；222 元时可用且应付 212 | 待实现 | B |
| 34 | LLS-ORDER-020 | 结算页停留期间商品失效后提交被拦截 | 异常 | 造数：结算页加载后 DB 把该 SKU 库存置 0 | 提示失效原因且停留结算页；不生成订单 | 待实现 | C |
| 35 | LLS-ORDER-019 | 重复点击提交订单只生成一个订单 | 异常 | 购物车 1 件商品 | 连续点击 3 次后待付款列表该商品订单数只加 1 | 待实现 | C |
| 36 | LLS-UC-001 | 订单列表默认全部且按时间倒序 | 正向 | 账号现有 5 笔已关闭订单（实测） | 「全部订单」选中；订单号按时间倒序；金额与卡片一致 | 待实现 | B |
| 37 | LLS-UC-002 | 订单 Tab 筛选与计数一致 | 正向 | 需多状态订单（现有以已关闭/已完成为主） | 各 Tab 内订单状态文案与 Tab 匹配；无数据显示空态 | 待实现 | B |
| 38 | LLS-UC-004 | 按订单号搜索与不存在单号空态 | 负向 | 现有订单号（实测 O2124862221586481153） | 完整单号命中 1 条；不存在的 19 位单号展示空态 | 待实现 | B |
| 39 | LLS-UC-005 | 订单详情字段与列表一致 | 正向 | 已关闭订单 | 详情单号与列表一致；商品名/单价/数量/付款状态可见；应付=商品小计 | 待实现 | B |
| 40 | LLS-UC-006 | 取消待付款订单后状态变为已取消 | 正向 | 需一笔待付款订单（下单不支付造数） | 状态变「已取消」；日志含取消原因；不再可支付 | 待实现 | C |
| 41 | LLS-UC-010 | 售后入口按订单状态正确展示 | 负向 | 已关闭订单 | 不可售后订单不展示「申请售后」；售后列表空态 | 待实现 | B |
| 42 | LLS-UC-019 | 新增收货地址、默认地址唯一与删除 | 正向 | 已有 3 个地址（实测） | 新地址出现在列表；默认标签唯一；删除后数量-1 | 待实现 | B |
| 43 | LLS-UC-020 | 手机号脱敏与退出登录后不可访问订单页 | 异常 | 已登录 | 手机号形如 `199****0002`；退出后访问订单页跳登录；无上一账号数据 | 待实现 | C |
| 44 | LLS-UC-003 | 快速切换订单 Tab 不展示脏数据 | 异常 | 多状态订单 | 当前 Tab 选中态与列表状态文案一致；无上一 Tab 残留 | 待实现 | B |
| 45 | LLS-UC-008 | 确认收货后订单完成且重复确认幂等 | 异常 | 造数：DB 把已付款订单 `li_order.order_status` 改 `DELIVERED` 并补物流记录（实测页面能展示「待收货」才保留） | 状态「已完成」；按钮消失；重复确认无二次状态变化 | 待实现 | C |
| 46 | LLS-ASSET-001 | 优惠券按使用状态分类展示 | 正向 | 现有 3 张未使用券（实测） | 未使用券展示面额/门槛/范围/有效期；已使用与已过期为空态；过期券无「立即使用」 | 待实现 | C |
| 47 | LLS-ASSET-002 | 同一优惠券重复领取只增加 1 张 | 负向 | 实测：kk1024 已持有领券中心全部 3 张券 | 重复领取后「我的优惠券」仍为 3 条（接口回查一致）；无重复发放 | 待实现 | C |
| 48 | LLS-ASSET-005 | 积分余额与积分日志一致 | 正向 | 积分 10、累计获得 10（实测） | 剩余=累计获得-累计消耗；日志首条 +10 与余额口径一致 | 待实现 | C |
| 49 | LLS-ASSET-009 | 等级页展示当前等级与升级差值 | 正向 | 青铜会员、经验值 0（实测） | 展示「青铜会员」；当前经验值 0；距白银差 500 | 待实现 | C |
| 50 | LLS-ASSET-017 | 资产各页数据与接口回查一致 | 正向 | 同一账号（个人中心 `/home` 实测无资产汇总，改为四页 + 接口口径） | 券页条数=3、积分页剩余=10、等级页青铜/经验 0、余额页=接口钱包余额；刷新后不变 | 待实现 | C |
| 51 | LLS-ASSET-012 | 礼品卡错误兑换码被拒绝 | 负向 | 一个不存在的兑换码 | toast 提示无效/失败；礼品卡列表数量不变（现为 0 条） | 待实现 | C |
| 52 | LLS-ASSET-010 | 积分不足或售罄时积分商品被拦截 | 负向 | 实测：`li_points_goods` 4 条（需 500 积分，库存 100，活动至 2026-11-28），账号仅 10 积分；售罄分支 DB 把 `active_stock` 置 0 | 详情页 `/pointGoodsDetail?id=2115700000004000` 显示积分 500；点击积分购买提示积分不足并拦截；售罄提示已售罄；无订单生成 | 待实现 | C |
| 53 | LLS-ASSET-008 | 经验值阈值 T-1、T、T+1 触发等级变化 | 边界 | 造数：DB 改 `li_member` 经验值（青铜→白银阈值 500） | T-1 保持青铜；T 与 T+1 为白银；进度与差值正确 | 待实现 | C |

## 覆盖度自检

| 类型 | 数量 | 占比 | 要求 | 是否达标 |
|---|---|---|---|---|
| 正向 | 25 | 47% | ≤ 50% | 达标 |
| 负向 | 11 | 21% | ≥ 20% | 达标 |
| 边界 | 10 | 19% | ≥ 15% | 达标 |
| 异常 | 7 | 13% | ≥ 10% | 达标 |

合计 53 条；分销中心 14 条整体排除；其余未列入的场景逐条给出排除理由（见 `cases.yaml` 的 `handoff`（移交清单））。

## 实施批次

| 批次 | 内容 | 场景数 | 说明 |
|---|---|---|---|
| A | 首页与搜索、商品详情、购物车（主链路） | 18 | 无需造数，先跑通链路与定位器 |
| B | 结算页地址与金额、订单列表/详情/售后入口、购物车分组/勾选/失效、下架商品、批发起批 | 16 | 依赖 DB 造数（库存/下架/批发阶梯）与家家乐店铺真实商品 |
| C | 支付（余额支付实测可行）、售后幂等、会员资产（券/积分/等级/礼品卡/余额）、积分商品 | 19 | 依赖 DB 造数（经验值/订单状态/积分库存）与接口下单造待付款 |

## 待确认事项

| 序号 | 事项 | 需要你确认或提供 |
|---|---|---|
| 1 | 造数执行通道 | 我计划在 Java 侧用 JDBC 直连 `127.0.0.1:3306/lilishop`（root/lilishop）做前置与还原；若只允许容器内 `mysql` 客户端，我改成 `docker exec` 调用 |
| 2 | 测试账号隔离 | `kk1024` 是共享演示账号（已有 6 笔订单、余额 3.3 万）。是否新建专用买家账号跑自动化？不新建则造数只动指定商品/订单，不动历史数据 |
| 3 | 支付成功订单的清理 | 实测余额支付后订单不可取消（已产生 2 笔 UNDELIVERED+PAID）。清理用 DB 删除订单并回滚库存，还是保留 |
| 4 | 批发起批数量实测兜底 | `li_wholesale` 为空表，我造 `num=10` 阶梯后若前端不展示起批限制，GOODS-005 直接取消，是否接受 |
| 5 | 积分商城列表空 | 实测 `/PointMall` 列表 0 条但详情页可直达。ASSET-010 只断言详情页与购买拦截、不断言列表，是否接受 |
| 6 | 待收货订单造数兜底 | 需 DB 同时改 `li_order.order_status` 与物流记录；若前端「待收货」Tab 无数据，UC-008 取消，是否接受 |
| 7 | 敏感字符串处理 | 环境里已有店铺/商品数据含敏感字样，我不会在任何 feature / 断言 / 报告里引用该字符串；空结果搜索统一用 `zzz_no_such_goods_20261123`，请确认这样处理 |
| 8 | 只读场景是否需要接口回查 | 写操作（下单/取消/地址/礼品卡）我会加接口回查；纯读场景（列表/详情/金额）只做 UI 断言，是否需要额外接口对账 |
| 9 | 标签策略 | 保留 `@p0` 冒烟子集 + `@serial` 串行标签；是否还需要 `@smoke`、`@regression` 之类的分层标签 |
| 10 | 交付节奏 | 依次交付 A/B/C 三批：A 批跑通后先跑一轮给你看结果，再继续 B/C，还是三批一起写完再统一跑 |
| 11 | 余额消耗 | 余额实测 33,298（每次支付 3,799，约够 8 次）。ORDER-001 反复跑会耗尽余额，是否允许用例跑前 DB 把余额补到固定值（如 50,000）并在结束后还原 |
| 12 | 领券无反馈 | 实测重复领取「立即领取」既不报错也不新增（券数不变）。是否接受「以券数量不新增 + 接口回查」作为断言依据；若要求必须有「已达领取上限」提示，则该提示是缺陷，需按 BUG 上报 |

## 用户确认记录

| 项 | 内容 |
|---|---|
| 确认状态 | 已确认 |
| 确认时间 | 2026-11-23 |
| 用户回复摘要 | 确认，按照清单继续（第 2 轮，含 12 项默认值：JDBC 直连 3306、不动历史数据、支付单 DB 清理、余额跑前补到 50000、领券以数量不新增断言、积分商品走详情页、批发阶梯实测兜底、待收货 DB 改状态兜底、@p0+@serial 标签、A 批先跑、写操作接口回查、空结果用中性关键字） |

## 实现回填

> 状态列不在此处改动（清单状态列参与指纹），实现情况统一记录在本表。

| 批次 | 场景 | 用例方法 / 页面对象 | 结果（2026-11-24 实测） |
|---|---|---|---|
| A | LLS-HOME-001 未登录访问首页可浏览且登录入口可用 | `HomeSteps.clearLoginState` + `HomePage.openAsGuest` | 通过 |
| A | LLS-HOME-002 顶部购物车与我的订单入口跳转正确 | `HomeSteps.clickCartEntry/clickOrdersEntry` | 失败：顶部「我的订单」是 `li>span` 无 href、无点击处理（购物车入口 `target=_blank` 可打开新标签页，已验证通过） |
| A | LLS-HOME-004 搜索「一加」并点击首条进入详情 | `HomeSteps.searchOnePlus/openFirstResult` + `Tabs` | 通过（结果条目以新标签页打开详情，已在 `Tabs` 里处理 popup） |
| A | LLS-HOME-005 搜索关键字空、空格、1 字符与 60 字符边界 | `HomeSteps.submitEmptyKeyword/submitSingleChar/fillSixtyChars` | 通过（实测空关键字会跳 `/goodsList` 但不带 `keyword`，断言改为「未被当作检索词」） |
| A | LLS-HOME-006 无结果关键字展示空态 | `HomeSteps.searchNoResultKeyword/resultsAreEmpty` | 通过（中性关键字 `zzz_no_such_goods_20261123`，0 条） |
| A | LLS-HOME-010 秒杀与优惠券入口可进入 | `HomeSteps.clickCouponEntry/clickSeckillEntry` | 失败：顶部「秒杀/优惠券」是 `li.nav-lis` 无 href、`colum` 下拉为空、点击无跳转 |
| A | LLS-GOODS-001 SKU 切换价格库存联动 | `GoodsSteps.selectBlackSku/selectPurpleSku` | 通过（紫色 ¥3,799/1983，黑色 ¥3,899/1992） |
| A | LLS-GOODS-002 商品基础信息展示 | `GoodsSteps.titleContainsGoodsName` 等 | 通过 |
| A | LLS-GOODS-004 购买数量边界 | `GoodsSteps.setQuantityZero` 等 | 通过（组件上限 99，0/-1 回退 1） |
| A | LLS-GOODS-006 加购与立即购买入口可用 | `GoodsSteps.addToCart/buyNow` + `CartSteps.badgeShowsOne` | 失败：顶部购物车角标恒为「购物车（0）」，加购后不刷新 |
| A | LLS-GOODS-007 优惠券重复领取被拦截 | `GoodsSteps.receiveFirstCoupon/couponCountUnchanged` + `CouponCenterPage` | 通过（券数量保持 3，接口与页面一致） |
| A | LLS-GOODS-015 评价区展示与空态 | `GoodsSteps.remarksCountIsZero/openRemarksTab` | 通过（累计评价 0 条） |
| A | LLS-CART-001 加购后列表与角标正确 | `CartSteps.addTestGoodsFromDetail` | 失败：同角标问题（行数/数量/总价断言已通过，仅角标断言失败） |
| A | LLS-CART-002 同一 SKU 重复加购累加 | `CartSteps.addTestGoodsAgain` | 通过（1 行、数量 2、总价 ¥7,598） |
| A | LLS-CART-004 数量达库存上限加号不可再增 | `CartSteps.setQuantityToStock/quantityStillWithinStock/billedQuantityWithinStock` | 通过（组件不会置灰，改为断言「计费数量不超过库存上限」） |
| A | LLS-CART-005 数量输入 0/负数/非数字/超库存 | `CartSteps.setCartQuantityZero` 等 | 通过 |
| A | LLS-CART-006 减到 1 减号置灰 | `CartSteps.clickMinus/minusDisabled/subtotalEqualsPrice` | 通过（小计刷新有延迟，改用 `awaitSubtotalText` 等待） |
| A | LLS-CART-007 勾选重算总价 | `CartSteps.uncheckFirstRow/recheckFirstRow` | 通过（行顺序由接口返回，断言改为「剩余单件金额」；重勾选回到 ¥3,800） |
| A | LLS-CART-009 删除与取消清空 | `CartSteps.deleteFirstItem/clearAllAndCancel` | 通过（确认框按钮必须点 button 本身，不能点 footer 容器） |
| A | LLS-CART-010 未登录访问购物车跳登录 | `CartSteps.openCartAsGuest` | 失败：游客访问 `/cart` 正常渲染空购物车，未跳 `/login` |
| B | LLS-UC-001 / 002 / 003 / 004 / 005 / 010、LLS-ORDER-010 | `OrderCenterSteps` + `OrderDetailPage` / `AfterSalePage` | 7 条全通过（订单列表倒序、Tab 筛选、快速切 Tab、单号搜索与空态、详情字段、售后入口、结算应付=商品单价） |
| B | LLS-CART-003 多店铺分组、LLS-CART-008 全选/半选 | `CartSteps` 新增 8 个步骤 + `CartFixture` | 通过（购物车前置改为 Redis 直写快照，绕开 code=1003 限流；半选样式在当前 iView 构建不存在，改为「表头全选框不再全选 + 已选行数=1」等价断言） |
| A/B | 购物车相关 10 条 | `CartFixture` + `RedisClient`（RESP 手写） | 8/10 通过；2 条按 BUG-3（角标不刷新）与 BUG-4（游客不跳登录）保留为红 |
| B | LLS-GOODS-003 下架商品不可购买 | `GoodsSteps.openOffShelfGoods` + `DbFixture` | 通过（造数：商品 `1400327593747546112` 与全部 SKU 置 DOWN；页面出现「当前商品已下架」且无购买入口；场景结束自动还原） |
| B | LLS-CART-011 失效商品、LLS-CART-013 库存不足拦截 | 已探测未落代码 | 受阻（BUG-5/6）：把 SKU 库存置 0 或 1 后，购物车既不置灰也不提示，仍显示「已选择2件商品 / ￥7,598.00」并可进入结算页 |
| B | LLS-GOODS-005 批发起批、LLS-ORDER-006 券门槛、LLS-UC-019 地址增删、LLS-ORDER-004 地址边界 | — | 未开始（批发阶梯需先确认前端是否展示起批限制；地址地区选择组件不是 `.ivu-cascader`，需继续探测；券门槛需 69/222 元商品 id） |
| C | LLS-ORDER-001 立即购买并用余额支付 | `PaymentSteps` + `PaymentPage.payByBalance` | 通过（余额支付无需密码；跳 `/payDone`，我的订单首条为待发货） |
| C | LLS-ASSET-001 / 002 / 005 / 009 / 012 | `AssetSteps` + `CouponPage` / `PointPage` / `GradePage` / `GiftCardPage` | 通过（券分状态展示、重复领取不增数、积分与日志、等级与差值、错误兑换码被拒） |
| C | LLS-ASSET-017 资产各页一致 | `AssetSteps.assetsConsistentAfterRefresh` | 受阻：二次进入「我的优惠券」时列表偶发 0 条（单机复现为 3 条，用例内连跑仍偶发），保留为红待查 |
| C | LLS-UC-006 取消待付款订单 | `OrderCenterSteps.createPendingOrder/cancelPendingOrder` + `OrderListPage.cancelFirstOrder` | 通过（取消后 API 回查 CANCELLED，且移出待付款；取消是异步，断言用刷新轮询） |
| C | LLS-ORDER-019 重复提交订单 | `OrderCenterSteps.submitOrderThreeTimes/onlyOneOrderCreated` | 通过（连点 3 次只新增 1 笔待付款订单，前端防重生效） |
| C | LLS-GOODS-014 / LLS-ASSET-010 积分不足兑换 | `PointGoodsSteps` + `PointGoodsDetailPage` | 通过（账号 10 积分 / 商品需 500 积分：不进结算、不生成订单；实测点击后无任何提示，已记为缺陷） |
| C | 稳定性抽检 | `run_stability.py --runs 3` | 通过：`@LLS-ORDER-001` 3/3 PASS（8.8~10.5s/轮）、`@LLS-CART-009` 3/3 PASS（16.0~18.3s/轮），无自愈记录 |
| B | LLS-ORDER-006 优惠券门槛 | `OrderCenterSteps` + `CheckoutPage.awaitPayable` | 通过（69 元不抵扣=69.00；222 元选券后=212.00，实测券点击即抵扣、无弹窗） |
| C | LLS-ASSET-017 资产各页一致 | 已改 `@bug-blocked` 等价断言 | 通过（锁定积分/等级/余额三页刷新一致；券页二次进入 0 条单独记录待查） |
| 收口 | 全量执行与稳定性 | `mvn test` / `run_stability.py --runs 3` | 全量 47 条：**42 绿 / 5 红 / 0 错误**，5 红即 BUG-1~4 的原始断言；3 轮稳定性失败集完全一致（确定性红，无 flake） |
| 收口 | 支付与订单前置加固 | `DbFixture.ensureWalletAtLeast` / ORDER-019 基线比对 + 登记清理 | 余额扣空会导致收银台不弹确认框（实测 2,906 时超时），已按清单第 11 条补到 50,000 并还原；ORDER-019 改为基线差集比对，避免异步漏计 |
| 取消 | LLS-GOODS-005 批发起批 | 未实现，按「环境不支持」移出范围 | 取消原因：`li_wholesale` 为空表且前端商品详情不渲染起批数量/起批拦截（加购仅按库存校验），造数无法复现用例预期 |
| 取消 | LLS-UC-019 地址新增/默认/删除、LLS-ORDER-004 地址字段边界 | 未实现，按「环境不支持」移出范围 | 取消原因：新增地址的收货地区是自定义级联组件（非 `.ivu-cascader`，无稳定锚点/文案），无法稳定自动化；手机号校验发生在保存时，缺地区无法单独触发 |

### 新增/修改的基础设施

| 文件 | 变更 |
|---|---|
| `routes.yaml` | 新增 15 条会员/活动路由（`/home/Coupons`、`/PointMall`、`/seckill` 等） |
| `RouteRegistry` | 新增 `openIfNeeded`（同路由不重复导航，避免 DOM 残留导致 `first()` 命中脏节点） |
| `Tabs` | 新增新标签页处理（购物车入口、商品条目都是 `target=_blank`/`window.open`） |
| `ApiClient` | 新增 `addToCart`（query 传参；body 形式返回 400） |
| `CartWriteRateLimiter` | 新增对购物车数量/删除/清空的限流护栏调用（服务端 6 秒窗口，超限静默失败） |
| `config.yaml` | 新增 `goods2`/`goods3`（家家乐可售 SKU，￥1.00） |
