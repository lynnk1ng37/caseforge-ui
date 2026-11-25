# 人工用例来源：manual-testcases 02（LLS-GOODS-001/002/004/006/007/015）
# 不转写：GOODS-003/005/014（批次 B/C 造数）、008（满减配置）、009/010（秒杀活动）、011/012/013（拼团砍价）、016（抓包篡改）

@ui @serial
Feature: 商品详情与营销活动

  Background:
    Given 买家已通过接口登录并注入登录态

  @p0 @positive @LLS-GOODS-001
  Scenario: 切换 SKU 后价格与库存同步变化
    Given 买家打开测试商品详情页
    When 买家选择商品规格黑色
    Then 商品价格应显示为 3899 元
    And 当前规格库存应大于 0
    When 买家选择商品规格紫色
    Then 商品价格应显示为 3799 元
    And 两个规格的库存数应不同

  @p0 @positive @LLS-GOODS-002
  Scenario: 商品基础信息与类型展示正确
    Given 买家打开测试商品详情页
    Then 商品标题应包含一加 OnePlus 9
    And 商品价格应显示为 3799 元
    And 商品详情应展示实物商品标识
    And 商品详情应展示规格紫色与黑色
    And 商品详情应展示购买入口
    And 商品详情应展示累计评价数量

  @p0 @boundary @LLS-GOODS-004
  Scenario: 购买数量 0、负数与超库存边界
    Given 买家打开测试商品详情页
    When 买家把购买数量改为 0
    Then 购买数量应回退为 1
    When 买家把购买数量改为 -1
    Then 购买数量应回退为 1
    When 买家把购买数量改为 99999
    Then 购买数量应被截断为库存上限

  @p0 @positive @LLS-GOODS-006 @known-issue
  Scenario: 加入购物车与立即购买入口均可用
    Given 买家打开测试商品详情页
    When 买家点击加入购物车
    Then 应提示商品已成功加入购物车
    When 买家打开购物车页
    Then 购物车角标应显示 1 件商品
    When 买家在商品详情页点击立即购买
    Then 应进入结算页并展示当前商品

  @p1 @negative @LLS-GOODS-007
  Scenario: 优惠券重复领取被拦截且只增加 1 张
    Given 买家当前持有 3 张优惠券
    When 买家在领券中心点击第一张券的立即领取
    Then 买家的优惠券数量应仍为 3 张

  @p2 @positive @LLS-GOODS-015
  Scenario: 商品评价区展示与无评价空态
    Given 买家打开测试商品详情页
    Then 累计评价数量应显示为 0 条
    When 买家切换到商品评价页签
    Then 商品评价不应出现错误提示

  # 人工用例：LLS-GOODS-003 售罄或下架不可购买（第二批，DB 造数）
  @p0 @negative @LLS-GOODS-003
  Scenario: 下架商品不可购买
    When 买家打开一个已下架商品的详情页
    Then 应提示当前商品已下架
    And 不应展示加入购物车与立即购买入口

  # 人工用例：LLS-GOODS-014 积分不足不可兑换（第二批）
  @p0 @negative @LLS-GOODS-014
  Scenario: 积分不足时不可兑换积分商品
    When 买家在积分商品详情页点击积分购买
    Then 积分不足应被拦截且不生成订单
