# 人工用例：LLS-UC-001 / 002 / 003 / 004 / 005 / 010、LLS-ORDER-010

@ui @serial
Feature: 订单中心与结算金额

  作为已登录买家
  我希望订单列表、订单详情与结算金额都准确可查
  以便确认每笔订单的状态与钱款

  Background:
    Given 买家已通过接口登录并注入登录态

  @p0 @positive @LLS-UC-001
  Scenario: 订单列表默认展示全部且按时间倒序
    When 买家打开我的订单
    Then 全部订单标签应处于选中状态
    And 订单数量应大于等于 1
    And 订单号应按创建时间倒序

  @p0 @positive @LLS-UC-002
  Scenario: 待付款与已完成标签只展示对应状态订单
    When 买家打开我的订单
    And 买家切换到待付款标签
    Then 待付款标签下的订单状态文案应一致
    When 买家切换到已完成标签
    Then 已完成标签下的订单状态文案应一致

  @p1 @exception @LLS-UC-003
  Scenario: 连续快速切换标签不展示上一个标签的数据
    When 买家打开我的订单
    And 买家连续切换待付款、待收货与已完成标签
    Then 当前选中标签应为已完成
    And 当前列表的订单状态应与已完成的标签一致

  @p0 @negative @LLS-UC-004
  Scenario: 按订单号搜索与不存在订单号空态
    When 买家打开我的订单
    And 买家按列表第一笔订单号搜索
    Then 搜索结果应只包含该订单号
    When 买家搜索不存在的订单号
    Then 订单列表应展示空态

  @p0 @positive @LLS-UC-005
  Scenario: 订单详情字段与列表一致
    When 买家打开我的订单并进入第一笔订单详情
    Then 详情订单号应与列表一致
    And 详情应展示应付金额与商品名

  @p0 @negative @LLS-UC-010
  Scenario: 已关闭订单不展示售后入口
    When 买家打开已关闭订单的详情
    Then 该订单不应展示申请售后入口
    When 买家打开我的售后
    Then 售后列表应展示空态或按状态展示

  Scenario: 结算页商品总额与应付金额一致
    When 买家从购物车进入结算页
    Then 结算页应付金额应等于商品总额
    And 结算页不应出现负数金额

  # 人工用例：LLS-UC-006 取消待付款订单（第二批）
  @p0 @positive @LLS-UC-006
  Scenario: 取消待付款订单后状态变为已取消
    Given 买家有一笔待付款订单
    When 买家在待付款列表取消该订单
    Then 该订单状态应为已取消

  # 人工用例：LLS-ORDER-019 重复提交订单（第二批）
  @p0 @exception @LLS-ORDER-019
  Scenario: 重复点击提交订单只生成一个订单
    When 买家在结算页连续点击提交订单 3 次
    Then 待付款订单只应新增 1 笔

  # 人工用例：LLS-ORDER-006 优惠券门槛（第二批）
  @p0 @boundary @LLS-ORDER-006
  Scenario: 优惠券门槛不足与满足门槛的抵扣结果
    Given 买家购物车放入金额 69 元的商品
    Then 结算页应付金额应为 69 元
    When 买家购物车换成金额 222 元的商品并进入结算页
    And 买家在结算页选择第一张可用优惠券
    Then 结算页应付金额应为 212 元
